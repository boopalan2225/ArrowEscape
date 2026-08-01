package com.example.arrowescape.game.level

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
}
