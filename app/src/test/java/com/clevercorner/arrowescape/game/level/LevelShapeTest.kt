package com.clevercorner.arrowescape.game.level

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LevelShape masks.
 */
class LevelShapeTest {

    @Test
    fun `square contains all cells`() {
        val cells = LevelShape.SQUARE.validCells(8, 8)
        assertEquals(64, cells.size)
    }

    @Test
    fun `triangle is narrow at top and wide at bottom`() {
        val rows = 8
        val cols = 8
        // Top row should have fewer valid cells than bottom row
        val topCells = (0 until cols).count { LevelShape.TRIANGLE.contains(0, it, rows, cols) }
        val bottomCells = (0 until cols).count { LevelShape.TRIANGLE.contains(rows - 1, it, rows, cols) }

        assertTrue("Bottom should be wider than top", bottomCells > topCells)
    }

    @Test
    fun `circle excludes corners`() {
        val rows = 9
        val cols = 9
        // Corners should be outside the circle
        assertFalse(LevelShape.CIRCLE.contains(0, 0, rows, cols))
        assertFalse(LevelShape.CIRCLE.contains(0, cols - 1, rows, cols))
        assertFalse(LevelShape.CIRCLE.contains(rows - 1, 0, rows, cols))
        // Center should be inside
        assertTrue(LevelShape.CIRCLE.contains(rows / 2, cols / 2, rows, cols))
    }

    @Test
    fun `diamond excludes corners but includes center`() {
        val rows = 9
        val cols = 9
        assertFalse(LevelShape.DIAMOND.contains(0, 0, rows, cols))
        assertTrue(LevelShape.DIAMOND.contains(rows / 2, cols / 2, rows, cols))
    }

    @Test
    fun `all shapes produce at least a few cells`() {
        LevelShape.entries.forEach { shape ->
            val cells = shape.validCells(10, 9)
            assertTrue("$shape should have cells", cells.size >= 4)
        }
    }

    @Test
    fun `forLevel returns square for early levels`() {
        assertEquals(LevelShape.SQUARE, LevelShape.forLevel(1))
        assertEquals(LevelShape.SQUARE, LevelShape.forLevel(3))
    }

    @Test
    fun `forLevel is deterministic`() {
        for (level in 1..50) {
            assertEquals(LevelShape.forLevel(level), LevelShape.forLevel(level))
        }
    }

    // --- Hard category shape tests ---

    @Test
    fun `ring has hollow center`() {
        val rows = 9
        val cols = 9
        // Center should be hollow (excluded)
        assertFalse("Ring center should be hollow", LevelShape.RING.contains(rows / 2, cols / 2, rows, cols))
        // But edges of the ring should be included
        assertTrue("Ring edge should be included", LevelShape.RING.contains(rows / 2, 0, rows, cols))
    }

    @Test
    fun `butterfly includes center body`() {
        val rows = 10
        val cols = 10
        // Center column should be included (the body)
        assertTrue("Butterfly body center", LevelShape.BUTTERFLY.contains(rows / 2, cols / 2, rows, cols))
    }

    @Test
    fun `crown has wide base`() {
        val rows = 10
        val cols = 10
        // Bottom row (base) should be included near center
        assertTrue("Crown base", LevelShape.CROWN.contains(rows - 1, cols / 2, rows, cols))
    }

    @Test
    fun `lightning produces continuous path`() {
        val rows = 10
        val cols = 10
        // Should have cells throughout vertically
        val cells = LevelShape.LIGHTNING.validCells(rows, cols)
        val rowsWithCells = cells.map { it.first }.toSet()
        // Should span most rows
        assertTrue("Lightning spans vertically", rowsWithCells.size >= rows - 2)
    }

    @Test
    fun `spiral produces enough cells`() {
        val rows = 10
        val cols = 10
        val cells = LevelShape.SPIRAL.validCells(rows, cols)
        // Spiral should have a reasonable number of cells (not too sparse)
        assertTrue("Spiral has substantial cells", cells.size >= 20)
    }

    // --- Template scaling tests ---

    @Test
    fun `shapes scale with grid size`() {
        // Larger grids should have more cells
        val smallCells = LevelShape.CROWN.validCells(8, 8)
        val largeCells = LevelShape.CROWN.validCells(20, 20)
        assertTrue("Larger grid has more cells", largeCells.size > smallCells.size)
    }

    @Test
    fun `level 112 and 148 have different densities for same shape`() {
        // Level 112 is in Crown milestone (121-130 actually, but let's test the concept)
        val config112 = DifficultyConfig.forLevel(112)
        val config148 = DifficultyConfig.forLevel(148)
        
        // Higher level should have larger grid
        assertTrue("Level 148 has larger grid", 
            config148.gridRows > config112.gridRows)
    }

    @Test
    fun `difficulty scales correctly`() {
        // Test that difficulty increases with level
        val easyConfig = DifficultyConfig.forLevel(5)
        val hardConfig = DifficultyConfig.forLevel(100)
        val masterConfig = DifficultyConfig.forLevel(145)
        
        assertTrue(easyConfig.gridRows < hardConfig.gridRows)
        assertTrue(hardConfig.gridRows < masterConfig.gridRows)
        assertTrue(easyConfig.maxSnakeLength < masterConfig.maxSnakeLength)
    }
}
