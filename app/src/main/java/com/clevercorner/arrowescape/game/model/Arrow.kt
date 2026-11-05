package com.clevercorner.arrowescape.game.model

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
 * An arrow escapes head-first: only the route its head carves - straight in
 * [direction] until a deflector bends it - has to be clear of other arrows.
 *
 * [colorGroup] marks a colour-locked pair. Both members leave together or not
 * at all, and they ignore each other while leaving, so the player has to spot
 * two openings at once instead of one.
 *
 * [sequenceKey] marks a numbered arrow. Numbered arrows must leave in
 * ascending order, so a board can demand a plan rather than only a clear
 * lane - see `SequencePlanner`. Unnumbered arrows are unaffected and stay
 * free, which keeps the mechanic a seasoning rather than a straitjacket.
 */
data class Arrow(
    val id: Int,
    val cells: List<Cell>,
    val direction: Direction,
    val isActive: Boolean = true,
    val colorGroup: Int? = null,
    val sequenceKey: Int? = null
) {
    val head: Cell get() = cells.last()

    /** All cells as a fast lookup set. */
    val cellSet: Set<Cell> by lazy { cells.toSet() }

    fun occupies(row: Int, col: Int): Boolean =
        cells.any { it.row == row && it.col == col }
}
