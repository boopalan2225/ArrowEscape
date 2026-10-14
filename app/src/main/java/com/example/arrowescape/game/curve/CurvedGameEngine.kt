package com.example.arrowescape.game.curve


/**
 * Escape rules and solvability analysis for curved arrow puzzles.
 *
 * An escaping arrow slides along its OWN curve - the body follows exactly the
 * trail the head leaves, like a train on a track - and then carries straight
 * on past the tip until it clears the board. Because the body only ever
 * occupies space the head has already passed through, the head's path is the
 * whole story: an arrow is blocked iff something live sits on the ray ahead of
 * its tip. That is also precisely what the escape animation draws, so what
 * the player sees and what the rules enforce can never disagree.
 */
object CurvedGameEngine {

    /** Distance between samples along the escape ray. */
    private const val RAY_STEP = 4f

    /**
     * Fraction of the two strokes' combined width that counts as touching.
     * Slightly under 1.0 so arrows that merely graze past do not block.
     */
    private const val CONTACT_FACTOR = 0.9f

    data class Board(val width: Float, val height: Float) {
        fun isOutside(p: Point2D, margin: Float): Boolean =
            p.x < -margin || p.x > width + margin || p.y < -margin || p.y > height + margin

        /**
         * Where a ray from [origin] along [direction] leaves the board.
         * Returns [origin] for a zero direction.
         */
        fun rayExit(origin: Point2D, direction: Point2D): Point2D {
            val dir = direction.normalize()
            if (dir == Point2D.ZERO) return origin
            val tx = when {
                dir.x > 0f -> (width - origin.x) / dir.x
                dir.x < 0f -> -origin.x / dir.x
                else -> Float.MAX_VALUE
            }
            val ty = when {
                dir.y > 0f -> (height - origin.y) / dir.y
                dir.y < 0f -> -origin.y / dir.y
                else -> Float.MAX_VALUE
            }
            return origin + dir * minOf(tx, ty).coerceAtLeast(0f)
        }
    }

    /**
     * How far [arrow]'s tip can travel along its heading before touching one
     * of [blockers] - the room it has to wiggle when a blocked tap bumps it.
     * Runs to the board edge if nothing is in the way.
     */
    fun runwayBeforeContact(
        arrow: CurvedArrow,
        blockers: Collection<CurvedArrow>,
        board: Board
    ): Float {
        val direction = arrow.direction
        if (direction == Point2D.ZERO) return 0f

        val tip = arrow.endPoint
        val margin = arrow.strokeWidth * 2f
        val steps = ((board.width + board.height) / RAY_STEP).toInt()

        for (step in 0..steps) {
            val probe = tip + direction * (RAY_STEP * step)
            if (board.isOutside(probe, margin)) return RAY_STEP * step
            val touched = blockers.any { other ->
                other.id != arrow.id &&
                    other.isNear(probe, (arrow.strokeWidth + other.strokeWidth) * 0.5f * CONTACT_FACTOR)
            }
            if (touched) return RAY_STEP * step
        }
        return RAY_STEP * steps
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
     * Every arrow standing on the ray ahead of [arrow]'s tip.
     */
    private fun allBlockersFor(
        arrow: CurvedArrow,
        index: ArrowSpatialIndex,
        board: Board
    ): Set<Int> {
        val direction = arrow.direction
        if (direction == Point2D.ZERO) return emptySet()

        val tip = arrow.endPoint
        val margin = arrow.strokeWidth * 2f
        val steps = ((board.width + board.height) / RAY_STEP).toInt()
        val found = mutableSetOf<Int>()

        for (step in 0..steps) {
            val probe = tip + direction * (RAY_STEP * step)
            if (board.isOutside(probe, margin)) break

            for (id in index.candidatesNear(probe)) {
                if (id == arrow.id || id in found) continue
                val other = index.arrowById(id) ?: continue
                // Centre-to-centre distance at which the two strokes touch.
                val touching = (arrow.strokeWidth + other.strokeWidth) * 0.5f * CONTACT_FACTOR
                if (other.isNear(probe, touching)) found.add(id)
            }
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
        fun analyze(flips: BooleanArray): SolveResult =
            greedyClear(ids) { slot -> blockersOf(slot, flips[slot]) }
    }

    /**
     * Greedy clear-through over a dependency graph, in O(arrows + edges).
     *
     * Greedy is sound because removing an arrow never blocks another, so any
     * free arrow is always safe to take. Done as a topological sweep with a
     * count of unmet blockers per arrow: the old version rescanned every
     * remaining arrow each turn (quadratic), which was fine for 30 arrows and
     * hopeless for the 150+ a dense silhouette needs.
     *
     * [blockersOf] maps an arrow's index in [ids] to the ids it waits on. The
     * lowest-index free arrow is taken each turn, so the order is stable.
     */
    private inline fun greedyClear(ids: List<Int>, blockersOf: (Int) -> Set<Int>): SolveResult {
        val slotOfId = HashMap<Int, Int>(ids.size * 2)
        ids.forEachIndexed { slot, id -> slotOfId[id] = slot }

        val unmet = IntArray(ids.size)
        val dependents = Array(ids.size) { ArrayList<Int>(2) }
        for (slot in ids.indices) {
            for (blocker in blockersOf(slot)) {
                val blockerSlot = slotOfId[blocker] ?: continue
                unmet[slot]++
                dependents[blockerSlot].add(slot)
            }
        }

        val free = java.util.TreeSet<Int>()
        for (slot in ids.indices) if (unmet[slot] == 0) free.add(slot)

        val order = ArrayList<Int>(ids.size)
        var branchingTotal = 0
        var turns = 0
        var forced = 0

        while (free.isNotEmpty()) {
            branchingTotal += free.size
            turns++
            if (free.size == 1) forced++

            val chosen = free.pollFirst()!!
            order.add(ids[chosen])
            for (dependent in dependents[chosen]) {
                if (--unmet[dependent] == 0) free.add(dependent)
            }
        }

        return SolveResult(order.size == ids.size, order, averageOf(branchingTotal, turns), forced)
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
     */
    fun analyze(graph: BlockingGraph): SolveResult =
        greedyClear(graph.ids) { slot -> graph.blockersOf(graph.ids[slot]) }

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

    /**
     * Whether every arrow in [remaining] can still escape, in some order.
     *
     * This is the live-board version of the check the generator runs: same
     * rule, same cached [graph], but only over the arrows still in play.
     * Blockers that have already left are simply ignored. O(arrows + edges),
     * so it is cheap enough to run after every move.
     */
    fun isSolvable(graph: BlockingGraph, remaining: Set<Int>): Boolean {
        val ids = graph.ids.filter { it in remaining }
        return greedyClear(ids) { slot -> graph.blockersOf(ids[slot]) }.solved
    }
}
