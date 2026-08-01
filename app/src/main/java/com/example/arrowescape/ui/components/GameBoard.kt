package com.example.arrowescape.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.model.Arrow
import com.example.arrowescape.game.model.Cell
import com.example.arrowescape.game.model.Direction
import com.example.arrowescape.ui.theme.ArrowEscapeTheme
import kotlin.math.sin

/**
 * Renders the board of bendable snake-arrows on a subtle dot grid.
 * Each arrow is a rounded poly-line with a chevron head, single navy color
 * (blue when hinted) - matching the reference art.
 */
@Composable
fun GameBoard(
    arrows: List<Arrow>,
    gridRows: Int,
    gridCols: Int,
    escapingArrows: Map<Int, Direction>,
    shakingArrows: Set<Int>,
    hintArrowId: Int?,
    onArrowTap: (Arrow) -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    val escapeAnim = escapingArrows.mapValues { (id, _) ->
        val a = remember(id) { Animatable(0f) }
        LaunchedEffect(id) {
            a.animateTo(1.7f, tween(320, easing = FastOutLinearInEasing))
        }
        a.value
    }

    val shakeAnim = shakingArrows.associateWith { id ->
        val a = remember(id) { Animatable(0f) }
        LaunchedEffect(id) { a.animateTo(1f, tween(300, easing = LinearEasing)) }
        sin(a.value * 4 * Math.PI.toFloat()) * 9f
    }

    val hintPulse = rememberInfiniteTransition(label = "hint")
    val hintGlow by hintPulse.animateFloat(
        0.25f, 0.6f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hintGlow"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(gridCols.toFloat() / gridRows.toFloat())
            .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(RoundedCornerShape(24.dp))
            .background(gameColors.boardBackground)
    ) {
        val boardW = constraints.maxWidth.toFloat()
        val boardH = constraints.maxHeight.toFloat()
        val cell = minOf(boardW / gridCols, boardH / gridRows)
        val gridW = cell * gridCols
        val gridH = cell * gridRows
        val offX = (boardW - gridW) / 2f
        val offY = (boardH - gridH) / 2f

        fun centerOf(c: Cell) = Offset(
            offX + c.col * cell + cell / 2,
            offY + c.row * cell + cell / 2
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(arrows) {
                    detectTapGestures { tap ->
                        val col = ((tap.x - offX) / cell).toInt()
                        val row = ((tap.y - offY) / cell).toInt()
                        arrows.firstOrNull { it.isActive && it.occupies(row, col) }
                            ?.let { onArrowTap(it) }
                    }
                }
        ) {
            drawDotGrid(gridRows, gridCols, cell, offX, offY, gameColors.gridLine)

            arrows.filter { it.isActive || escapingArrows.containsKey(it.id) }.forEach { arrow ->
                val progress = escapeAnim[arrow.id] ?: 0f
                val slide = when (arrow.direction) {
                    Direction.UP -> Offset(0f, -progress * gridH)
                    Direction.DOWN -> Offset(0f, progress * gridH)
                    Direction.LEFT -> Offset(-progress * gridW, 0f)
                    Direction.RIGHT -> Offset(progress * gridW, 0f)
                }
                val shake = shakeAnim[arrow.id] ?: 0f
                val delta = Offset(slide.x + shake, slide.y)

                val isHinted = arrow.id == hintArrowId
                val color = if (isHinted) gameColors.arrowHint else gameColors.arrowPrimary

                val points = arrow.cells.map { centerOf(it) + delta }

                if (isHinted) {
                    points.forEach {
                        drawCircle(gameColors.arrowHint.copy(alpha = hintGlow), cell * 0.5f, it)
                    }
                }

                drawSnakeArrow(points, arrow.direction, cell, color)
            }
        }
    }
}

private fun DrawScope.drawDotGrid(
    rows: Int, cols: Int, cell: Float, offX: Float, offY: Float, dotColor: Color
) {
    val radius = cell * 0.035f
    for (r in 0 until rows) for (c in 0 until cols) {
        drawCircle(dotColor, radius, Offset(offX + c * cell + cell / 2, offY + r * cell + cell / 2))
    }
}

/**
 * Draws a bendable arrow: rounded poly-line stem + chevron head.
 * [points] are ordered tail -> head.
 */
private fun DrawScope.drawSnakeArrow(
    points: List<Offset>,
    direction: Direction,
    cell: Float,
    color: Color
) {
    val stroke = cell * 0.13f
    val head = points.last()

    val dirVec = when (direction) {
        Direction.UP -> Offset(0f, -1f)
        Direction.DOWN -> Offset(0f, 1f)
        Direction.LEFT -> Offset(-1f, 0f)
        Direction.RIGHT -> Offset(1f, 0f)
    }
    val perp = Offset(-dirVec.y, dirVec.x)

    // Stem: poly-line through all cell centers, ending a touch before the tip
    // so the chevron sits cleanly at the head.
    val stemEnd = head + dirVec * (cell * 0.10f)
    val stemPath = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        lineTo(stemEnd.x, stemEnd.y)
    }
    drawPath(
        stemPath, color,
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Chevron head
    val tip = head + dirVec * (cell * 0.34f)
    val back = tip - dirVec * (cell * 0.30f)
    val halfW = cell * 0.22f
    val chevron = Path().apply {
        val left = back + perp * halfW
        val right = back - perp * halfW
        moveTo(left.x, left.y)
        lineTo(tip.x, tip.y)
        lineTo(right.x, right.y)
    }
    drawPath(
        chevron, color,
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
