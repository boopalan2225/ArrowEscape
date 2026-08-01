package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction

/**
 * Validates that a generated level is solvable.
 * Uses a simple greedy solver - if at least one valid solution exists, the level is solvable.
 */
object GameSolver {

    /**
     * Checks if the given board configuration is solvable.
     *
     * @param arrows The initial list of arrows
     * @param gridRows Number of rows in the grid
     * @param gridCols Number of columns in the grid
     * @return true if the board can be completely cleared
     */
    fun isSolvable(
        arrows: List<ArrowTile>,
        gridRows: Int,
        gridCols: Int
    ): Boolean {
        // Make a mutable copy of the arrows
        val remaining = arrows.map { it.copy() }.toMutableList()

        // Keep trying to remove escapable arrows until we can't anymore
        while (remaining.isNotEmpty()) {
            val escapable = CollisionChecker.getEscapableArrows(remaining, gridRows, gridCols)

            if (escapable.isEmpty()) {
                // No arrows can escape but board isn't empty - unsolvable
                return false
            }

            // Remove the first escapable arrow (greedy approach)
            // For a proper solver, we'd need to try all orderings, but greedy works
            // for our reverse-generated boards
            remaining.removeAll { it.id == escapable.first().id }
        }

        return true
    }

    /**
     * Finds one valid solution (sequence of arrow removals).
     * Returns null if unsolvable.
     */
    fun findSolution(
        arrows: List<ArrowTile>,
        gridRows: Int,
        gridCols: Int
    ): List<ArrowTile>? {
        val solution = mutableListOf<ArrowTile>()
        val remaining = arrows.map { it.copy() }.toMutableList()

        while (remaining.isNotEmpty()) {
            val escapable = CollisionChecker.getEscapableArrows(remaining, gridRows, gridCols)

            if (escapable.isEmpty()) {
                return null // Unsolvable
            }

            val toRemove = escapable.first()
            solution.add(toRemove)
            remaining.removeAll { it.id == toRemove.id }
        }

        return solution
    }

    /**
     * Counts the number of currently escapable arrows.
     * Useful for difficulty assessment.
     */
    fun countEscapableArrows(
        arrows: List<ArrowTile>,
        gridRows: Int,
        gridCols: Int
    ): Int {
        return CollisionChecker.getEscapableArrows(arrows, gridRows, gridCols).size
    }
}
