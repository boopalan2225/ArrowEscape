package com.example.arrowescape.game.level

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Defines the shape that arrows are arranged into on the board.
 * Each shape provides a mask determining which grid cells are valid.
 * Designed for square grids so shapes render accurately.
 */
enum class LevelShape(val displayName: String) {
    SQUARE("Square"),
    TRIANGLE("Triangle"),
    DIAMOND("Diamond"),
    CIRCLE("Circle"),
    HEART("Heart"),
    CROSS("Cross"),
    PYRAMID("Pyramid"),
    STAR("Star"),
    HEXAGON("Hexagon"),
    OVAL("Oval"),
    HOURGLASS("Hourglass"),
    ARROW_UP("Arrow"),
    LETTER_X("X Mark"),
    CRESCENT("Crescent");

    fun contains(row: Int, col: Int, rows: Int, cols: Int): Boolean = when (this) {
        SQUARE -> true
        TRIANGLE, PYRAMID -> isInTriangle(row, col, rows, cols)
        DIAMOND -> isInDiamond(row, col, rows, cols)
        CIRCLE -> isInCircle(row, col, rows, cols)
        HEART -> isInHeart(row, col, rows, cols)
        CROSS -> isInCross(row, col, rows, cols)
        STAR -> isInStar(row, col, rows, cols)
        HEXAGON -> isInHexagon(row, col, rows, cols)
        OVAL -> isInOval(row, col, rows, cols)
        HOURGLASS -> isInHourglass(row, col, rows, cols)
        ARROW_UP -> isInArrowUp(row, col, rows, cols)
        LETTER_X -> isInLetterX(row, col, rows, cols)
        CRESCENT -> isInCrescent(row, col, rows, cols)
    }

    fun validCells(rows: Int, cols: Int): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (contains(r, c, rows, cols)) cells.add(r to c)
        }
        return cells
    }

    companion object {
        /**
         * Picks a shape for the given level. Levels 1-3 are SQUARE (intro),
         * then variety grows the further you get.
         */
        fun forLevel(level: Int): LevelShape {
            if (level <= 3) return SQUARE

            val pool = when {
                level <= 12 -> listOf(SQUARE, TRIANGLE, DIAMOND, PYRAMID)
                level <= 25 -> listOf(TRIANGLE, DIAMOND, CIRCLE, PYRAMID, CROSS, HEXAGON)
                level <= 45 -> listOf(
                    DIAMOND, CIRCLE, HEART, CROSS, STAR, HEXAGON, OVAL, ARROW_UP
                )
                level <= 70 -> listOf(
                    CIRCLE, HEART, CROSS, STAR, HEXAGON, OVAL, HOURGLASS, ARROW_UP, LETTER_X
                )
                else -> entries.toList() // everything, including CRESCENT
            }
            return pool[level % pool.size]
        }
    }
}

// --- Crisp shape masks (tuned for square grids) ---

private fun isInTriangle(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val center = (cols - 1) / 2f
    val progress = (row + 1).toFloat() / rows
    return abs(col - center) <= progress * (cols / 2f)
}

private fun isInDiamond(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    return (abs(col - cx) / (cols / 2f) + abs(row - cy) / (rows / 2f)) <= 1.0f
}

private fun isInCircle(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val dx = (col - cx) / (cols / 2f); val dy = (row - cy) / (rows / 2f)
    return dx * dx + dy * dy <= 1.02f
}

private fun isInOval(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val dx = (col - cx) / (cols / 2f * 0.72f); val dy = (row - cy) / (rows / 2f)
    return dx * dx + dy * dy <= 1.02f
}

private fun isInHeart(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val x = ((col - (cols - 1) / 2f) / ((cols - 1) / 2f)) * 1.25f
    val y = (((rows - 1) / 2f - row) / ((rows - 1) / 2f)) * 1.30f + 0.20f
    return (x * x + y * y - 1f).pow(3) - x * x * y * y * y <= 0f
}

private fun isInCross(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val inV = col >= cols / 3f - 0.001f && col < cols * 2 / 3f
    val inH = row >= rows / 3f - 0.001f && row < rows * 2 / 3f
    return inV || inH
}

private fun isInStar(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val dx = abs(col - cx) / (cols / 2f); val dy = abs(row - cy) / (rows / 2f)
    return (sqrt(dx) + sqrt(dy)) <= 1.05f
}

private fun isInHexagon(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val nx = abs(col - cx) / (cols / 2f); val ny = abs(row - cy) / (rows / 2f)
    // Wide middle band, pointed top/bottom.
    return ny <= 1.0f && nx <= 1.0f && (nx + max(0f, ny - 0.5f) * 2f) <= 1.05f
}

private fun isInHourglass(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val halfW = abs(row - cy) / (rows / 2f) * (cols / 2f)
    return abs(col - cx) <= halfW + 0.5f
}

private fun isInArrowUp(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val center = (cols - 1) / 2f
    return if (row < rows / 2) {
        // Triangle head widening from the apex.
        val progress = (row + 1f) / (rows / 2f)
        abs(col - center) <= progress * (cols / 2f)
    } else {
        // Central stem (~1/3 width).
        abs(col - center) <= cols / 6f + 0.5f
    }
}

private fun isInLetterX(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    // Square grid assumed. Two diagonals, thickness ~2 cells.
    val scale = (rows - 1).toFloat() / (cols - 1)
    val d1 = abs(row - col * scale)
    val d2 = abs(row - ((rows - 1) - col * scale))
    return d1 <= 1.0f || d2 <= 1.0f
}

private fun isInCrescent(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val nx = (col - cx) / (cols / 2f); val ny = (row - cy) / (rows / 2f)
    val inBig = nx * nx + ny * ny <= 1.02f
    // Subtract a circle offset to the right to carve the crescent.
    val ox = (col - (cx + cols * 0.30f)) / (cols / 2f); val oy = ny
    val inSmall = ox * ox + oy * oy <= 0.85f
    return inBig && !inSmall
}
