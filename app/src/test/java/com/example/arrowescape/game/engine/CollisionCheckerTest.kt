package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.Arrow
import com.example.arrowescape.game.model.Cell
import com.example.arrowescape.game.model.Direction
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CollisionChecker with bendable snake-arrows.
 */
class CollisionCheckerTest {

    private val rows = 5
    private val cols = 5

    private fun arrow(id: Int, dir: Direction, vararg cells: Pair<Int, Int>) =
        Arrow(id, cells.map { Cell(it.first, it.second) }, dir)

    @Test
    fun `single-cell arrow with clear path can escape`() {
        val a = arrow(0, Direction.RIGHT, 2 to 1)
        assertTrue(CollisionChecker.canEscape(a, listOf(a), rows, cols))
    }

    @Test
    fun `single-cell arrow blocked cannot escape`() {
        val a = arrow(0, Direction.RIGHT, 2 to 1)
        val b = arrow(1, Direction.UP, 2 to 3)
        assertFalse(CollisionChecker.canEscape(a, listOf(a, b), rows, cols))
    }

    @Test
    fun `arrow at edge escapes`() {
        val a = arrow(0, Direction.UP, 0 to 2)
        assertTrue(CollisionChecker.canEscape(a, listOf(a), rows, cols))
    }

    @Test
    fun `inactive blocker does not block`() {
        val a = arrow(0, Direction.RIGHT, 2 to 1)
        val b = arrow(1, Direction.UP, 2 to 3).copy(isActive = false)
        assertTrue(CollisionChecker.canEscape(a, listOf(a, b), rows, cols))
    }

    @Test
    fun `multi-cell snake escapes only if every cell path is clear`() {
        // Horizontal snake at row 2, cols 0-1, pointing UP.
        val snake = arrow(0, Direction.UP, 2 to 0, 2 to 1)
        // Blocker above col 1 -> should block.
        val blocker = arrow(1, Direction.DOWN, 0 to 1)
        assertFalse(CollisionChecker.canEscape(snake, listOf(snake, blocker), rows, cols))

        // Blocker not in any cell's column -> does not block.
        val faraway = arrow(2, Direction.DOWN, 0 to 4)
        assertTrue(CollisionChecker.canEscape(snake, listOf(snake, faraway), rows, cols))
    }

    @Test
    fun `bent snake escapes with clear paths`() {
        // L-shaped snake: (2,0)-(2,1)-(1,1), pointing UP, top rows clear.
        val snake = arrow(0, Direction.UP, 2 to 0, 2 to 1, 1 to 1)
        assertTrue(CollisionChecker.canEscape(snake, listOf(snake), rows, cols))
    }

    @Test
    fun `all four directions work`() {
        val up = arrow(0, Direction.UP, 0 to 2)
        val down = arrow(1, Direction.DOWN, 4 to 1)
        val left = arrow(2, Direction.LEFT, 1 to 0)
        val right = arrow(3, Direction.RIGHT, 3 to 4)
        val all = listOf(up, down, left, right)
        assertTrue(CollisionChecker.canEscape(up, all, rows, cols))
        assertTrue(CollisionChecker.canEscape(down, all, rows, cols))
        assertTrue(CollisionChecker.canEscape(left, all, rows, cols))
        assertTrue(CollisionChecker.canEscape(right, all, rows, cols))
    }

    @Test
    fun `getEscapableArrows filters correctly`() {
        val free = arrow(0, Direction.UP, 0 to 0)
        val blocked = arrow(1, Direction.RIGHT, 2 to 2)
        val blocker = arrow(2, Direction.LEFT, 2 to 4)
        val result = CollisionChecker.getEscapableArrows(listOf(free, blocked, blocker), rows, cols)
        assertTrue(result.any { it.id == 0 })
    }
}
