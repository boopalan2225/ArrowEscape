package com.example.arrowescape.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction
import com.example.arrowescape.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Animation state for an individual arrow.
 */
data class ArrowAnimationState(
    val arrowId: Int,
    val type: AnimationType,
    val startTime: Long = System.currentTimeMillis()
)

enum class AnimationType {
    ESCAPING,
    SHAKING
}

/**
 * The main game board component that renders arrows using Canvas.
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

    // Escape animations - each arrow moves in its direction
    val escapeAnimations = escapingArrows.mapValues { (arrowId, _) ->
        val animatable = remember(arrowId) { Animatable(0f) }
        LaunchedEffect(arrowId) {
            animatable.animateTo(
                targetValue = 1.5f, // Move 1.5x the screen dimension
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        }
        animatable.value
    }

    // Shake animations
    val shakeAnimations = shakingArrows.associateWith { arrowId ->
        val animatable = remember(arrowId) { Animatable(0f) }
        LaunchedEffect(arrowId) {
            repeat(3) {
                animatable.animateTo(10f, animationSpec = tween(50))
                animatable.animateTo(-10f, animationSpec = tween(50))
            }
            animatable.animateTo(0f, animationSpec = tween(50))
        }
        animatable.value
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val boardWidth = constraints.maxWidth.toFloat()
        val cellSize = boardWidth / gridCols
        val boardHeight = cellSize * gridRows

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { boardHeight.toDp() })
                .pointerInput(arrows) {
                    detectTapGestures { offset ->
                        // Find which arrow was tapped
                        val col = (offset.x / cellSize).toInt()
                        val row = (offset.y / cellSize).toInt()

                        arrows
                            .filter { it.isActive && it.row == row && it.col == col }
                            .firstOrNull()
                            ?.let { onArrowTap(it) }
                    }
                }
        ) {
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

                drawArrow(
                    center = Offset(centerX, centerY),
                    size = cellSize * 0.7f,
                    direction = arrow.direction,
                    color = getArrowColor(arrow.direction)
                )
            }
        }
    }
}

private fun DrawScope.drawArrow(
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
        // Draw arrow pointing UP (will be rotated for other directions)
        val arrowPath = Path().apply {
            // Arrow head (triangle)
            moveTo(center.x, center.y - size / 2)
            lineTo(center.x - size / 3, center.y - size / 6)
            lineTo(center.x - size / 6, center.y - size / 6)
            // Arrow body (rectangle)
            lineTo(center.x - size / 6, center.y + size / 3)
            lineTo(center.x + size / 6, center.y + size / 3)
            lineTo(center.x + size / 6, center.y - size / 6)
            lineTo(center.x + size / 3, center.y - size / 6)
            close()
        }

        drawPath(arrowPath, color)
    }
}

private fun getArrowColor(direction: Direction): Color {
    return when (direction) {
        Direction.UP -> ArrowUp
        Direction.DOWN -> ArrowDown
        Direction.LEFT -> ArrowLeft
        Direction.RIGHT -> ArrowRight
    }
}
