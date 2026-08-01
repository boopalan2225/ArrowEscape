package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for GameSolver.
 */
class GameSolverTest {

    @Test
    fun `simple solvable board returns true`() {
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.UP),
            ArrowTile(1, 2, 2, Direction.DOWN)
        )

        assertTrue(GameSolver.isSolvable(arrows, 3, 3))
    }

    @Test
    fun `empty board is solvable`() {
        assertTrue(GameSolver.isSolvable(emptyList(), 3, 3))
    }

    @Test
    fun `deadlocked board returns false`() {
        // Two arrows blocking each other in a cycle
        val arrows = listOf(
            ArrowTile(0, 1, 0, Direction.RIGHT),
            ArrowTile(1, 1, 2, Direction.LEFT)
        )

        assertFalse(GameSolver.isSolvable(arrows, 3, 3))
    }

    @Test
    fun `findSolution returns valid sequence for solvable board`() {
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.UP),
            ArrowTile(1, 0, 1, Direction.UP),
            ArrowTile(2, 2, 0, Direction.DOWN)
        )

        val solution = GameSolver.findSolution(arrows, 3, 3)

        assertNotNull(solution)
        assertEquals(3, solution!!.size)
    }

    @Test
    fun `findSolution returns null for unsolvable board`() {
        val arrows = listOf(
            ArrowTile(0, 1, 0, Direction.RIGHT),
            ArrowTile(1, 1, 2, Direction.LEFT)
        )

        val solution = GameSolver.findSolution(arrows, 3, 3)

        assertNull(solution)
    }

    @Test
    fun `countEscapableArrows returns correct count`() {
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.UP),      // Can escape
            ArrowTile(1, 1, 0, Direction.RIGHT),   // Blocked by arrow at (1,2)
            ArrowTile(2, 1, 2, Direction.DOWN),    // Can escape
            ArrowTile(3, 2, 2, Direction.DOWN)     // Can escape
        )

        val count = GameSolver.countEscapableArrows(arrows, 3, 3)

        assertEquals(3, count)
    }

    @Test
    fun `complex solvable board with dependencies`() {
        // A board where arrows must be removed in a specific order
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.DOWN),    // Blocked by 1
            ArrowTile(1, 1, 0, Direction.LEFT),    // Can escape first
            ArrowTile(2, 2, 0, Direction.UP),      // Blocked by 0 and 1
        )

        assertTrue(GameSolver.isSolvable(arrows, 3, 3))
    }
}
