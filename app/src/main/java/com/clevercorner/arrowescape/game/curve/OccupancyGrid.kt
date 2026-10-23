package com.clevercorner.arrowescape.game.curve

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * A coarse boolean raster used while packing arrows into a shape.
 *
 * Bounding boxes were the old approach, and they were terrible: a long diagonal
 * arrow reserved a huge rectangle of empty space, so shapes ended up sparse and
 * unrecognisable. Rasterising the actual path lets neighbouring arrows nestle
 * right up against each other the way the reference art does.
 *
 * The grid is anchored at [originX]/[originY] because shapes are translated
 * onto the board - assuming an origin of (0,0) silently clamped every
 * coordinate and crushed the whole layout into one corner.
 */
class OccupancyGrid(
    private val originX: Float,
    private val originY: Float,
    width: Float,
    height: Float,
    private val cellSize: Float
) {
    private val cols = max(1, ceil(width / cellSize).toInt())
    private val rows = max(1, ceil(height / cellSize).toInt())
    private val cells = BooleanArray(cols * rows)

    private fun index(col: Int, row: Int) = row * cols + col

    private fun colOf(x: Float) = ((x - originX) / cellSize).toInt().coerceIn(0, cols - 1)

    private fun rowOf(y: Float) = ((y - originY) / cellSize).toInt().coerceIn(0, rows - 1)

    /** True when any cell within [radius] of [point] is already taken. */
    fun isOccupied(point: Point2D, radius: Float): Boolean {
        var hit = false
        forEachCellNear(point, radius) { i -> if (cells[i]) hit = true }
        return hit
    }

    /** Mark every cell within [radius] of [point] as taken. */
    fun occupy(point: Point2D, radius: Float) = forEachCellNear(point, radius) { i ->
        cells[i] = true
    }

    /** Mark a whole polyline, inflated by [radius]. */
    fun occupyPath(points: List<Point2D>, radius: Float) = points.forEach { occupy(it, radius) }

    /** True when any sample of [points] collides with existing occupancy. */
    fun pathCollides(points: List<Point2D>, radius: Float): Boolean =
        points.any { isOccupied(it, radius) }

    private inline fun forEachCellNear(point: Point2D, radius: Float, action: (Int) -> Unit) {
        val minCol = colOf(point.x - radius)
        val maxCol = colOf(point.x + radius)
        val minRow = rowOf(point.y - radius)
        val maxRow = rowOf(point.y + radius)
        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                action(index(col, row))
            }
        }
    }
}

/**
 * The shared canvas every region of a level is packed onto.
 *
 * One grid for the whole board (rather than one per region) is what lets the
 * generator guarantee solvability: an arrow placed later can see - and avoid
 * deadlocking with - everything placed before it, including arrows in other
 * regions.
 */
class PackingCanvas(val width: Float, val height: Float, cellSize: Float = 2.5f) {
    val grid = OccupancyGrid(0f, 0f, width, height, cellSize)

    /** Who-must-leave-first graph; an arrow is only accepted if it stays acyclic. */
    val dependencies = DependencyTracker(width, height)
}

/**
 * Uniform spatial hash over arrow polylines so escape-ray marching stays O(1)
 * per step instead of scanning every arrow on the board.
 */
class ArrowSpatialIndex(cellSize: Float, arrows: Collection<CurvedArrow>) {
    private val cellSize = max(1f, cellSize)
    private val buckets = HashMap<Long, MutableList<Int>>()
    private val byId = arrows.associateBy { it.id }

    init {
        for (arrow in arrows) {
            for (p in arrow.polyline) {
                buckets.getOrPut(key(p.x, p.y)) { mutableListOf() }.let {
                    if (it.lastOrNull() != arrow.id) it.add(arrow.id)
                }
            }
        }
    }

    private fun key(x: Float, y: Float): Long {
        val cx = Math.floorDiv(x.toInt(), cellSize.toInt())
        val cy = Math.floorDiv(y.toInt(), cellSize.toInt())
        return (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)
    }

    /** Candidate arrow ids whose geometry lives near [point]. */
    fun candidatesNear(point: Point2D): Set<Int> {
        val result = mutableSetOf<Int>()
        for (dy in -1..1) {
            for (dx in -1..1) {
                buckets[key(point.x + dx * cellSize, point.y + dy * cellSize)]
                    ?.let { result.addAll(it) }
            }
        }
        return result
    }

    fun arrowById(id: Int): CurvedArrow? = byId[id]

    companion object {
        fun forArrows(arrows: Collection<CurvedArrow>): ArrowSpatialIndex {
            val cell = arrows.maxOfOrNull { it.strokeWidth }?.times(3f) ?: 24f
            return ArrowSpatialIndex(min(64f, max(16f, cell)), arrows)
        }
    }
}
