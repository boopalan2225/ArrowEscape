package com.arrowescape.game.level

import com.arrowescape.game.curve.ShapeDesign
import com.arrowescape.game.curve.ShapeLibrary
import java.util.concurrent.ConcurrentHashMap

/**
 * Grid masks for the illustrated shapes (owl, cat, crab, deer, panda).
 *
 * These shapes are authored once, as vector art in [ShapeLibrary], and the
 * grid engine derives its cell mask from that art instead of keeping a second
 * hand-drawn bitmap that could drift out of sync with the curved engine.
 */
internal object DesignMask {

    /** Rasterise at this many sub-units per cell so thin features survive. */
    private const val SUPERSAMPLE = 16f

    private data class Key(val shape: LevelShape, val rows: Int, val cols: Int)

    private val cache = ConcurrentHashMap<Key, BooleanArray>()

    fun designFor(shape: LevelShape): ShapeDesign? = when (shape) {
        LevelShape.OWL -> ShapeLibrary.OWL
        LevelShape.CAT -> ShapeLibrary.CAT
        LevelShape.CRAB -> ShapeLibrary.CRAB
        LevelShape.DEER -> ShapeLibrary.DEER
        LevelShape.PANDA -> ShapeLibrary.PANDA
        else -> null
    }

    fun contains(shape: LevelShape, row: Int, col: Int, rows: Int, cols: Int): Boolean {
        val mask = cache.getOrPut(Key(shape, rows, cols)) { rasterise(shape, rows, cols) }
        return mask[row * cols + col]
    }

    private fun rasterise(shape: LevelShape, rows: Int, cols: Int): BooleanArray {
        val design = designFor(shape) ?: return BooleanArray(rows * cols)

        // Fit the artwork's aspect ratio inside the grid, centred.
        var artWidth = cols.toFloat()
        var artHeight = rows.toFloat()
        design.aspect?.let { aspect ->
            artHeight = artWidth / aspect
            if (artHeight > rows) {
                artHeight = rows.toFloat()
                artWidth = artHeight * aspect
            }
        }
        val offsetX = (cols - artWidth) / 2f
        val offsetY = (rows - artHeight) / 2f

        val outlines = design.ordered.map {
            it.build(artWidth * SUPERSAMPLE, artHeight * SUPERSAMPLE)
        }

        val mask = BooleanArray(rows * cols)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = (col + 0.5f - offsetX) * SUPERSAMPLE
                val y = (row + 0.5f - offsetY) * SUPERSAMPLE
                val point = com.arrowescape.game.curve.Point2D(x, y)
                mask[row * cols + col] = outlines.any { it.contains(point) }
            }
        }
        return mask
    }
}
