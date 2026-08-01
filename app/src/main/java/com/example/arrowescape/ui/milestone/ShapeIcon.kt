package com.example.arrowescape.ui.milestone

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.example.arrowescape.game.level.LevelShape

/**
 * Renders a LevelShape as a small filled icon using its cell mask.
 * Gives each milestone a recognizable silhouette (heart, star, circle...).
 */
@Composable
fun ShapeIcon(
    shape: LevelShape,
    color: Color,
    modifier: Modifier = Modifier,
    resolution: Int = 11
) {
    Canvas(modifier = modifier) {
        val cells = shape.validCells(resolution, resolution)
        if (cells.isEmpty()) return@Canvas

        val cell = minOf(size.width, size.height) / resolution
        val gridSize = cell * resolution
        val offX = (size.width - gridSize) / 2f
        val offY = (size.height - gridSize) / 2f
        val dot = cell * 0.42f

        cells.forEach { (r, c) ->
            val cx = offX + c * cell + cell / 2
            val cy = offY + r * cell + cell / 2
            drawCircle(color = color, radius = dot, center = Offset(cx, cy))
        }
    }
}

/**
 * Draws a filled 5-point star (used for milestone rewards / ratings).
 */
fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val points = 5
    val innerRadius = radius * 0.42f
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) radius else innerRadius
        val angle = Math.PI / points * i - Math.PI / 2
        val x = center.x + (r * kotlin.math.cos(angle)).toFloat()
        val y = center.y + (r * kotlin.math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Fill)
}
