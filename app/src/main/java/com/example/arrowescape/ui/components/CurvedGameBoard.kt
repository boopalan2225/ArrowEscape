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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.curve.CurvedArrow
import com.example.arrowescape.game.curve.Point2D
import com.example.arrowescape.ui.theme.ArrowEscapeTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders curved arrows with smooth bezier paths.
 * Supports escape animations along the curve path.
 */
@Composable
fun CurvedGameBoard(
    arrows: List<CurvedArrow>,
    boardWidth: Float,
    boardHeight: Float,
    escapingArrows: Map<Int, Float>, // Arrow ID to escape progress (0..1)
    shakingArrows: Set<Int>,
    hintArrowId: Int?,
    onArrowTap: (CurvedArrow) -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors
    
    // Shake animation
    val shakeAnimatables = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
    
    SideEffect {
        shakeAnimatables.keys.filter { it !in shakingArrows }.forEach { shakeAnimatables.remove(it) }
    }
    
    shakingArrows.forEach { id ->
        key(id) {
            val animatable = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                shakeAnimatables[id] = animatable
                animatable.animateTo(1f, tween(300, easing = LinearEasing))
            }
        }
    }
    
    val shakeOffsets: Map<Int, Float> = shakeAnimatables.mapValues {
        sin(it.value.value * 4 * PI.toFloat()) * 8f
    }
    
    // Hint pulse animation
    val hintPulse = rememberInfiniteTransition(label = "hint")
    val hintGlow by hintPulse.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hintGlow"
    )
    
    // Track tap handling
    val latestArrows by rememberUpdatedState(arrows)
    val latestOnArrowTap by rememberUpdatedState(onArrowTap)
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(boardWidth / boardHeight)
            .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.10f))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        gameColors.boardBackground,
                        gameColors.arrowPrimary.copy(alpha = 0.06f)
                    )
                )
            )
    ) {
        val canvasWidth = this.constraints.maxWidth.toFloat()
        val canvasHeight = this.constraints.maxHeight.toFloat()
        val scaleX = canvasWidth / boardWidth
        val scaleY = canvasHeight / boardHeight
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        // Convert tap to shape coordinates
                        val shapeX = tap.x / scaleX
                        val shapeY = tap.y / scaleY
                        val tapPoint = Point2D(shapeX, shapeY)
                        
                        // Find tapped arrow
                        val tapped = latestArrows.firstOrNull { arrow ->
                            arrow.isActive && arrow.isNear(tapPoint, 20f)
                        }
                        tapped?.let { latestOnArrowTap(it) }
                    }
                }
        ) {
            // Draw each arrow
            arrows.forEach { arrow ->
                if (!arrow.isActive && arrow.id !in escapingArrows) return@forEach
                
                val escapeProgress = escapingArrows[arrow.id] ?: 0f
                val shakeOffset = shakeOffsets[arrow.id] ?: 0f
                val isHinted = arrow.id == hintArrowId
                
                drawCurvedArrow(
                    arrow = arrow,
                    scaleX = scaleX,
                    scaleY = scaleY,
                    escapeProgress = escapeProgress,
                    shakeOffset = shakeOffset,
                    isHinted = isHinted,
                    hintGlow = hintGlow
                )
            }
        }
    }
}

/**
 * Draw a single curved arrow with bezier paths.
 */
private fun DrawScope.drawCurvedArrow(
    arrow: CurvedArrow,
    scaleX: Float,
    scaleY: Float,
    escapeProgress: Float,
    shakeOffset: Float,
    isHinted: Boolean,
    hintGlow: Float
) {
    if (arrow.segments.isEmpty()) return
    
    val color = Color(arrow.color)
    val strokeWidth = arrow.strokeWidth * ((scaleX + scaleY) / 2)
    
    // Build the path
    val path = Path()
    var isFirst = true
    
    for (segment in arrow.segments) {
        // Apply escape animation - shrink from tail
        val adjustedSegment = if (escapeProgress > 0f) {
            // Calculate how much of this segment should be visible
            val segmentStart = arrow.segments.indexOf(segment).toFloat() / arrow.segments.size
            val segmentEnd = (arrow.segments.indexOf(segment) + 1).toFloat() / arrow.segments.size
            
            if (escapeProgress >= segmentEnd) {
                null // This segment has escaped
            } else if (escapeProgress > segmentStart) {
                // Partially visible - trim from start
                val localProgress = (escapeProgress - segmentStart) / (segmentEnd - segmentStart)
                segment.splitAt(localProgress).second
            } else {
                segment
            }
        } else {
            segment
        }
        
        adjustedSegment?.let { seg ->
            val p0 = Offset(seg.p0.x * scaleX + shakeOffset, seg.p0.y * scaleY)
            val p1 = Offset(seg.p1.x * scaleX + shakeOffset, seg.p1.y * scaleY)
            val p2 = Offset(seg.p2.x * scaleX + shakeOffset, seg.p2.y * scaleY)
            val p3 = Offset(seg.p3.x * scaleX + shakeOffset, seg.p3.y * scaleY)
            
            if (isFirst) {
                path.moveTo(p0.x, p0.y)
                isFirst = false
            }
            path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        }
    }
    
    // Draw hint glow
    if (isHinted) {
        drawPath(
            path = path,
            color = color.copy(alpha = hintGlow),
            style = Stroke(
                width = strokeWidth * 2.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
    
    // Draw the arrow body
    val alpha = if (escapeProgress > 0f) 1f - escapeProgress * 0.3f else 1f
    drawPath(
        path = path,
        color = color.copy(alpha = alpha),
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    
    // Draw arrowhead at the tip
    if (escapeProgress < 0.9f) {
        val tipT = 1f - escapeProgress.coerceIn(0f, 0.8f)
        val tip = arrow.pointAt(tipT)
        val tangent = arrow.tangentAt(tipT)
        
        val tipX = tip.x * scaleX + shakeOffset
        val tipY = tip.y * scaleY
        val headLength = strokeWidth * 2.5f
        val headWidth = strokeWidth * 1.8f
        
        val angle = kotlin.math.atan2(tangent.y, tangent.x)
        
        val arrowPath = Path().apply {
            // Tip point
            moveTo(
                tipX + cos(angle) * headLength,
                tipY + sin(angle) * headLength
            )
            // Left wing
            lineTo(
                tipX + cos(angle + 2.5f) * headWidth,
                tipY + sin(angle + 2.5f) * headWidth
            )
            // Back to center-ish
            lineTo(tipX, tipY)
            // Right wing
            lineTo(
                tipX + cos(angle - 2.5f) * headWidth,
                tipY + sin(angle - 2.5f) * headWidth
            )
            close()
        }
        
        drawPath(
            path = arrowPath,
            color = color.copy(alpha = alpha)
        )
    }
}
