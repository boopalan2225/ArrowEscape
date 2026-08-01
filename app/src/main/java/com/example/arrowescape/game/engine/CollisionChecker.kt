package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.Arrow
import com.example.arrowescape.game.model.Cell
import com.example.arrowescape.game.model.Direction

/**
 * Determines whether bendable snake-arrows can escape.
 * Pure logic, fully unit-testable.
 *
 * Rule: an arrow escapes only if EVERY one of its cells has a clear
 * straight path in the arrow's direction to the board edge, with no other
 * active arrow's cell in the way. This lets the rigid shape slide off
 * cleanly without ever overlapping another arrow.
 */
object CollisionChecker {

    fun canEscape(
        arrow: Arrow,
        activeArrows: List<Arrow>,
        gridRows: Int,
        gridCols: Int
    ): Boolean {
        if (!arrow.isActive) return false

        // Cells occupied by all OTHER active arrows.
        val blocked = HashSet<Cell>()
        for (other in activeArrows) {
            if (other.id == arrow.id || !other.isActive) continue
            blocked.addAll(other.cells)
        }

        val dr = arrow.direction.deltaRow()
        val dc = arrow.direction.deltaCol()

        // Every cell must have a clear ray to the edge.
        for (cell in arrow.cells) {
            var r = cell.row + dr
            var c = cell.col + dc
            while (r in 0 until gridRows && c in 0 until gridCols) {
                if (Cell(r, c) in blocked) return false
                r += dr
                c += dc
            }
        }
        return true
    }

    fun getEscapableArrows(
        activeArrows: List<Arrow>,
        gridRows: Int,
        gridCols: Int
    ): List<Arrow> = activeArrows.filter {
        canEscape(it, activeArrows, gridRows, gridCols)
    }
}
