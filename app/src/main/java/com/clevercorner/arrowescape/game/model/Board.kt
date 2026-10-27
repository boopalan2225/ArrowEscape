package com.clevercorner.arrowescape.game.model

/**
 * A redirector pinned to an empty cell. Any arrow whose escape run crosses it
 * leaves in [direction] instead of the way it came in.
 *
 * Deflectors are PERMANENT, not one-shot. That is a deliberate design call:
 * a one-shot dot (as in the reference generator) lets a player burn the only
 * redirect a later arrow needed, producing an unwinnable board. With a
 * permanent dot every arrow's route depends solely on which arrows are still
 * on the board, so clearing an arrow can only ever help - the board can never
 * dead-end, and a greedy solve is provably complete.
 */
data class Deflector(val cell: Cell, val direction: Direction)

/**
 * The playfield: its bounds plus whatever furniture sits on it.
 *
 * Every escape rule in the game reads this one object, so collision checks,
 * the solver and the generator can never drift apart on what "the board" is.
 */
data class Board(
    val rows: Int,
    val cols: Int,
    val deflectors: List<Deflector> = emptyList()
) {
    /** Fast lookup used by the escape walk. */
    val deflectorAt: Map<Cell, Direction> = deflectors.associate { it.cell to it.direction }

    fun contains(cell: Cell): Boolean =
        cell.row in 0 until rows && cell.col in 0 until cols
}
