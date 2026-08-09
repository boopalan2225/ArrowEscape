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
    CRESCENT("Crescent"),
    // Hard category shapes
    SPIRAL("Spiral"),
    LIGHTNING("Lightning"),
    CROWN("Crown"),
    RING("Ring"),
    BUTTERFLY("Butterfly");

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
        // Hard category
        SPIRAL -> isInSpiral(row, col, rows, cols)
        LIGHTNING -> isInLightning(row, col, rows, cols)
        CROWN -> isInCrown(row, col, rows, cols)
        RING -> isInRing(row, col, rows, cols)
        BUTTERFLY -> isInButterfly(row, col, rows, cols)
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
                level <= 90 -> listOf(
                    HEART, STAR, HOURGLASS, ARROW_UP, LETTER_X, CRESCENT, SPIRAL, LIGHTNING
                )
                else -> entries.toList() // everything, including hard shapes
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

// --- Hard category shapes ---

/**
 * Spiral: winding path from outside to center. Disorienting and fun.
 */
private fun isInSpiral(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val dx = col - cx; val dy = row - cy
    val dist = sqrt(dx * dx + dy * dy)
    val angle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
    // Spiral arm: distance should be proportional to angle (with wrapping)
    val normalizedAngle = (angle + kotlin.math.PI) / (2 * kotlin.math.PI) // 0 to 1
    val spiralRadius = (normalizedAngle * 0.6f + 0.2f) * (cols / 2f)
    val armThickness = cols / 5f
    // Multiple spiral arms by modulating
    val armDist = abs(dist - spiralRadius) % (cols / 3f)
    return armDist <= armThickness && dist <= cols / 2f
}

/**
 * Lightning bolt: zigzag flash pattern. Narrow and tricky.
 */
private fun isInLightning(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f
    val progress = row.toFloat() / (rows - 1) // 0 at top, 1 at bottom
    val thickness = cols / 5f
    // Zigzag center line shifts left/right based on row sections
    val segment = (row * 5) / rows // 0-4 segments
    val zigzagOffset = when (segment) {
        0 -> 0f
        1 -> cols * 0.25f
        2 -> -cols * 0.15f
        3 -> cols * 0.20f
        else -> 0f
    }
    val centerX = cx + zigzagOffset
    return abs(col - centerX) <= thickness
}

/**
 * Crown: royal shape with 3-5 points at top, solid base.
 */
private fun isInCrown(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f
    // Bottom half is solid rectangle (crown base)
    if (row >= rows * 0.6f) {
        return abs(col - cx) <= cols * 0.4f
    }
    // Top half has peaks
    val progress = row.toFloat() / (rows * 0.6f) // 0 at top, 1 at base
    val numPeaks = 3
    val peakWidth = cols.toFloat() / (numPeaks * 2)
    // Check if we're in any peak
    for (i in 0 until numPeaks) {
        val peakCenter = cols * (0.2f + 0.3f * i)
        val peakHeight = 1.0f - abs(col - peakCenter) / peakWidth
        if (peakHeight > 0f && progress >= (1.0f - peakHeight)) {
            return true
        }
    }
    return false
}

/**
 * Ring/Donut: hollow circle - center is cut out.
 */
private fun isInRing(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val dx = (col - cx) / (cols / 2f); val dy = (row - cy) / (rows / 2f)
    val distSq = dx * dx + dy * dy
    // Inside outer circle but outside inner circle (hole)
    return distSq <= 1.02f && distSq >= 0.25f
}

/**
 * Butterfly: two symmetrical wings with narrow body in center.
 */
private fun isInButterfly(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
    val nx = abs(col - cx) / (cols / 2f); val ny = abs(row - cy) / (rows / 2f)
    
    // Narrow body in center (vertical strip)
    if (nx <= 0.15f) return true
    
    // Wings: ellipses on each side, vertically stacked
    // Upper wings
    val wingCenterY = 0.4f
    val wingCenterX = 0.55f
    val wingRadX = 0.45f
    val wingRadY = 0.5f
    val dxWing = (nx - wingCenterX) / wingRadX
    val dyWingUpper = ((row.toFloat() / rows) - 0.3f) / wingRadY
    val dyWingLower = ((row.toFloat() / rows) - 0.7f) / wingRadY
    
    // Check upper wing
    if (dxWing * dxWing + dyWingUpper * dyWingUpper <= 1.0f && nx > 0.15f) return true
    // Check lower wing
    if (dxWing * dxWing + dyWingLower * dyWingLower <= 1.0f && nx > 0.15f) return true
    
    return false
}
