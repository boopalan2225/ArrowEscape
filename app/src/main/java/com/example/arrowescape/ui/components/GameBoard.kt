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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction
import com.example.arrowescape.ui.theme.ArrowEscapeTheme
import kotlin.math.sin

/**
 * The main game board - clean, minimal, classic line-art arrows on a
 * subtle dot grid. Arrows are single-color (navy) like the reference
 * designs; the hinted arrow glows blue.
 */
@Composable
fun GameBoard(
    arrows: List<ArrowTile>,
    gridRows: Int,
    gridCols: Int,
    escapingArrows: Map<Int, Direction>,
    shakingArrows: Set<Int>,
    hintArrowId: Int?,
    onArrowTap: (ArrowTile) -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    val escapeAnimations = escapingArrows.mapValues { (arrowId, _) ->
        val animatable = remember(arrowId) { Animatable(0f) }
        LaunchedEffect(arrowId) {
            animatable.animateTo(
                targetValue = 1.6f,
                animationSpec = tween(300, easing = FastOutLinearInEasing)
            )
        }
        animatable.value
    }

    val shakeAnimations = shakingArrows.associateWith { arrowId ->
        val animatable = remember(arrowId) { Animatable(0f) }
        LaunchedEffect(arrowId) {
            animatable.animateTo(1f, animationSpec = tween(300, easing = LinearEasing))
        }
        sin(animatable.value * 4 * Math.PI.toFloat()) * 9f
    }

    val hintPulse = rememberInfiniteTransition(label = "hint")
    val hintGlow by hintPulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
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
        val boardWidth = constraints.maxWidth.toFloat()
        val boardHeight = constraints.maxHeight.toFloat()
        val cellSize = minOf(boardWidth / gridCols, boardHeight / gridRows)

        val gridWidth = cellSize * gridCols
        val gridHeight = cellSize * gridRows
        val offsetX = (boardWidth - gridWidth) / 2f
        val offsetY = (boardHeight - gridHeight) / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(arrows) {
                    detectTapGestures { tap ->
                        val col = ((tap.x - offsetX) / cellSize).toInt()
                        val row = ((tap.y - offsetY) / cellSize).toInt()
                        arrows.firstOrNull { it.isActive && it.row == row && it.col == col }
                            ?.let { onArrowTap(it) }
                    }
                }
        ) {
            drawDotGrid(gridRows, gridCols, cellSize, offsetX, offsetY, gameColors.gridLine)

            arrows.filter { it.isActive || escapingArrows.containsKey(it.id) }.forEach { arrow ->
                val baseX = offsetX + arrow.col * cellSize + cellSize / 2
                val baseY = offsetY + arrow.row * cellSize + cellSize / 2

                val escapeProgress = escapeAnimations[arrow.id] ?: 0f
                val escapeOffset = when (arrow.direction) {
                    Direction.UP -> Offset(0f, -escapeProgress * gridHeight)
                    Direction.DOWN -> Offset(0f, escapeProgress * gridHeight)
                    Direction.LEFT -> Offset(-escapeProgress * gridWidth, 0f)
                    Direction.RIGHT -> Offset(escapeProgress * gridWidth, 0f)
                }

                val shakeOffset = shakeAnimations[arrow.id] ?: 0f
                val centerX = baseX + escapeOffset.x + shakeOffset
                val centerY = baseY + escapeOffset.y

                val isHinted = arrow.id == hintArrowId
                val color = if (isHinted) gameColors.arrowHint else gameColors.arrowPrimary

                if (isHinted) {
                    drawCircle(
                        color = gameColors.arrowHint.copy(alpha = hintGlow),
                        radius = cellSize * 0.5f,
                        center = Offset(centerX, centerY)
                    )
                }

                drawClassicArrow(
                    center = Offset(centerX, centerY),
                    cellSize = cellSize,
                    direction = arrow.direction,
                    color = color
                )
            }
        }
    }
}

private fun DrawScope.drawDotGrid(
    rows: Int,
    cols: Int,
    cellSize: Float,
    offsetX: Float,
    offsetY: Float,
    dotColor: Color
) {
    val dotRadius = cellSize * 0.035f
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val cx = offsetX + c * cellSize + cellSize / 2
            val cy = offsetY + r * cellSize + cellSize / 2
            drawCircle(color = dotColor, radius = dotRadius, center = Offset(cx, cy))
        }
    }
}

/**
 * Draws a classic thin-line arrow (stem + chevron head) matching the
 * reference art. Base orientation points UP; rotated for other directions.
 */
private fun DrawScope.drawClassicArrow(
    center: Offset,
    cellSize: Float,
    direction: Direction,
    color: Color
) {
    val rotation = when (direction) {
        Direction.UP -> 0f
        Direction.RIGHT -> 90f
        Direction.DOWN -> 180f
        Direction.LEFT -> 270f
    }

    val length = cellSize * 0.66f
    val strokeWidth = cellSize * 0.11f
    val headHalfWidth = cellSize * 0.19f
    val headHeight = cellSize * 0.24f

    rotate(rotation, pivot = center) {
        val tipY = center.y - length / 2
        val tailY = center.y + length / 2
        val headBaseY = tipY + headHeight

        // Stem line
        drawLine(
            color = color,
            start = Offset(center.x, tailY),
            end = Offset(center.x, headBaseY - strokeWidth * 0.3f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Chevron arrowhead (open V) drawn as two rounded strokes
        val headPath = Path().apply {
            moveTo(center.x - headHalfWidth, headBaseY)
            lineTo(center.x, tipY)
            lineTo(center.x + headHalfWidth, headBaseY)
        }
        drawPath(
            path = headPath,
            color = color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
