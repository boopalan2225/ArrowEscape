package com.clevercorner.arrowescape.game.engine

import com.clevercorner.arrowescape.game.model.Board
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.Direction

/**
 * Walks the route an arrow head would carve on its way off the board.
 *
 * This is the single definition of "can it get out?" in the game. The
 * collision checker, the solver, the generator and the board renderer all
 * call it, so none of them can disagree about the rules - and the escape
 * animation follows the very same cells the rules were judged on.
 *
 * Rules, in order of application per step:
 *  1. Step off the board -> escaped.
 *  2. Land on a deflector -> continue in the deflector's direction.
 *  3. Land on a blocked cell -> stopped.
 *
 * Deflectors can be chained into a cycle, so the walk also remembers which
 * (cell, direction) states it has already been in: revisiting one means the
 * arrow would circle forever, which counts as stopped.
 */
object ExitSimulator {

    /**
     * Where an arrow would get to.
     *
     * [path] always holds the cells walked, head cell excluded, so it can be
     * drawn whether the arrow made it or not. [stoppedAt] is null on a clean
     * escape; otherwise it is the cell that refused to let it past - which is
     * exactly what a player staring at a dense board wants pointed out.
     */
    data class Trace(val path: List<Cell>, val stoppedAt: Cell?) {
        val escaped: Boolean get() = stoppedAt == null
    }

    /**
     * The cells an arrow travels through after leaving [head]. On a clean
     * escape the last entry is the first cell OUTSIDE the board, so renderers
     * can slide the arrow fully off-screen.
     */
    fun trace(
        head: Cell,
        direction: Direction,
        board: Board,
        blocked: Set<Cell>
    ): Trace {
        val path = mutableListOf<Cell>()
        // Only a deflector can bend a route back on itself, so a plain board
        // needs no cycle bookkeeping at all - and the carver walks thousands
        // of routes per level.
        val seen = if (board.deflectors.isEmpty()) null else HashSet<Int>()
        var dir = direction
        var cell = head

        while (true) {
            cell = Cell(cell.row + dir.deltaRow(), cell.col + dir.deltaCol())
            path.add(cell)

            if (!board.contains(cell)) return Trace(path, null)

            if (seen != null && !seen.add(stateKey(cell, dir, board))) {
                return Trace(path, cell)
            }

            val bend = board.deflectorAt[cell]
            if (bend != null) {
                dir = bend
                continue
            }
            if (cell in blocked) return Trace(path, cell)
        }
    }

    /** The walked cells, or null if the arrow cannot get out at all. */
    fun route(
        head: Cell,
        direction: Direction,
        board: Board,
        blocked: Set<Cell>
    ): List<Cell>? = trace(head, direction, board, blocked).takeIf { it.escaped }?.path

    private fun stateKey(cell: Cell, dir: Direction, board: Board): Int =
        (cell.row * board.cols + cell.col) * Direction.entries.size + dir.ordinal
}
