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
        val longestChain: Int
    ) {
        /**
         * One number to rank candidate boards by. Tightness says how little
         * freedom there was on average; the chain terms say whether that
         * pressure came in readable runs or in scattered moments.
         */
        val challenge: Float
            get() = tightness * 0.45f + forcedRatio * 0.35f + chainReach * 0.20f

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

        while (remaining.isNotEmpty()) {
            val moves = legalMoves(remaining, board)
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
            order.add(chosen.first)
            remaining.removeAll { it.id in chosen.second }
        }

        val divisor = steps.coerceAtLeast(1)
        return Solution(
            order = order,
            tightness = (1f - freedomSum / divisor).coerceIn(0f, 1f),
            forcedRatio = forced.toFloat() / divisor,
            longestChain = longestChain
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
    ): List<Pair<Int, Set<Int>>> {
        val seen = HashSet<Int>()
        val moves = mutableListOf<Pair<Int, Set<Int>>>()
        for (arrow in remaining) {
            if (arrow.id in seen) continue
            val escape = CollisionChecker.escapeFor(arrow, remaining, board) ?: continue
            seen.addAll(escape.arrowIds)
            moves.add(arrow.id to escape.arrowIds)
        }
        return moves
    }

    /** Pairs count once: a locked pair is one decision, not two. */
    private fun partyCount(remaining: List<Arrow>): Int {
        val groups = remaining.mapNotNull { it.colorGroup }.distinct().size
        val loose = remaining.count { it.colorGroup == null }
        return groups + loose
    }
}
