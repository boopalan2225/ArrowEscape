package com.clevercorner.arrowescape.game.engine

import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board

/**
 * Plays a board out to the end and reports how it went.
 *
 * A greedy peel is enough here, and that is a property of the rules rather
 * than luck: clearing arrows only ever frees cells, deflectors are permanent
 * and colour-locked arrows move as one. So the set of legal moves grows
 * monotonically - if a move is legal now it is still legal later, and taking
 * any legal move can never destroy a solution. No backtracking required, and
 * the player can never walk into an unwinnable board.
 */
object GameSolver {

    /**
     * @param order the arrows to tap, in order (one id per move; the partner
     *        of a colour-locked pair rides along and is not listed).
     * @param tightness 0..1 - how forced the solution was. 1.0 means there was
     *        exactly one legal move at every single step.
     * @param longestChain the most moves in a row that had exactly one legal
     *        option. This is the "closely chained" measure: a long chain is a
     *        run the player must read ahead to survive, rather than a board
     *        they can poke at randomly.
     */
    data class Solution(
        val order: List<Int>,
        val tightness: Float,
        val forcedRatio: Float,
        val longestChain: Int,
        /** What the board asks of the player's eye. See [PuzzleMetrics]. */
        val metrics: PuzzleMetrics = PuzzleMetrics.NONE
    ) {
        /**
         * One number to rank candidate boards by.
         *
         * This used to be `tightness*0.45 + forcedRatio*0.35 + chainReach*0.20`,
         * which is three measurements of the same thing and peaks on a board
         * where every move is forced - a board with no thinking in it
         * whatsoever. Maximising it was therefore actively steering toward
         * the most tedious board the generator could find.
         *
         * Perception now carries most of the weight, because under the
         * current rules no move can be wrong (see [PuzzleMetrics]), so
         * reading the board *is* the game. Constraint still counts - a board
         * with no pressure at all is a chore rather than a puzzle - but it no
         * longer counts three times.
         */
        val challenge: Float
            get() = tightness * 0.25f +
                chainReach * 0.10f +
                metrics.falseAffordanceRate * 0.40f +
                metrics.visualSearchCost * 0.25f

        /** Chains beyond ten moves stop feeling harder and start feeling long. */
        private val chainReach: Float get() = (longestChain / 10f).coerceIn(0f, 1f)
    }

    /** The full clear order, or null if the board deadlocks. */
    fun solve(arrows: List<Arrow>, board: Board): Solution? {
        val remaining = arrows.filter { it.isActive }.toMutableList()
        if (remaining.isEmpty()) return Solution(emptyList(), 0f, 0f, 0)

        val order = mutableListOf<Int>()
        var freedomSum = 0f
        var steps = 0
        var forced = 0
        var chain = 0
        var longestChain = 0
        val recorder = MetricsRecorder(board.rows, board.cols)

        while (remaining.isNotEmpty()) {
            // One pass of traces serves both purposes: which moves are legal,
            // and which blocked arrows were pretending to be. Asking twice
            // would double the cost of every solve for no new information.
            val step = analyseStep(remaining, board)
            val moves = step.moves
            if (moves.isEmpty()) return null

            val parties = partyCount(remaining)
            freedomSum += (moves.size - 1).toFloat() / (parties - 1).coerceAtLeast(1)
            steps++

            if (moves.size == 1) {
                forced++
                chain++
                longestChain = maxOf(longestChain, chain)
            } else {
                chain = 0
            }

            val chosen = moves.first()
            val chosenHead = remaining.first { it.id == chosen.first }.head
            recorder.step(
                legalMoveCount = moves.size,
                chosenHead = chosenHead,
                falseAffordances = step.falseAffordances,
                remaining = remaining.size
            )

            order.add(chosen.first)
            remaining.removeAll { it.id in chosen.second }
        }

        val divisor = steps.coerceAtLeast(1)
        return Solution(
            order = order,
            tightness = (1f - freedomSum / divisor).coerceIn(0f, 1f),
            forcedRatio = forced.toFloat() / divisor,
            longestChain = longestChain,
            metrics = recorder.build()
        )
    }

    fun isSolvable(arrows: List<Arrow>, board: Board): Boolean = solve(arrows, board) != null

    /** Legacy plain-board check - no deflectors, no locks. */
    fun isSolvable(arrows: List<Arrow>, gridRows: Int, gridCols: Int): Boolean =
        isSolvable(arrows, Board(gridRows, gridCols))

    /** Distinct moves available now, as (tapped arrow id, ids that leave). */
    private fun legalMoves(
        remaining: List<Arrow>,
        board: Board
    ): List<Pair<Int, Set<Int>>> = analyseStep(remaining, board).moves

    /** One step's worth of traces, read for both legality and honesty. */
    private data class StepAnalysis(
        val moves: List<Pair<Int, Set<Int>>>,
        val falseAffordances: Int
    )

    /**
     * Trace every remaining arrow once, and sort the results into the moves
     * that work and the ones that only look like they do.
     */
    private fun analyseStep(remaining: List<Arrow>, board: Board): StepAnalysis {
        val seen = HashSet<Int>()
        val moves = mutableListOf<Pair<Int, Set<Int>>>()
        var falseAffordances = 0

        for (arrow in remaining) {
            if (arrow.id in seen) continue
            val traces = CollisionChecker.traceFor(arrow, remaining, board)
            if (traces.isEmpty()) continue

            if (traces.values.all { it.escaped }) {
                val ids = traces.keys.toSet()
                seen.addAll(ids)
                moves.add(arrow.id to ids)
            } else {
                // Blocked - but was it obviously blocked? An arrow stopped by
                // the neighbour touching it is honest; one that runs several
                // cells into open board before stopping looked available.
                val run = traces.values.filter { !it.escaped }.maxOf { it.path.size }
                if (run >= PuzzleMetrics.FAR_RUN_CELLS) falseAffordances++
            }
        }

        return StepAnalysis(moves, falseAffordances)
    }

    /** Pairs count once: a locked pair is one decision, not two. */
    private fun partyCount(remaining: List<Arrow>): Int {
        val groups = remaining.mapNotNull { it.colorGroup }.distinct().size
        val loose = remaining.count { it.colorGroup == null }
        return groups + loose
    }
}
