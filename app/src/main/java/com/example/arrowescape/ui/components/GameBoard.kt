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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

/**
 * The main game board component that renders arrows using Canvas.
 * Polished with shadows, rounded corners, and smooth animations.
 */
@Composable
fun GameBoard(
    arrows: List<ArrowTile>,
    gridRows: Int,
    gridCols: Int,
    animatingArrows: Set<Int>,
    escapingArrows: Map<Int, Direction>,
    shakingArrows: Set<Int>,
    onArrowTap: (ArrowTile) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val gameColors = ArrowEscapeTheme.gameColors

    // Escape animations - each arrow moves in its direction
    val escapeAnimations = escapingArrows.mapValues { (arrowId, _) ->
        val animatable = remember(arrowId) { Animatable(0f) }
        LaunchedEffect(arrowId) {
            animatable.animateTo(
                targetValue = 1.5f,
                animationSpec = tween(
                    durationMillis = 280,
                    easing = FastOutLinearInEasing
                )
            )
        }
        animatable.value
    }

    // Shake animations with spring effect
    val shakeAnimations = shakingArrows.associateWith { arrowId ->
        val animatable = remember(arrowId) { Animatable(0f) }
        LaunchedEffect(arrowId) {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh,
                    visibilityThreshold = 0.1f
                ),
                initialVelocity = 800f
            )
        }
        kotlin.math.sin(animatable.value * 3 * Math.PI.toFloat()) * 12f
    }

    // Scale animation for tapped arrows
    val scaleAnimations = remember { mutableStateMapOf<Int, Float>() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(gameColors.boardBackground)
    ) {
        val boardWidth = constraints.maxWidth.toFloat()
        val cellSize = boardWidth / gridCols
        val boardHeight = cellSize * gridRows

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { boardHeight.toDp() })
                .pointerInput(arrows) {
                    detectTapGestures { offset ->
                        val col = (offset.x / cellSize).toInt()
                        val row = (offset.y / cellSize).toInt()

                        arrows
                            .filter { it.isActive && it.row == row && it.col == col }
                            .firstOrNull()
                            ?.let { onArrowTap(it) }
                    }
                }
        ) {
            // Draw subtle grid lines
            drawGridLines(gridRows, gridCols, cellSize, gameColors.gridLine)

            // Draw each active arrow
            arrows.filter { it.isActive || escapingArrows.containsKey(it.id) }.forEach { arrow ->
                val baseX = arrow.col * cellSize + cellSize / 2
                val baseY = arrow.row * cellSize + cellSize / 2

                // Apply escape animation offset
                val escapeProgress = escapeAnimations[arrow.id] ?: 0f
                val escapeOffset = when (arrow.direction) {
                    Direction.UP -> Offset(0f, -escapeProgress * boardHeight)
                    Direction.DOWN -> Offset(0f, escapeProgress * boardHeight)
                    Direction.LEFT -> Offset(-escapeProgress * boardWidth, 0f)
                    Direction.RIGHT -> Offset(escapeProgress * boardWidth, 0f)
                }

                // Apply shake animation offset
                val shakeOffset = shakeAnimations[arrow.id] ?: 0f

                val centerX = baseX + escapeOffset.x + shakeOffset
                val centerY = baseY + escapeOffset.y

                // Scale effect (slightly smaller during escape)
                val scale = if (escapeProgress > 0f) {
                    1f - (escapeProgress * 0.3f)
                } else {
                    scaleAnimations[arrow.id] ?: 1f
                }

                val arrowColor = getArrowColor(arrow.direction, gameColors)
                
                drawArrowTile(
                    center = Offset(centerX, centerY),
                    size = cellSize * 0.75f * scale,
                    direction = arrow.direction,
                    color = arrowColor
                )
            }
        }
    }
}

private fun DrawScope.drawGridLines(
    rows: Int,
    cols: Int,
    cellSize: Float,
    lineColor: Color
) {
    val strokeWidth = 1.dp.toPx()

    // Horizontal lines
    for (i in 1 until rows) {
        drawLine(
            color = lineColor,
            start = Offset(0f, i * cellSize),
            end = Offset(size.width, i * cellSize),
            strokeWidth = strokeWidth
        )
    }

    // Vertical lines
    for (i in 1 until cols) {
        drawLine(
            color = lineColor,
            start = Offset(i * cellSize, 0f),
            end = Offset(i * cellSize, size.height),
            strokeWidth = strokeWidth
        )
    }
}

private fun DrawScope.drawArrowTile(
    center: Offset,
    size: Float,
    direction: Direction,
    color: Color
) {
    val rotation = when (direction) {
        Direction.UP -> 0f
        Direction.RIGHT -> 90f
        Direction.DOWN -> 180f
        Direction.LEFT -> 270f
    }

    rotate(rotation, pivot = center) {
        // Draw shadow/glow effect
        val shadowColor = color.copy(alpha = 0.3f)
        val shadowOffset = 2.dp.toPx()
        
        drawArrowShape(
            center = Offset(center.x + shadowOffset, center.y + shadowOffset),
            size = size,
            color = shadowColor
        )

        // Draw main arrow with rounded appearance
        drawArrowShape(
            center = center,
            size = size,
            color = color
        )

        // Draw highlight/shine effect
        val highlightColor = Color.White.copy(alpha = 0.2f)
        drawArrowHighlight(center, size, highlightColor)
    }
}

private fun DrawScope.drawArrowShape(
    center: Offset,
    size: Float,
    color: Color
) {
    val arrowPath = Path().apply {
        // Arrow head (triangle pointing up)
        moveTo(center.x, center.y - size / 2)
        lineTo(center.x - size / 2.8f, center.y - size / 8)
        
        // Left shoulder
        lineTo(center.x - size / 5, center.y - size / 8)
        
        // Left side of stem
        lineTo(center.x - size / 5, center.y + size / 2.5f)
        
        // Bottom of stem (rounded)
        quadraticBezierTo(
            center.x - size / 5, center.y + size / 2.2f,
            center.x, center.y + size / 2.2f
        )
        quadraticBezierTo(
            center.x + size / 5, center.y + size / 2.2f,
            center.x + size / 5, center.y + size / 2.5f
        )
        
        // Right side of stem
        lineTo(center.x + size / 5, center.y - size / 8)
        
        // Right shoulder
        lineTo(center.x + size / 2.8f, center.y - size / 8)
        
        close()
    }

    drawPath(arrowPath, color, style = Fill)
}

private fun DrawScope.drawArrowHighlight(
    center: Offset,
    size: Float,
    color: Color
) {
    // Small highlight on the arrow head
    val highlightPath = Path().apply {
        moveTo(center.x - size / 6, center.y - size / 4)
        lineTo(center.x, center.y - size / 2.5f)
        lineTo(center.x + size / 12, center.y - size / 4)
        close()
    }
    drawPath(highlightPath, color, style = Fill)
}

private fun getArrowColor(direction: Direction, gameColors: com.example.arrowescape.ui.theme.GameColors): Color {
    return when (direction) {
        Direction.UP -> gameColors.arrowUp
        Direction.DOWN -> gameColors.arrowDown
        Direction.LEFT -> gameColors.arrowLeft
        Direction.RIGHT -> gameColors.arrowRight
    }
}
