package com.example.arrowescape.game.level

import com.example.arrowescape.game.engine.GameSolver
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LevelGenerator.
 */
class LevelGeneratorTest {

    @Test
    fun `generateLevel returns reasonable arrow count for level 1`() {
        val level = LevelGenerator.generateLevel(1)

        assertTrue("Level 1 should have at least 4 arrows", level.arrows.size >= 4)
        assertTrue("Grid rows should be reasonable", level.gridRows in 4..14)
        assertTrue("Grid cols should be reasonable", level.gridCols in 4..14)
    }

    @Test
    fun `generated levels are solvable`() {
        for (levelNum in listOf(1, 5, 10, 25, 50, 75, 100, 150)) {
            val level = LevelGenerator.generateLevel(levelNum)
            assertTrue(
                "Level $levelNum should be solvable",
                GameSolver.isSolvable(level.arrows, level.gridRows, level.gridCols)
            )
        }
    }

    @Test
    fun `same level number generates same board`() {
        val a = LevelGenerator.generateLevel(42)
        val b = LevelGenerator.generateLevel(42)

        assertEquals(a.gridRows, b.gridRows)
        assertEquals(a.gridCols, b.gridCols)
        assertEquals(a.shape, b.shape)
        assertEquals(a.arrows.size, b.arrows.size)

        a.arrows.forEachIndexed { index, arrow ->
            assertEquals(arrow.row, b.arrows[index].row)
            assertEquals(arrow.col, b.arrows[index].col)
            assertEquals(arrow.direction, b.arrows[index].direction)
        }
    }

    @Test
    fun `different levels generate different boards`() {
        val a = LevelGenerator.generateLevel(10)
        val b = LevelGenerator.generateLevel(11)

        val configA = a.arrows.map { Triple(it.row, it.col, it.direction) }.toSet()
        val configB = b.arrows.map { Triple(it.row, it.col, it.direction) }.toSet()

        assertNotEquals("Different levels should differ", configA, configB)
    }

    @Test
    fun `tutorial level is a filled square and solvable`() {
        val level = LevelGenerator.generateTutorialLevel()

        assertEquals(LevelShape.SQUARE, level.shape)
        assertEquals(16, level.arrows.size) // 4x4 fully filled
        assertTrue(
            "Tutorial should be solvable",
            GameSolver.isSolvable(level.arrows, level.gridRows, level.gridCols)
        )
    }

    @Test
    fun `every shape cell is filled with an arrow`() {
        for (levelNum in listOf(4, 12, 25, 45, 70, 95)) {
            val level = LevelGenerator.generateLevel(levelNum)
            val expectedCells = level.shape.validCells(level.gridRows, level.gridCols).size
            assertEquals(
                "Level $levelNum (${level.shape}) should fill every shape cell",
                expectedCells,
                level.arrows.size
            )
        }
    }

    @Test
    fun `all arrows have unique IDs`() {
        val level = LevelGenerator.generateLevel(30)
        val ids = level.arrows.map { it.id }
        assertEquals("All arrows should have unique IDs", ids.size, ids.toSet().size)
    }

    @Test
    fun `no arrows overlap positions`() {
        val level = LevelGenerator.generateLevel(30)
        val positions = level.arrows.map { it.row to it.col }
        assertEquals("No arrows should overlap", positions.size, positions.toSet().size)
    }

    @Test
    fun `arrows stay within shape mask`() {
        // Every arrow must be inside the level's shape
        for (levelNum in listOf(8, 20, 45, 90)) {
            val level = LevelGenerator.generateLevel(levelNum)
            level.arrows.forEach { arrow ->
                assertTrue(
                    "Arrow at (${arrow.row},${arrow.col}) should be inside ${level.shape} for level $levelNum",
                    level.shape.contains(arrow.row, arrow.col, level.gridRows, level.gridCols)
                )
            }
        }
    }

    @Test
    fun `shape rotates across levels`() {
        // Level 1-3 are SQUARE, later levels vary
        assertEquals(LevelShape.SQUARE, LevelGenerator.generateLevel(1).shape)
        assertEquals(LevelShape.SQUARE, LevelGenerator.generateLevel(2).shape)
    }
}
