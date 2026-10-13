package com.example.arrowescape.game.curve


/**
 * Escape rules and solvability analysis for curved arrow puzzles.
 *
 * An arrow escapes by sliding forward along its own heading until its entire
 * body has left the board. It is blocked if any part of its swept body touches
 * a still-active arrow. This is a body sweep, not a ray cast from the tip -
 * the old tip-ray let arrows slip through neighbours they visibly overlapped.
 */
object CurvedGameEngine {

    /** Distance advanced per sweep step. Smaller = stricter, slower. */
    private const val SWEEP_STEP = 7f

    /**
     * Contact radius as a fraction of stroke width.
     *
     * Tuned against the packing density in CurveGenerator: arrows sit roughly
     * one stroke-width apart, so a contact radius near 1.0 made every arrow
     * permanently blocked by its own neighbours. Half a stroke means arrows
     * must genuinely overlap to count as blocking.
     */
    private const val CONTACT_SLACK = 0.5f

    data class Board(val width: Float, val height: Float) {
        fun isOutside(p: Point2D, margin: Float): Boolean =
            p.x < -margin || p.x > width + margin || p.y < -margin || p.y > height + margin
    }

    /**
     * Precomputed "who blocks whom" graph for a fixed set of arrows.
     *
     * Blocking depends only on geometry, never on which arrows are still
     * alive: if B is out of A's path it can never start blocking it. So the
     * expensive sweep runs once, and the solver then just walks integer sets.
     * This is what turns level generation from seconds into milliseconds.
     */
    class BlockingGraph(arrows: List<CurvedArrow>, board: Board) {
        val ids: List<Int> = arrows.map { it.id }

        /** For each arrow, the arrows standing in its escape path. */
        private val blockers: Map<Int, Set<Int>>

        init {
            val index = ArrowSpatialIndex.forArrows(arrows)
            blockers = arrows.associate { arrow ->
                arrow.id to allBlockersFor(arrow, index, board)
            }
        }

        fun blockersOf(id: Int): Set<Int> = blockers[id] ?: emptySet()

        /** An arrow is free when none of its blockers are still on the board. */
        fun isFree(id: Int, remaining: Set<Int>): Boolean =
            blockersOf(id).none { it in remaining }
    }

    /**
     * Every arrow whose body lies in [arrow]'s swept escape corridor.
     */
    private fun allBlockersFor(
        arrow: CurvedArrow,
        index: ArrowSpatialIndex,
        board: Board
    ): Set<Int> {
        val direction = arrow.direction
        if (direction == Point2D.ZERO) return emptySet()

        val body = arrow.polyline
        if (body.isEmpty()) return emptySet()

        val found = mutableSetOf<Int>()
        val radius = arrow.strokeWidth * CONTACT_SLACK
        val margin = arrow.strokeWidth * 2f
        val steps = ((board.width + board.height) / SWEEP_STEP).toInt()

        for (step in 1..steps) {
            val shift = direction * (SWEEP_STEP * step)
            var fullyOutside = true

            for (bodyPoint in body) {
                val swept = bodyPoint + shift
                if (!board.isOutside(swept, margin)) fullyOutside = false
                for (id in index.candidatesNear(swept)) {
                    if (id == arrow.id || id in found) continue
                    val other = index.arrowById(id) ?: continue
                    if (other.isNear(swept, radius)) found.add(id)
                }
            }

            if (fullyOutside) break
        }

        return found
    }

    /**
     * Blocking graph for both orientations of every arrow, built once.
     *
     * The orientation search flips arrows thousands of times, and each flip
     * used to trigger a full geometric re-sweep. Since an arrow only ever has
     * two possible headings, we precompute both up front and the search then
     * costs nothing but set lookups.
     */
    class OrientationGraph(arrows: List<CurvedArrow>, board: Board) {
        val size = arrows.size
        val ids: List<Int> = arrows.map { it.id }

        private val forward: List<Set<Int>>
        private val reversed: List<Set<Int>>

        init {
            // Index over forward geometry is enough: reversing an arrow keeps
            // its body in exactly the same place, only the heading changes.
            val index = ArrowSpatialIndex.forArrows(arrows)
            forward = arrows.map { allBlockersFor(it, index, board) }
            reversed = arrows.map { allBlockersFor(it.reversed(), index, board) }
        }

        fun blockersOf(slot: Int, flipped: Boolean): Set<Int> =
            if (flipped) reversed[slot] else forward[slot]

        /**
         * Greedy clear-through for one orientation assignment.
         * [flips] is indexed by slot, not by arrow id.
         */
        fun analyze(flips: BooleanArray): SolveResult {
            val slotOfId = HashMap<Int, Int>(size)
            ids.forEachIndexed { slot, id -> slotOfId[id] = slot }

            val remaining = ids.toMutableSet()
            val order = mutableListOf<Int>()
            var branchingTotal = 0
            var turns = 0
            var forced = 0

            while (remaining.isNotEmpty()) {
                val free = remaining.filter { id ->
                    val slot = slotOfId.getValue(id)
                    blockersOf(slot, flips[slot]).none { it in remaining }
                }
                if (free.isEmpty()) {
                    return SolveResult(false, order, averageOf(branchingTotal, turns), forced)
                }

                branchingTotal += free.size
                turns++
                if (free.size == 1) forced++

                val chosen = free.first()
                remaining.remove(chosen)
                order.add(chosen)
            }

            return SolveResult(true, order, averageOf(branchingTotal, turns), forced)
        }
    }

    /**
     * Check if an arrow can escape (clear swept path off the board).
     *
     * Convenience wrapper for one-off queries. Hot paths should build a
     * [BlockingGraph] once and call `isFree` instead.
     */
    fun canEscape(
        arrow: CurvedArrow,
        allArrows: List<CurvedArrow>,
        shapeWidth: Float,
        shapeHeight: Float
    ): Boolean {
        if (!arrow.isActive) return false
        val live = allArrows.filter { it.isActive }
        val graph = BlockingGraph(live, Board(shapeWidth, shapeHeight))
        return graph.isFree(arrow.id, live.map { it.id }.toSet())
    }

    /**
     * Result of simulating a full playthrough.
     */
    data class SolveResult(
        val solved: Boolean,
        val order: List<Int>,
        /** Mean number of legal moves available per turn. Lower = harder. */
        val averageBranching: Float,
        /** Turns where exactly one move existed. Higher = harder. */
        val forcedMoves: Int
    ) {
        /**
         * 0..1 difficulty score. Branching is normalised by the number of
         * arrows, because a raw count scales with board size - without that,
         * a huge dense level always scored 'easier' than a tiny sparse one
         * purely because it had more arrows free at any moment.
         */
        val tightness: Float
            get() {
                if (!solved || order.isEmpty()) return 0f
                val openFraction = (averageBranching / order.size).coerceIn(0f, 1f)
                val forcedScore = forcedMoves.toFloat() / order.size
                return ((1f - openFraction) * 0.6f + forcedScore * 0.4f).coerceIn(0f, 1f)
            }
    }

    /**
     * Greedily simulate clearing the board, recording how constrained it was.
     */
    fun analyze(
        arrows: List<CurvedArrow>,
        board: Board
    ): SolveResult = analyze(BlockingGraph(arrows, board))

    /**
     * Greedily simulate clearing the board from a precomputed graph.
     * Greedy is sound here: removing an arrow never blocks another, so any
     * escapable arrow is always safe to take.
     */
    fun analyze(graph: BlockingGraph): SolveResult {
        val remaining = graph.ids.toMutableSet()
        val order = mutableListOf<Int>()

        var branchingTotal = 0
        var turns = 0
        var forced = 0

        while (remaining.isNotEmpty()) {
            val free = remaining.filter { graph.isFree(it, remaining) }
            if (free.isEmpty()) {
                return SolveResult(false, order, averageOf(branchingTotal, turns), forced)
            }

            branchingTotal += free.size
            turns++
            if (free.size == 1) forced++

            val chosen = free.first()
            remaining.remove(chosen)
            order.add(chosen)
        }

        return SolveResult(true, order, averageOf(branchingTotal, turns), forced)
    }

    private fun averageOf(total: Int, turns: Int): Float =
        if (turns == 0) 0f else total.toFloat() / turns

    /**
     * Check if the level is solvable (every arrow can eventually escape).
     */
    fun isSolvable(
        arrows: List<CurvedArrow>,
        shapeWidth: Float,
        shapeHeight: Float
    ): Boolean = analyze(arrows, Board(shapeWidth, shapeHeight)).solved
}
