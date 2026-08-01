package com.example.arrowescape.game.level

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Defines the shape that arrows are arranged into on the board.
 * Each shape provides a mask determining which grid cells are valid for arrows.
 *
 * Shapes are designed for SQUARE grids (rows == cols) so circles look round
 * and hearts look proportioned - matching the reference tile art.
 */
enum class LevelShape(val displayName: String) {
    SQUARE("Square"),
    TRIANGLE("Triangle"),
    DIAMOND("Diamond"),
    CIRCLE("Circle"),
    HEART("Heart"),
    CROSS("Cross"),
    PYRAMID("Pyramid"),
    STAR("Star");

    /**
     * Returns true if the cell at (row, col) is inside this shape.
     */
    fun contains(row: Int, col: Int, rows: Int, cols: Int): Boolean {
        return when (this) {
            SQUARE -> true
            TRIANGLE, PYRAMID -> isInTriangle(row, col, rows, cols)
            DIAMOND -> isInDiamond(row, col, rows, cols)
            CIRCLE -> isInCircle(row, col, rows, cols)
            HEART -> isInHeart(row, col, rows, cols)
            CROSS -> isInCross(row, col, rows, cols)
            STAR -> isInStar(row, col, rows, cols)
        }
    }

    /**
     * Returns all valid cell positions for this shape.
     */
    fun validCells(rows: Int, cols: Int): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (contains(r, c, rows, cols)) cells.add(r to c)
            }
        }
        return cells
    }

    companion object {
        /**
         * Picks a shape for the given level. Levels 1-3 are SQUARE (intro),
         * then shapes rotate with increasing variety.
         */
        fun forLevel(level: Int): LevelShape {
            if (level <= 3) return SQUARE

            val pool = when {
                level <= 15 -> listOf(SQUARE, TRIANGLE, DIAMOND, PYRAMID)
                level <= 40 -> listOf(TRIANGLE, DIAMOND, CIRCLE, PYRAMID, CROSS)
                else -> entries.toList()
            }
            return pool[level % pool.size]
        }
    }
}

// --- Crisp shape mask implementations (tuned for square grids) ---

private fun isInTriangle(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // Upward-pointing triangle: apex at top row, full width at bottom row.
    val center = (cols - 1) / 2f
    val progress = (row + 1).toFloat() / rows      // 0<..<=1 top->bottom
    val halfSpan = progress * (cols / 2f)
    return abs(col - center) <= halfSpan
}

private fun isInDiamond(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f
    val cy = (rows - 1) / 2f
    val dx = abs(col - cx) / (cols / 2f)
    val dy = abs(row - cy) / (rows / 2f)
    return (dx + dy) <= 1.0f
}

private fun isInCircle(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f
    val cy = (rows - 1) / 2f
    val dx = (col - cx) / (cols / 2f)
    val dy = (row - cy) / (rows / 2f)
    return (dx * dx + dy * dy) <= 1.02f
}

private fun isInHeart(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // Classic implicit heart: (x^2 + y^2 - 1)^3 - x^2 * y^3 <= 0
    // x horizontal, y vertical-up. Point at bottom, lobes at top.
    val x = ((col - (cols - 1) / 2f) / ((cols - 1) / 2f)) * 1.25f
    val y = (((rows - 1) / 2f - row) / ((rows - 1) / 2f)) * 1.30f + 0.20f
    val v = (x * x + y * y - 1f).pow(3) - x * x * y * y * y
    return v <= 0f
}

private fun isInCross(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // Plus/cross: central vertical band + central horizontal band (~1/3 thick)
    val loR = rows / 3f
    val hiR = rows * 2 / 3f
    val loC = cols / 3f
    val hiC = cols * 2 / 3f
    val inVertical = col >= loC - 0.001f && col < hiC
    val inHorizontal = row >= loR - 0.001f && row < hiR
    return inVertical || inHorizontal
}

private fun isInStar(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // Astroid gives a crisp 4-pointed star: sqrt(dx) + sqrt(dy) <= 1
    val cx = (cols - 1) / 2f
    val cy = (rows - 1) / 2f
    val dx = abs(col - cx) / (cols / 2f)
    val dy = abs(row - cy) / (rows / 2f)
    return (sqrt(dx) + sqrt(dy)) <= 1.05f
}
