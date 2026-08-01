package com.example.arrowescape.game.level

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Defines the shape that arrows are arranged into on the board.
 * Each shape provides a mask determining which grid cells are valid for arrows.
 *
 * This is the signature feature - levels form recognizable shapes like
 * triangles, circles, hearts, diamonds rather than plain rectangles.
 */
enum class LevelShape(val displayName: String) {
    SQUARE("Square"),
    TRIANGLE("Triangle"),
    DIAMOND("Diamond"),
    CIRCLE("Circle"),
    HEART("Heart"),
    CROSS("Cross"),
    STAR("Star"),
    PYRAMID("Pyramid");

    /**
     * Returns true if the cell at (row, col) is inside this shape
     * for a grid of the given dimensions.
     */
    fun contains(row: Int, col: Int, rows: Int, cols: Int): Boolean {
        return when (this) {
            SQUARE -> true
            TRIANGLE -> isInTriangle(row, col, rows, cols)
            DIAMOND -> isInDiamond(row, col, rows, cols)
            CIRCLE -> isInCircle(row, col, rows, cols)
            HEART -> isInHeart(row, col, rows, cols)
            CROSS -> isInCross(row, col, rows, cols)
            STAR -> isInStar(row, col, rows, cols)
            PYRAMID -> isInPyramid(row, col, rows, cols)
        }
    }

    /**
     * Returns all valid cell positions for this shape.
     */
    fun validCells(rows: Int, cols: Int): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (contains(r, c, rows, cols)) {
                    cells.add(r to c)
                }
            }
        }
        return cells
    }

    companion object {
        /**
         * Picks a shape for the given level.
         * Level 1 is always SQUARE (tutorial), then shapes rotate with
         * increasing variety at higher levels.
         */
        fun forLevel(level: Int): LevelShape {
            if (level <= 3) return SQUARE // Easy intro levels

            // Rotate through shapes deterministically
            val shapePool = when {
                level <= 15 -> listOf(SQUARE, TRIANGLE, DIAMOND, PYRAMID)
                level <= 40 -> listOf(TRIANGLE, DIAMOND, CIRCLE, PYRAMID, CROSS)
                else -> entries.toList() // All shapes including HEART and STAR
            }
            return shapePool[level % shapePool.size]
        }
    }
}

// --- Shape mask implementations ---

private fun isInTriangle(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // Upward-pointing triangle: widens toward the bottom
    val centerCol = (cols - 1) / 2f
    val progress = row / (rows - 1).toFloat() // 0 at top, 1 at bottom
    val halfWidth = progress * (cols / 2f)
    return abs(col - centerCol) <= halfWidth + 0.5f
}

private fun isInPyramid(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // Stepped pyramid - similar to triangle but slightly fuller
    val centerCol = (cols - 1) / 2f
    val progress = row / (rows - 1).toFloat()
    val halfWidth = progress * (cols / 2f) + 0.5f
    return abs(col - centerCol) <= halfWidth
}

private fun isInDiamond(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val centerRow = (rows - 1) / 2f
    val centerCol = (cols - 1) / 2f
    val normRow = abs(row - centerRow) / (rows / 2f)
    val normCol = abs(col - centerCol) / (cols / 2f)
    return (normRow + normCol) <= 1.05f
}

private fun isInCircle(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val centerRow = (rows - 1) / 2f
    val centerCol = (cols - 1) / 2f
    val normRow = (row - centerRow) / (rows / 2f)
    val normCol = (col - centerCol) / (cols / 2f)
    return sqrt(normRow.pow(2) + normCol.pow(2)) <= 1.05f
}

private fun isInHeart(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // Map grid to heart curve coordinates
    // Heart equation: (x^2 + y^2 - 1)^3 - x^2 * y^3 <= 0
    val x = (col - (cols - 1) / 2f) / (cols / 2.2f)
    // Flip y so heart points down correctly; shift up a bit
    val y = -((row - (rows - 1) / 2f) / (rows / 2.2f)) + 0.2f
    val value = (x * x + y * y - 1).pow(3) - x * x * y * y * y
    return value <= 0f
}

private fun isInCross(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val third = 0.32f
    val colLow = cols * third
    val colHigh = cols * (1 - third)
    val rowLow = rows * third
    val rowHigh = rows * (1 - third)
    val inVertical = col >= colLow - 0.5f && col <= colHigh - 0.5f
    val inHorizontal = row >= rowLow - 0.5f && row <= rowHigh - 0.5f
    return inVertical || inHorizontal
}

private fun isInStar(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // 4-pointed star: diamond body + thinner extensions
    val centerRow = (rows - 1) / 2f
    val centerCol = (cols - 1) / 2f
    val normRow = abs(row - centerRow) / (rows / 2f)
    val normCol = abs(col - centerCol) / (cols / 2f)
    // Body diamond
    if ((normRow + normCol) <= 0.7f) return true
    // Points along axes
    val onVerticalAxis = normCol <= 0.25f && normRow <= 1.05f
    val onHorizontalAxis = normRow <= 0.25f && normCol <= 1.05f
    return onVerticalAxis || onHorizontalAxis
}
