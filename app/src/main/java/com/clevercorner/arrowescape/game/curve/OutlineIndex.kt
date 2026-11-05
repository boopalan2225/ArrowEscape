package com.clevercorner.arrowescape.game.curve

/**
 * A uniform grid over a shape's flattened outline, for "which outline point
 * is nearest?" without reading the whole outline.
 *
 * That question is the hottest in the whole game. The flow field asks it for
 * every sample of every streamline it traces, and the authored SVG shapes
 * have two thousand outline points each, so a linear scan - with a square
 * root per point - was measured at over half of all curved generation time.
 *
 * **Exact, not approximate.** It returns the same point the scan returned,
 * including which one wins a tie, because level content is seeded and a
 * different answer here would silently re-deal the game. Two rules buy that:
 *
 *  - cells are searched in expanding rings, and the search only stops once
 *    the best point found is closer than anything the unsearched region
 *    could possibly hold;
 *  - ties go to the earlier point in outline order, which is what a scan
 *    with a strict `<` does.
 */
internal class OutlineIndex(private val contours: List<List<Point2D>>) {

    /** Flat point coordinates, with the contour and position each came from. */
    private val xs: FloatArray
    private val ys: FloatArray
    private val ring: IntArray
    private val indexInRing: IntArray

    private val cellSize: Float
    private val cols: Int
    private val rows: Int
    private val originX: Float
    private val originY: Float

    /** Point ids grouped by cell: `cellStart[c] until cellStart[c + 1]`. */
    private val cellStart: IntArray
    private val cellPoints: IntArray

    val isEmpty: Boolean get() = xs.isEmpty()

    init {
        val total = contours.sumOf { it.size }
        xs = FloatArray(total)
        ys = FloatArray(total)
        ring = IntArray(total)
        indexInRing = IntArray(total)

        var next = 0
        contours.forEachIndexed { ringIndex, points ->
            points.forEachIndexed { pointIndex, point ->
                xs[next] = point.x
                ys[next] = point.y
                ring[next] = ringIndex
                indexInRing[next] = pointIndex
                next++
            }
        }

        if (total == 0) {
            cellSize = 1f; cols = 1; rows = 1; originX = 0f; originY = 0f
            cellStart = IntArray(2); cellPoints = IntArray(0)
        } else {
            originX = xs.min()
            originY = ys.min()
            val spanX = xs.max() - originX
            val spanY = ys.max() - originY

            // Aim for a handful of points per cell: enough that a ring of
            // cells is worth visiting, few enough that visiting one is cheap.
            val target = kotlin.math.sqrt(total.toDouble() / POINTS_PER_CELL).toInt().coerceAtLeast(1)
            cellSize = maxOf(spanX, spanY).coerceAtLeast(1f) / target
            cols = (spanX / cellSize).toInt() + 1
            rows = (spanY / cellSize).toInt() + 1

            // Counting sort into buckets - one pass to size them, one to fill.
            val counts = IntArray(cols * rows + 1)
            for (i in 0 until total) counts[cellOf(xs[i], ys[i]) + 1]++
            for (c in 1 until counts.size) counts[c] += counts[c - 1]
            cellStart = counts

            val cursor = cellStart.copyOf()
            cellPoints = IntArray(total)
            for (i in 0 until total) {
                val cell = cellOf(xs[i], ys[i])
                cellPoints[cursor[cell]++] = i
            }
        }
    }

    /** The nearest outline point to ([x], [y]) as contour and position, or null. */
    fun nearest(x: Float, y: Float): Pair<List<Point2D>, Int>? {
        if (isEmpty) return null

        val queryCol = columnOf(x)
        val queryRow = rowOf(y)

        var bestPoint = -1
        var bestDistance = Float.MAX_VALUE

        var radius = 0
        val maxRadius = cols + rows
        while (radius <= maxRadius) {
            forEachPointInRing(queryCol, queryRow, radius) { point ->
                val dx = xs[point] - x
                val dy = ys[point] - y
                val distance = dx * dx + dy * dy
                if (distance < bestDistance || (distance == bestDistance && earlier(point, bestPoint))) {
                    bestDistance = distance
                    bestPoint = point
                }
            }

            // Everything still unsearched lies outside the box of cells we
            // have covered, so it is at least this far away. Once the best
            // point beats that, no further ring can improve on it.
            if (bestPoint >= 0) {
                val reach = distanceToOutside(x, y, queryCol, queryRow, radius)
                if (reach > 0f && bestDistance <= reach * reach) break
            }
            radius++
        }

        if (bestPoint < 0) return null
        return contours[ring[bestPoint]] to indexInRing[bestPoint]
    }

    /** Outline order, so ties resolve the way a front-to-back scan resolves them. */
    private fun earlier(candidate: Int, incumbent: Int): Boolean {
        if (incumbent < 0) return true
        if (ring[candidate] != ring[incumbent]) return ring[candidate] < ring[incumbent]
        return indexInRing[candidate] < indexInRing[incumbent]
    }

    /** Distance from ([x], [y]) to the edge of the searched box of cells. */
    private fun distanceToOutside(x: Float, y: Float, col: Int, row: Int, radius: Int): Float {
        val left = originX + (col - radius) * cellSize
        val right = originX + (col + radius + 1) * cellSize
        val top = originY + (row - radius) * cellSize
        val bottom = originY + (row + radius + 1) * cellSize
        return minOf(x - left, right - x, y - top, bottom - y)
    }

    /**
     * Visits the points in cells exactly [radius] cells away (Chebyshev).
     *
     * The box inside that ring was visited by the earlier, smaller radii, so
     * only its boundary is new: the top and bottom rows in full, and the two
     * end columns of every row between them.
     */
    private inline fun forEachPointInRing(col: Int, row: Int, radius: Int, visit: (Int) -> Unit) {
        val fromRow = (row - radius).coerceAtLeast(0)
        val toRow = (row + radius).coerceAtMost(rows - 1)

        for (r in fromRow..toRow) {
            val wholeRow = r == row - radius || r == row + radius
            if (wholeRow) {
                val fromCol = (col - radius).coerceAtLeast(0)
                val toCol = (col + radius).coerceAtMost(cols - 1)
                for (c in fromCol..toCol) visitCell(r, c, visit)
            } else {
                visitCell(r, col - radius, visit)
                visitCell(r, col + radius, visit)
            }
        }
    }

    private inline fun visitCell(row: Int, col: Int, visit: (Int) -> Unit) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return
        val cell = row * cols + col
        for (slot in cellStart[cell] until cellStart[cell + 1]) visit(cellPoints[slot])
    }

    private fun cellOf(x: Float, y: Float): Int = rowOf(y) * cols + columnOf(x)

    private fun columnOf(x: Float): Int =
        ((x - originX) / cellSize).toInt().coerceIn(0, cols - 1)

    private fun rowOf(y: Float): Int =
        ((y - originY) / cellSize).toInt().coerceIn(0, rows - 1)

    private companion object {
        /** Points per cell to aim for. Pure tuning - correctness does not depend on it. */
        const val POINTS_PER_CELL = 4
    }
}
