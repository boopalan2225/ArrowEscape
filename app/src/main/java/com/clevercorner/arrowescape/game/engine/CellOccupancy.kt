package com.clevercorner.arrowescape.game.engine

import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board
import com.clevercorner.arrowescape.game.model.Cell

/**
 * Which arrow is sitting on each cell, for one board state.
 *
 * Exists because the question "is this cell blocked?" used to be answered by
 * building a fresh `HashSet<Cell>` of *every occupied cell on the board* -
 * once per arrow, per step, per solve. The solver asks it O(n²) times per
 * board and the generator solves up to twenty-one boards per level, so a
 * single level could allocate half a gigabyte of identical sets. The board
 * does not change between those questions; only the asker does.
 *
 * So the board state is indexed once and the party is subtracted per asker:
 * a flat `IntArray` of owning arrow ids, read without allocating anything at
 * all.
 *
 * Assumes arrows do not overlap, which the carver guarantees. If two arrows
 * ever shared a cell, the later one would own it here, where the old set
 * would have blocked for both.
 */
class CellOccupancy private constructor(
    private val rows: Int,
    private val cols: Int,
    private val owner: IntArray
) {

    /** The id of the arrow on [cell], or [NOBODY] - including off-board. */
    fun ownerOf(cell: Cell): Int {
        if (cell.row < 0 || cell.row >= rows || cell.col < 0 || cell.col >= cols) return NOBODY
        return owner[cell.row * cols + cell.col]
    }

    /**
     * What blocks [party] on its way out: everything except itself.
     *
     * A colour-locked pair leaves together and so ignores its own halves -
     * the same rule the old per-arrow set encoded by omission.
     */
    fun blockingAllBut(party: List<Arrow>): BlockedCells {
        val ids = IntArray(party.size) { party[it].id }
        return BlockedCells { cell ->
            val id = ownerOf(cell)
            id != NOBODY && !ids.contains(id)
        }
    }

    companion object {
        /** No arrow is here. Arrow ids are non-negative, so -1 is free. */
        const val NOBODY = -1

        fun of(arrows: List<Arrow>, board: Board): CellOccupancy {
            val owner = IntArray(board.rows * board.cols) { NOBODY }
            for (arrow in arrows) {
                if (!arrow.isActive) continue
                for (cell in arrow.cells) {
                    if (cell.row < 0 || cell.row >= board.rows) continue
                    if (cell.col < 0 || cell.col >= board.cols) continue
                    owner[cell.row * board.cols + cell.col] = arrow.id
                }
            }
            return CellOccupancy(board.rows, board.cols, owner)
        }
    }
}
