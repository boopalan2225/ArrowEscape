package com.clevercorner.arrowescape.game.curve


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

        /**
         * Blockers as *slot* indices rather than arrow ids, so the sweep can
         * walk a flat `IntArray` instead of iterating a `Set<Int>` - which
         * boxed every element and needed an id-to-slot lookup per edge, on
         * every one of the thousands of calls the search makes.
         */
        private val forward: Array<IntArray>
        private val reversed: Array<IntArray>

        private val work: SweepWorkspace

        init {
            // Index over forward geometry is enough: reversing an arrow keeps
            // its body in exactly the same place, only the heading changes.
            val index = ArrowSpatialIndex.forArrows(arrows)
            val slotOfId = HashMap<Int, Int>(arrows.size * 2)
            arrows.forEachIndexed { slot, arrow -> slotOfId[arrow.id] = slot }

            fun slotsFor(arrow: CurvedArrow): IntArray {
                val blockers = allBlockersFor(arrow, index, board)
                val slots = IntArray(blockers.size)
                var at = 0
                for (id in blockers) slotOfId[id]?.let { slots[at++] = it }
                return if (at == slots.size) slots else slots.copyOf(at)
            }

            forward = Array(arrows.size) { slotsFor(arrows[it]) }
            reversed = Array(arrows.size) { slotsFor(arrows[it].reversed()) }

            // Whichever orientation each arrow ends up in, the edge count
            // cannot exceed the per-slot maximum of the two.
            val maxEdges = (0 until size).sumOf { maxOf(forward[it].size, reversed[it].size) }
            work = SweepWorkspace(size, maxEdges)
        }

        private fun blockersOf(slot: Int, flipped: Boolean): IntArray =
            if (flipped) reversed[slot] else forward[slot]

        /**
         * Greedy clear-through for one orientation assignment.
         * [flips] is indexed by slot, not by arrow id.
         *
         * Not thread-safe: the scratch buffers are shared between calls. The
         * orientation search is single-threaded by design, and sharing is the
         * entire point - allocating them per call is what made it slow.
         */
        fun analyze(flips: BooleanArray): SolveResult =
            sweep(ids, work) { slot -> blockersOf(slot, flips[slot]) }
    }

    /**
     * Reusable scratch buffers for [sweep].
     *
     * The orientation search runs the sweep thousands of times per level, and
     * it used to allocate a `HashMap`, an `ArrayList` per arrow and a
     * `TreeSet` on every single call. For a dense board that is millions of
     * boxed operations and hundreds of thousands of objects per level - which
     * is what left the loading spinner sitting there. Hoisting the buffers out
     * means a search step allocates nothing but its result.
     */
    private class SweepWorkspace(size: Int, maxEdges: Int) {
        /** How many blockers each slot is still waiting on. */
        val unmet = IntArray(size)

        /** CSR offsets: slot s's dependents live in [start[s], start[s + 1]). */
        val start = IntArray(size + 1)
        val cursor = IntArray(size + 1)
        val dependents = IntArray(maxEdges)

        /** Binary min-heap of free slots, so the lowest index is taken first. */
        val heap = IntArray(size)
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
     * [blockerSlotsOf] maps an arrow's index in [ids] to the *slots* it waits
     * on. The lowest-index free arrow is taken each turn, so the order is
     * stable.
     */
    private inline fun sweep(
        ids: List<Int>,
        work: SweepWorkspace,
        blockerSlotsOf: (Int) -> IntArray
    ): SolveResult {
        val n = ids.size
        val unmet = work.unmet
        val start = work.start
        val cursor = work.cursor
        val dependents = work.dependents
        val heap = work.heap

        // Build the reverse adjacency (who unblocks whom) as a counting sort
        // into flat arrays, rather than a list object per arrow.
        java.util.Arrays.fill(start, 0, n + 1, 0)
        for (slot in 0 until n) {
            val blockers = blockerSlotsOf(slot)
            unmet[slot] = blockers.size
            for (blocker in blockers) start[blocker + 1]++
        }
        for (slot in 0 until n) start[slot + 1] += start[slot]
        System.arraycopy(start, 0, cursor, 0, n + 1)
        for (slot in 0 until n) {
            for (blocker in blockerSlotsOf(slot)) dependents[cursor[blocker]++] = slot
        }

        var heapSize = 0
        for (slot in 0 until n) {
            if (unmet[slot] == 0) heapPush(heap, heapSize++, slot)
        }

        val order = ArrayList<Int>(n)
        var branchingTotal = 0
        var turns = 0
        var forced = 0

        while (heapSize > 0) {
            branchingTotal += heapSize
            turns++
            if (heapSize == 1) forced++

            val chosen = heap[0]
            heapSize = heapPop(heap, heapSize)
            order.add(ids[chosen])

            for (edge in start[chosen] until start[chosen + 1]) {
                val dependent = dependents[edge]
                if (--unmet[dependent] == 0) heapPush(heap, heapSize++, dependent)
            }
        }

        return SolveResult(order.size == n, order, averageOf(branchingTotal, turns), forced)
    }

    /** Places [value] at [at] and sifts it up. [at] is the new last index. */
    private fun heapPush(heap: IntArray, at: Int, value: Int) {
        var child = at
        heap[child] = value
        while (child > 0) {
            val parent = (child - 1) / 2
            if (heap[parent] <= heap[child]) break
            val swap = heap[parent]
            heap[parent] = heap[child]
            heap[child] = swap
            child = parent
        }
    }

    /** Removes the root and returns the new size. */
    private fun heapPop(heap: IntArray, size: Int): Int {
        val last = size - 1
        heap[0] = heap[last]
        var parent = 0
        while (true) {
            val left = parent * 2 + 1
            if (left >= last) break
            val right = left + 1
            val smaller = if (right < last && heap[right] < heap[left]) right else left
            if (heap[parent] <= heap[smaller]) break
            val swap = heap[parent]
            heap[parent] = heap[smaller]
            heap[smaller] = swap
            parent = smaller
        }
        return last
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
    fun analyze(graph: BlockingGraph): SolveResult = sweepOverIds(graph, graph.ids)

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
    fun isSolvable(graph: BlockingGraph, remaining: Set<Int>): Boolean =
        sweepOverIds(graph, graph.ids.filter { it in remaining }).solved

    /**
     * Runs the sweep over an arbitrary subset of [graph]'s arrows.
     *
     * Converts the graph's id-keyed blocker sets into the slot-indexed form
     * [sweep] wants. One-shot callers pay for that conversion; the generator
     * does not, because [OrientationGraph] stores slots in the first place.
     */
    private fun sweepOverIds(graph: BlockingGraph, ids: List<Int>): SolveResult {
        val slotOfId = HashMap<Int, Int>(ids.size * 2)
        ids.forEachIndexed { slot, id -> slotOfId[id] = slot }

        val blockerSlots = Array(ids.size) { slot ->
            val blockers = graph.blockersOf(ids[slot])
            val slots = IntArray(blockers.size)
            var at = 0
            for (id in blockers) slotOfId[id]?.let { slots[at++] = it }
            if (at == slots.size) slots else slots.copyOf(at)
        }

        val work = SweepWorkspace(ids.size, blockerSlots.sumOf { it.size })
        return sweep(ids, work) { slot -> blockerSlots[slot] }
    }
}
