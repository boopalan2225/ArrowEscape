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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction
import com.example.arrowescape.ui.theme.ArrowEscapeTheme
import com.example.arrowescape.ui.theme.GameColors
import kotlin.math.sin

/**
 * The main game board - elegant dot-grid background with polished arrows.
 * Arrows are arranged into shapes (triangle, circle, heart, etc.) by the
 * level generator; this component just renders whatever cells are populated.
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
    val density = LocalDensity.current
    val gameColors = ArrowEscapeTheme.gameColors

    // Escape animation: fly off in the arrow's direction
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

    // Shake animation for blocked taps
    val shakeAnimations = shakingArrows.associateWith { arrowId ->
        val animatable = remember(arrowId) { Animatable(0f) }
        LaunchedEffect(arrowId) {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = LinearEasing)
            )
        }
        sin(animatable.value * 4 * Math.PI.toFloat()) * 10f
    }

    // Hint pulse animation
    val hintPulse = rememberInfiniteTransition(label = "hint")
    val hintScale by hintPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintScale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(gridCols.toFloat() / gridRows.toFloat())
            .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(RoundedCornerShape(24.dp))
            .background(gameColors.boardBackground)
    ) {
        val boardWidth = constraints.maxWidth.toFloat()
        val boardHeight = constraints.maxHeight.toFloat()
        val cellSize = minOf(boardWidth / gridCols, boardHeight / gridRows)

        // Center the grid within the board
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
            // 1. Draw subtle dot grid at every cell center
            drawDotGrid(gridRows, gridCols, cellSize, offsetX, offsetY, gameColors.gridLine)

            // 2. Draw arrows
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
                val scale = when {
                    escapeProgress > 0f -> 1f - (escapeProgress * 0.25f)
                    isHinted -> hintScale
                    else -> 1f
                }

                val color = getArrowColor(arrow.direction, gameColors)

                // Hint glow ring behind the arrow
                if (isHinted) {
                    drawCircle(
                        color = color.copy(alpha = 0.18f),
                        radius = cellSize * 0.55f,
                        center = Offset(centerX, centerY)
                    )
                }

                drawElegantArrow(
                    center = Offset(centerX, centerY),
                    cellSize = cellSize * scale,
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
    val dotRadius = cellSize * 0.04f
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val cx = offsetX + c * cellSize + cellSize / 2
            val cy = offsetY + r * cellSize + cellSize / 2
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * Draws a thick, rounded arrow in the "stroke + head" style similar to
 * modern puzzle games. Base orientation points UP; rotated for others.
 */
private fun DrawScope.drawElegantArrow(
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

    val length = cellSize * 0.62f
    val strokeWidth = cellSize * 0.16f
    val headSize = cellSize * 0.26f

    rotate(rotation, pivot = center) {
        val topY = center.y - length / 2
        val bottomY = center.y + length / 2

        // Soft shadow underneath for depth
        val shadowShift = cellSize * 0.03f
        drawArrowStrokeAndHead(
            center = center,
            topY = topY + shadowShift,
            bottomY = bottomY + shadowShift,
            strokeWidth = strokeWidth,
            headSize = headSize,
            color = color.copy(alpha = 0.22f)
        )

        // Main arrow
        drawArrowStrokeAndHead(
            center = center,
            topY = topY,
            bottomY = bottomY,
            strokeWidth = strokeWidth,
            headSize = headSize,
            color = color
        )
    }
}

private fun DrawScope.drawArrowStrokeAndHead(
    center: Offset,
    topY: Float,
    bottomY: Float,
    strokeWidth: Float,
    headSize: Float,
    color: Color
) {
    // Stem: from just below the head to the bottom
    val headBaseY = topY + headSize
    drawLine(
        color = color,
        start = Offset(center.x, headBaseY),
        end = Offset(center.x, bottomY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    // Arrow head (filled triangle) with rounded joins
    val headPath = Path().apply {
        moveTo(center.x, topY)
        lineTo(center.x - headSize, headBaseY + headSize * 0.15f)
        lineTo(center.x + headSize, headBaseY + headSize * 0.15f)
        close()
    }
    drawPath(
        path = headPath,
        color = color,
        style = Fill
    )
    // Smooth the head corners with a matching stroke
    drawPath(
        path = headPath,
        color = color,
        style = Stroke(width = strokeWidth * 0.5f, join = StrokeJoin.Round, cap = StrokeCap.Round)
    )
}

private fun getArrowColor(direction: Direction, gameColors: GameColors): Color {
    return when (direction) {
        Direction.UP -> gameColors.arrowUp
        Direction.DOWN -> gameColors.arrowDown
        Direction.LEFT -> gameColors.arrowLeft
        Direction.RIGHT -> gameColors.arrowRight
    }
}
