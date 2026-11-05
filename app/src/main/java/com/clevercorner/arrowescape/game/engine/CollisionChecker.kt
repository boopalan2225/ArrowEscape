package com.clevercorner.arrowescape.game.engine

import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board
import com.clevercorner.arrowescape.game.model.Cell

/**
 * Decides which arrows may leave the board right now. Pure logic, no UI.
 *
 * Mechanic: a snake slithers out head-first and its body follows the exact
 * trail the head carved, so only the HEAD's route matters (see
 * [ExitSimulator]). That is what makes densely packed bent snakes playable.
 *
 * Two mechanics ride on top of the base rule:
 *  - deflectors bend the route, so "clear lane" is no longer a straight line;
 *  - colour-locked arrows leave as a pair, ignoring each other on the way out.
 */
object CollisionChecker {

    /** A legal move: the arrows that leave, and the route each one takes. */
    data class Escape(val routes: Map<Int, List<Cell>>) {
        val arrowIds: Set<Int> get() = routes.keys
    }

    /**
     * The move tapping [arrow] would make, or null if it is blocked.
     * Tapping either half of a colour-locked pair moves both halves.
     */
    fun escapeFor(arrow: Arrow, activeArrows: List<Arrow>, board: Board): Escape? {
        if (isSequenceBlocked(arrow, activeArrows)) return null
        val traces = traceFor(arrow, activeArrows, board)
        if (traces.isEmpty() || traces.values.any { !it.escaped }) return null
        return Escape(traces.mapValues { it.value.path })
    }

    /**
     * Whether [arrow] is waiting its turn in the numbered sequence.
     *
     * Checked here rather than in the solver so that one rule serves every
     * caller: the solver, the hint system, the "can I tap this" test and the
     * route preview all go through [escapeFor], and a mechanic implemented in
     * only some of them is a mechanic that contradicts itself on screen.
     *
     * A numbered arrow waits for *all* lower numbers rather than only its
     * immediate predecessor. That costs a scan and buys correctness when a
     * sequence is broken up by colour locks or a dropped arrow leaves a gap
     * in the numbering.
     */
    fun isSequenceBlocked(arrow: Arrow, activeArrows: List<Arrow>): Boolean {
        val key = arrow.sequenceKey ?: return false
        return activeArrows.any { other ->
            other.isActive && other.id != arrow.id &&
                (other.sequenceKey ?: Int.MAX_VALUE) < key
        }
    }

    /**
     * What would happen if [arrow] were tapped, whether or not it works: one
     * trace per arrow that would leave with it. The board renderer uses this
     * to show the player the route - including the cell that blocks it - so
     * a deflected exit can be read rather than guessed at.
     */
    fun traceFor(
        arrow: Arrow,
        activeArrows: List<Arrow>,
        board: Board
    ): Map<Int, ExitSimulator.Trace> =
        traceFor(arrow, activeArrows, board, CellOccupancy.of(activeArrows, board))

    /**
     * As above, against a board state that has already been indexed.
     *
     * The solver asks this for every arrow at every step, and the board is
     * the same for all of them - so it builds one [CellOccupancy] per step
     * and passes it in. See that class for what the old per-arrow sets cost.
     */
    fun traceFor(
        arrow: Arrow,
        activeArrows: List<Arrow>,
        board: Board,
        occupancy: CellOccupancy
    ): Map<Int, ExitSimulator.Trace> {
        if (!arrow.isActive) return emptyMap()

        val party = partyOf(arrow, activeArrows)
        val blocked = occupancy.blockingAllBut(party)

        return party.associate { member ->
            member.id to ExitSimulator.trace(member.head, member.direction, board, blocked)
        }
    }

    fun canEscape(arrow: Arrow, activeArrows: List<Arrow>, board: Board): Boolean =
        escapeFor(arrow, activeArrows, board) != null

    /** Legacy plain-board check - kept so callers without furniture stay simple. */
    fun canEscape(arrow: Arrow, activeArrows: List<Arrow>, gridRows: Int, gridCols: Int): Boolean =
        canEscape(arrow, activeArrows, Board(gridRows, gridCols))

    fun getEscapableArrows(activeArrows: List<Arrow>, board: Board): List<Arrow> =
        activeArrows.filter { canEscape(it, activeArrows, board) }

    fun getEscapableArrows(
        activeArrows: List<Arrow>,
        gridRows: Int,
        gridCols: Int
    ): List<Arrow> = getEscapableArrows(activeArrows, Board(gridRows, gridCols))

    /**
     * The arrows that must move together with [arrow]: itself, plus its
     * colour-lock partners that are still on the board.
     */
    private fun partyOf(arrow: Arrow, activeArrows: List<Arrow>): List<Arrow> {
        val group = arrow.colorGroup ?: return listOf(arrow)
        return activeArrows.filter { it.isActive && it.colorGroup == group }
            .ifEmpty { listOf(arrow) }
    }
}
