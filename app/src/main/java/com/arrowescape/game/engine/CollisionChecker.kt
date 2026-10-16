package com.arrowescape.game.engine

import com.arrowescape.game.model.Arrow
import com.arrowescape.game.model.Cell

/**
 * Determines whether bendable snake-arrows can escape.
 * Pure logic, fully unit-testable.
 *
 * Mechanic: an arrow slithers out head-first, its body following the exact
 * trail the head carved. Therefore the ONLY cells it newly enters are along
 * the head's straight forward ray. So an arrow can escape if and only if its
 * HEAD has a clear straight path in its direction to the board edge, with no
 * other active arrow's cell in the way.
 *
 * This is what makes densely-packed BENT snakes playable - the body can bend
 * any way it likes; only the head's exit lane matters.
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

        var r = arrow.head.row + dr
        var c = arrow.head.col + dc
        while (r in 0 until gridRows && c in 0 until gridCols) {
            if (Cell(r, c) in blocked) return false
            r += dr
            c += dc
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
