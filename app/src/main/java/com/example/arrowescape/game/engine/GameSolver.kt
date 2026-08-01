package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.Arrow

/**
 * Validates that a board of snake-arrows is solvable by greedily removing
 * any arrow that can currently escape until the board is empty.
 */
object GameSolver {

    fun isSolvable(
        arrows: List<Arrow>,
        gridRows: Int,
        gridCols: Int
    ): Boolean {
        val remaining = arrows.filter { it.isActive }.toMutableList()

        while (remaining.isNotEmpty()) {
            val escapable = CollisionChecker.getEscapableArrows(remaining, gridRows, gridCols)
            if (escapable.isEmpty()) return false
            remaining.removeAll { it.id == escapable.first().id }
        }
        return true
    }
}
