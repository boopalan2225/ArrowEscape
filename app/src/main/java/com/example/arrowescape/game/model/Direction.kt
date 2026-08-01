package com.example.arrowescape.game.model

/**
 * Represents the four possible arrow directions.
 */
enum class Direction {
    UP, DOWN, LEFT, RIGHT;

    /**
     * Returns the delta row change for movement in this direction.
     */
    fun deltaRow(): Int = when (this) {
        UP -> -1
        DOWN -> 1
        LEFT, RIGHT -> 0
    }

    /**
     * Returns the delta column change for movement in this direction.
     */
    fun deltaCol(): Int = when (this) {
        LEFT -> -1
        RIGHT -> 1
        UP, DOWN -> 0
    }

    /**
     * Returns the opposite direction.
     */
    fun opposite(): Direction = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
    }
}
