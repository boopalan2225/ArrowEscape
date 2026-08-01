package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction

/**
 * Determines whether arrows can escape based on their position and direction.
 * This is pure logic with no UI dependencies - fully unit testable.
 */
object CollisionChecker {

    /**
     * Checks if the given arrow can escape (has a clear path in its direction).
     *
     * @param arrow The arrow to check
     * @param activeArrows All currently active arrows on the board
     * @param gridRows Total number of rows in the grid (for boundary detection)
     * @param gridCols Total number of columns in the grid (for boundary detection)
     * @return true if the arrow can escape, false if blocked
     */
    fun canEscape(
        arrow: ArrowTile,
        activeArrows: List<ArrowTile>,
        gridRows: Int,
        gridCols: Int
    ): Boolean {
        if (!arrow.isActive) return false

        // Get all other active arrows (exclude the one we're checking)
        val otherArrows = activeArrows.filter { it.id != arrow.id && it.isActive }

        // Check the path in the arrow's direction
        return !isPathBlocked(arrow, otherArrows, gridRows, gridCols)
    }

    /**
     * Checks if there's any active arrow blocking the escape path.
     */
    private fun isPathBlocked(
        arrow: ArrowTile,
        otherArrows: List<ArrowTile>,
        gridRows: Int,
        gridCols: Int
    ): Boolean {
        val deltaRow = arrow.direction.deltaRow()
        val deltaCol = arrow.direction.deltaCol()

        var checkRow = arrow.row + deltaRow
        var checkCol = arrow.col + deltaCol

        // Walk along the path until we hit the edge
        while (checkRow in 0 until gridRows && checkCol in 0 until gridCols) {
            // Check if any active arrow is at this position
            if (otherArrows.any { it.row == checkRow && it.col == checkCol }) {
                return true // Path is blocked
            }
            checkRow += deltaRow
            checkCol += deltaCol
        }

        return false // Path is clear to the edge
    }

    /**
     * Returns all arrows that are currently able to escape.
     */
    fun getEscapableArrows(
        activeArrows: List<ArrowTile>,
        gridRows: Int,
        gridCols: Int
    ): List<ArrowTile> {
        return activeArrows.filter { arrow ->
            canEscape(arrow, activeArrows, gridRows, gridCols)
        }
    }

    /**
     * Finds the blocking arrow(s) for a given arrow.
     * Used for potential hint features.
     */
    fun findBlockingArrows(
        arrow: ArrowTile,
        activeArrows: List<ArrowTile>,
        gridRows: Int,
        gridCols: Int
    ): List<ArrowTile> {
        if (!arrow.isActive) return emptyList()

        val blocking = mutableListOf<ArrowTile>()
        val otherArrows = activeArrows.filter { it.id != arrow.id && it.isActive }

        val deltaRow = arrow.direction.deltaRow()
        val deltaCol = arrow.direction.deltaCol()

        var checkRow = arrow.row + deltaRow
        var checkCol = arrow.col + deltaCol

        while (checkRow in 0 until gridRows && checkCol in 0 until gridCols) {
            val blocker = otherArrows.find { it.row == checkRow && it.col == checkCol }
            if (blocker != null) {
                blocking.add(blocker)
            }
            checkRow += deltaRow
            checkCol += deltaCol
        }

        return blocking
    }
}
