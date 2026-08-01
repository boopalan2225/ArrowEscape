package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CollisionChecker - the core game logic.
 */
class CollisionCheckerTest {

    private val gridRows = 5
    private val gridCols = 5

    @Test
    fun `arrow pointing right with clear path can escape`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 1, direction = Direction.RIGHT)
        val arrows = listOf(arrow)

        assertTrue(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `arrow pointing right blocked by another arrow cannot escape`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 1, direction = Direction.RIGHT)
        val blocker = ArrowTile(id = 1, row = 2, col = 3, direction = Direction.UP)
        val arrows = listOf(arrow, blocker)

        assertFalse(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `arrow pointing up at top row can escape`() {
        val arrow = ArrowTile(id = 0, row = 0, col = 2, direction = Direction.UP)
        val arrows = listOf(arrow)

        assertTrue(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `arrow pointing down at bottom row can escape`() {
        val arrow = ArrowTile(id = 0, row = 4, col = 2, direction = Direction.DOWN)
        val arrows = listOf(arrow)

        assertTrue(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `arrow pointing left at left edge can escape`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 0, direction = Direction.LEFT)
        val arrows = listOf(arrow)

        assertTrue(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `arrow pointing right at right edge can escape`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 4, direction = Direction.RIGHT)
        val arrows = listOf(arrow)

        assertTrue(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `removed arrow does not block path`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 1, direction = Direction.RIGHT)
        val removedBlocker = ArrowTile(id = 1, row = 2, col = 3, direction = Direction.UP, isActive = false)
        val arrows = listOf(arrow, removedBlocker)

        assertTrue(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `multiple arrows in path blocks escape`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 0, direction = Direction.RIGHT)
        val blocker1 = ArrowTile(id = 1, row = 2, col = 2, direction = Direction.UP)
        val blocker2 = ArrowTile(id = 2, row = 2, col = 4, direction = Direction.DOWN)
        val arrows = listOf(arrow, blocker1, blocker2)

        assertFalse(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `arrow not in path does not block`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 1, direction = Direction.RIGHT)
        val notBlocking = ArrowTile(id = 1, row = 3, col = 3, direction = Direction.UP) // Different row
        val arrows = listOf(arrow, notBlocking)

        assertTrue(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `inactive arrow cannot escape`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 1, direction = Direction.RIGHT, isActive = false)
        val arrows = listOf(arrow)

        assertFalse(CollisionChecker.canEscape(arrow, arrows, gridRows, gridCols))
    }

    @Test
    fun `getEscapableArrows returns correct arrows`() {
        val canEscape1 = ArrowTile(id = 0, row = 0, col = 0, direction = Direction.UP)
        val canEscape2 = ArrowTile(id = 1, row = 4, col = 4, direction = Direction.DOWN)
        val blocked = ArrowTile(id = 2, row = 2, col = 2, direction = Direction.RIGHT)
        val blocker = ArrowTile(id = 3, row = 2, col = 4, direction = Direction.LEFT)
        val arrows = listOf(canEscape1, canEscape2, blocked, blocker)

        val escapable = CollisionChecker.getEscapableArrows(arrows, gridRows, gridCols)

        assertTrue(escapable.any { it.id == 0 })
        assertTrue(escapable.any { it.id == 1 })
        // blocked (id=2) is blocked by blocker (id=3) which is at col 4, and blocked points right from col 2
        // blocker (id=3) points left and has clear path (nothing to its left in row 2 except blocked which doesn't block LEFT direction)
        assertTrue(escapable.any { it.id == 3 })
    }

    @Test
    fun `findBlockingArrows returns all blockers`() {
        val arrow = ArrowTile(id = 0, row = 2, col = 0, direction = Direction.RIGHT)
        val blocker1 = ArrowTile(id = 1, row = 2, col = 2, direction = Direction.UP)
        val blocker2 = ArrowTile(id = 2, row = 2, col = 4, direction = Direction.DOWN)
        val arrows = listOf(arrow, blocker1, blocker2)

        val blockers = CollisionChecker.findBlockingArrows(arrow, arrows, gridRows, gridCols)

        assertEquals(2, blockers.size)
        assertTrue(blockers.any { it.id == 1 })
        assertTrue(blockers.any { it.id == 2 })
    }

    @Test
    fun `all four directions work correctly`() {
        // Test each direction at center with clear paths
        val up = ArrowTile(id = 0, row = 2, col = 2, direction = Direction.UP)
        val down = ArrowTile(id = 1, row = 2, col = 1, direction = Direction.DOWN)
        val left = ArrowTile(id = 2, row = 1, col = 2, direction = Direction.LEFT)
        val right = ArrowTile(id = 3, row = 3, col = 2, direction = Direction.RIGHT)
        val arrows = listOf(up, down, left, right)

        // All should be able to escape (they're in different rows/cols for their directions)
        assertTrue(CollisionChecker.canEscape(up, arrows, gridRows, gridCols))
        assertTrue(CollisionChecker.canEscape(down, arrows, gridRows, gridCols))
        assertTrue(CollisionChecker.canEscape(left, arrows, gridRows, gridCols))
        assertTrue(CollisionChecker.canEscape(right, arrows, gridRows, gridCols))
    }
}
