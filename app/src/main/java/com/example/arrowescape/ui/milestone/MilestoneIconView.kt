package com.example.arrowescape.ui.milestone

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.example.arrowescape.game.curve.CurvedLevelGenerator
import com.example.arrowescape.game.curve.ShapeDesign

/** Draws a chapter's icon with whichever engine the chapter belongs to. */
@Composable
fun MilestoneIconView(icon: MilestoneIcon, tint: Color, modifier: Modifier = Modifier) {
    when (icon) {
        is MilestoneIcon.Grid -> ShapeIcon(icon.shape, tint, modifier)
        is MilestoneIcon.Curved -> CurvedShapeIcon(icon.design, modifier)
    }
}

// Any square will do - the icon is rescaled to its canvas - this just keeps the
// layout maths in comfortable numbers.
private const val LAYOUT_BOARD = 100f

/**
 * A curved-mode shape as a small flat icon, in its own colours.
 *
 * Uses the generator's own layout, so the icon is the very silhouette the level
 * will be filled in - tree trunk brown, foliage green - never an approximation.
 */
@Composable
fun CurvedShapeIcon(design: ShapeDesign, modifier: Modifier = Modifier) {
    val parts = remember(design) {
        val outlines = CurvedLevelGenerator.layOutRegions(design, LAYOUT_BOARD, LAYOUT_BOARD)
        design.ordered.zip(outlines)
    }

    Canvas(modifier) {
        val minX = parts.minOf { it.second.bounds.first.x }
        val minY = parts.minOf { it.second.bounds.first.y }
        val maxX = parts.maxOf { it.second.bounds.second.x }
        val maxY = parts.maxOf { it.second.bounds.second.y }
        val span = maxOf(maxX - minX, maxY - minY)
        if (span <= 0f) return@Canvas

        val scale = minOf(size.width, size.height) / span
        // Centre the art in the canvas on both axes.
        val originX = (size.width - (maxX - minX) * scale) / 2f
        val originY = (size.height - (maxY - minY) * scale) / 2f

        parts.forEach { (region, outline) ->
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                outline.contours.filter { it.size > 2 }.forEach { ring ->
                    ring.forEachIndexed { i, p ->
                        val x = originX + (p.x - minX) * scale
                        val y = originY + (p.y - minY) * scale
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
            }
            drawPath(path, Color(region.color))
        }
    }
}
