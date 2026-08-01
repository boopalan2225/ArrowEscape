package com.example.arrowescape.game.model

/**
 * A single grid coordinate.
 */
data class Cell(val row: Int, val col: Int)

/**
 * A bendable "snake" arrow occupying one or more connected cells.
 *
 * The arrow points in [direction]; [head] is the leading cell (the tip).
 * [cells] are ordered from tail -> head so the shape can be drawn as a
 * connected poly-line with the arrowhead at the end.
 *
 * An arrow can escape only if EVERY cell has a clear straight path in
 * [direction] to the board edge (no other active arrow in the way). This
 * guarantees the rigid shape can slide off without overlapping anything.
 */
data class Arrow(
    val id: Int,
    val cells: List<Cell>,
    val direction: Direction,
    val isActive: Boolean = true
) {
    val head: Cell get() = cells.last()

    /** All cells as a fast lookup set. */
    val cellSet: Set<Cell> by lazy { cells.toSet() }

    fun occupies(row: Int, col: Int): Boolean =
        cells.any { it.row == row && it.col == col }
}
