package com.example.arrowescape.game.model

/**
 * Represents a single arrow tile on the game board.
 *
 * @param id Unique identifier for this arrow
 * @param row The row position (0-indexed from top)
 * @param col The column position (0-indexed from left)
 * @param direction The direction this arrow points
 * @param isActive Whether this arrow is still on the board
 */
data class ArrowTile(
    val id: Int,
    val row: Int,
    val col: Int,
    val direction: Direction,
    val isActive: Boolean = true
)
