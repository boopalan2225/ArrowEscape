package com.clevercorner.arrowescape.game.level

import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.Deflector
import com.clevercorner.arrowescape.game.model.Direction
import kotlin.random.Random

/**
 * Decides where the route-bending dots go.
 *
 * A dot needs a cell of its own, and most shape templates fill their mask
 * solidly, so the planner *reserves* the cells before the carver runs rather
 * than hunting for leftovers afterwards. Reserved cells are punched out of
 * the fill, which is why a dot always reads as a deliberate hole in the
 * artwork instead of a bite taken out of its silhouette.
 */
object DeflectorPlanner {

    /** Dots this close together turn into an unreadable minefield. */
    private const val MIN_SPACING = 2

    /**
     * Picks up to [count] interior cells, well spaced, and dresses each with a
     * direction. Interior means all four neighbours are part of the shape:
     * a dot on the rim would just nibble the outline.
     */
    fun plan(shapeCells: Set<Cell>, count: Int, random: Random): List<Deflector> {
        if (count <= 0) return emptyList()

        val interior = shapeCells.filter { cell ->
            neighbours(cell).all { it in shapeCells }
        }
        if (interior.isEmpty()) return emptyList()

        val chosen = mutableListOf<Cell>()
        for (cell in interior.shuffled(random)) {
            if (chosen.size == count) break
            if (chosen.any { chebyshev(it, cell) < MIN_SPACING }) continue
            chosen.add(cell)
        }

        return chosen.map { Deflector(it, Direction.entries.random(random)) }
    }

    private fun chebyshev(a: Cell, b: Cell): Int =
        maxOf(kotlin.math.abs(a.row - b.row), kotlin.math.abs(a.col - b.col))

    private fun neighbours(cell: Cell): List<Cell> = listOf(
        Cell(cell.row - 1, cell.col),
        Cell(cell.row + 1, cell.col),
        Cell(cell.row, cell.col - 1),
        Cell(cell.row, cell.col + 1)
    )
}
