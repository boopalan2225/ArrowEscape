package com.clevercorner.arrowescape.game.level

import com.clevercorner.arrowescape.game.curve.PathCommand
import com.clevercorner.arrowescape.game.curve.Point2D
import com.clevercorner.arrowescape.game.curve.ShapePath
import java.util.concurrent.ConcurrentHashMap

/**
 * Grid masks for [LevelDesigns]' 59 ASCII templates, rasterised from a traced
 * outline instead of resampled pixel-for-pixel.
 *
 * The old path - [ShapeTemplate.scaleTemplate] - mapped each target cell back
 * to one source cell with integer division. A heart whose template is 11 rows
 * tall, played on a 22-row grid, got every source row duplicated exactly
 * twice: flat staircases, lost tips, no curve anywhere. Upscaling a bitmap by
 * copying pixels can only ever repeat information, never add it.
 *
 * This instead traces the bitmap's true boundary once with [BitmapOutline]
 * and samples *that* - continuous geometry - at the target resolution, the
 * same way [DesignMask] already does for the illustrated animals. The
 * silhouette is still only as detailed as the original art (a traced square
 * stays a square), but a diagonal edge now lands at the correct point on
 * every row instead of jumping in duplicated blocks.
 */
internal object TemplateMask {

    private data class Key(val template: List<String>, val rows: Int, val cols: Int)

    private val maskCache = ConcurrentHashMap<Key, Set<Pair<Int, Int>>>()
    private val outlineCache = ConcurrentHashMap<List<String>, ShapePath?>()

    /** Drop-in replacement for [ShapeTemplate.scaleTemplate] - same signature, vector-backed. */
    fun scaleTemplate(template: List<String>, targetRows: Int, targetCols: Int): Set<Pair<Int, Int>> =
        maskCache.getOrPut(Key(template, targetRows, targetCols)) {
            rasterise(template, targetRows, targetCols)
        }

    private fun outlineOf(template: List<String>): ShapePath? =
        outlineCache.getOrPut(template) {
            val loops = BitmapOutline.trace(template)
            if (loops.isEmpty()) return@getOrPut null

            val commands = buildList {
                for (loop in loops) {
                    add(PathCommand.MoveTo(loop.first()))
                    for (point in loop.drop(1)) add(PathCommand.LineTo(point))
                    add(PathCommand.Close)
                }
            }
            ShapePath(
                name = "template",
                commands = commands,
                width = template.maxOf { it.length }.toFloat(),
                height = template.size.toFloat()
            )
        }

    private fun rasterise(template: List<String>, rows: Int, cols: Int): Set<Pair<Int, Int>> {
        val path = outlineOf(template) ?: return emptySet()
        val srcCols = template.maxOf { it.length }.toFloat()
        val srcRows = template.size.toFloat()

        // Fit the bitmap's aspect ratio inside the target grid, centred -
        // same policy DesignMask uses, so a template and an illustrated
        // shape never look differently proportioned for the same reason.
        var artWidth = cols.toFloat()
        var artHeight = artWidth * (srcRows / srcCols)
        if (artHeight > rows) {
            artHeight = rows.toFloat()
            artWidth = artHeight * (srcCols / srcRows)
        }
        val offsetX = (cols - artWidth) / 2f
        val offsetY = (rows - artHeight) / 2f

        val result = mutableSetOf<Pair<Int, Int>>()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                // Map this grid cell's centre back into the bitmap's own
                // coordinate space, where the traced outline lives.
                val bitmapX = (col + 0.5f - offsetX) / artWidth * srcCols
                val bitmapY = (row + 0.5f - offsetY) / artHeight * srcRows
                if (path.contains(Point2D(bitmapX, bitmapY))) result.add(row to col)
            }
        }
        return result
    }
}
