package com.example.arrowescape.game.level

import com.example.arrowescape.game.engine.GameSolver
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LevelGenerator.
 */
class LevelGeneratorTest {

    @Test
    fun `generateLevel returns correct arrow count for level 1`() {
        val (arrows, rows, cols) = LevelGenerator.generateLevel(1)

        assertTrue("Level 1 should have 5-10 arrows", arrows.size in 5..10)
        assertTrue("Grid rows should be reasonable", rows in 3..10)
        assertTrue("Grid cols should be reasonable", cols in 3..10)
    }

    @Test
    fun `generated levels are solvable`() {
        // Test multiple levels
        for (level in listOf(1, 5, 10, 25, 50, 75, 100)) {
            val (arrows, rows, cols) = LevelGenerator.generateLevel(level)

            assertTrue(
                "Level $level should be solvable",
                GameSolver.isSolvable(arrows, rows, cols)
            )
        }
    }

    @Test
    fun `same level number generates same board`() {
        val (arrows1, rows1, cols1) = LevelGenerator.generateLevel(42)
        val (arrows2, rows2, cols2) = LevelGenerator.generateLevel(42)

        assertEquals("Same level should generate same rows", rows1, rows2)
        assertEquals("Same level should generate same cols", cols1, cols2)
        assertEquals("Same level should generate same arrow count", arrows1.size, arrows2.size)

        // Check arrow positions match
        arrows1.forEachIndexed { index, arrow ->
            assertEquals(arrow.row, arrows2[index].row)
            assertEquals(arrow.col, arrows2[index].col)
            assertEquals(arrow.direction, arrows2[index].direction)
        }
    }

    @Test
    fun `different levels generate different boards`() {
        val (arrows1, _, _) = LevelGenerator.generateLevel(10)
        val (arrows2, _, _) = LevelGenerator.generateLevel(11)

        // Different levels should have different configurations
        val config1 = arrows1.map { Triple(it.row, it.col, it.direction) }.toSet()
        val config2 = arrows2.map { Triple(it.row, it.col, it.direction) }.toSet()

        assertNotEquals("Different levels should generate different boards", config1, config2)
    }

    @Test
    fun `tutorial level is simple and solvable`() {
        val (arrows, rows, cols) = LevelGenerator.generateTutorialLevel()

        assertTrue("Tutorial should have few arrows", arrows.size <= 6)
        assertTrue("Tutorial should be solvable", GameSolver.isSolvable(arrows, rows, cols))
    }

    @Test
    fun `higher levels have more arrows`() {
        val (arrowsLow, _, _) = LevelGenerator.generateLevel(5)
        val (arrowsHigh, _, _) = LevelGenerator.generateLevel(50)

        assertTrue(
            "Higher levels should have more arrows",
            arrowsHigh.size > arrowsLow.size
        )
    }

    @Test
    fun `all arrows have unique IDs`() {
        val (arrows, _, _) = LevelGenerator.generateLevel(30)

        val ids = arrows.map { it.id }
        assertEquals("All arrows should have unique IDs", ids.size, ids.toSet().size)
    }

    @Test
    fun `no arrows overlap positions`() {
        val (arrows, _, _) = LevelGenerator.generateLevel(30)

        val positions = arrows.map { it.row to it.col }
        assertEquals("No arrows should overlap", positions.size, positions.toSet().size)
    }
}
