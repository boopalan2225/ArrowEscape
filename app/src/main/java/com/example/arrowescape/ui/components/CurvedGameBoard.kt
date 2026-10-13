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
                        val tapPoint = Point2D(tap.x / scaleX, tap.y / scaleY)

                        // Pick the NEAREST arrow, not the first match. With dense
                        // packing several arrows sit inside one fingertip, and
                        // first-match meant taps landed on whichever happened to
                        // be generated earliest.
                        val tapRadius = 22f
                        val tapped = latestArrows
                            .filter { it.isActive }
                            .mapNotNull { arrow ->
                                arrow.polyline
                                    .minOfOrNull { it.distanceTo(tapPoint) }
                                    ?.let { arrow to it }
                            }
                            .filter { it.second <= tapRadius }
                            .minByOrNull { it.second }
                            ?.first

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
 *
 * Escape is rendered as the arrow flying off along its own heading with a
 * motion-blur trail, rather than shrinking in place - a dissolving arrow read
 * as "deleted", not "escaped", which hid the whole point of the mechanic.
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

    // Ease-in flight: slow lift-off, then snap away.
    val eased = escapeProgress * escapeProgress * (3f - 2f * escapeProgress)
    val flightDistance = (size.width + size.height) * 0.75f * eased
    val heading = arrow.direction
    val travel = Offset(heading.x * flightDistance, heading.y * flightDistance)
    val alpha = (1f - escapeProgress * escapeProgress).coerceIn(0f, 1f)

    fun buildPath(extra: Offset): Path {
        val path = Path()
        arrow.segments.forEachIndexed { i, seg ->
            fun map(p: com.example.arrowescape.game.curve.Point2D) = Offset(
                p.x * scaleX + shakeOffset + extra.x,
                p.y * scaleY + extra.y
            )
            if (i == 0) map(seg.p0).let { path.moveTo(it.x, it.y) }
            val c1 = map(seg.p1)
            val c2 = map(seg.p2)
            val end = map(seg.p3)
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
        }
        return path
    }

    // Motion-blur trail while escaping.
    if (escapeProgress > 0f) {
        val trailCount = 3
        for (i in trailCount downTo 1) {
            val lag = i.toFloat() / (trailCount + 1)
            val ghost = Offset(travel.x * (1f - lag), travel.y * (1f - lag))
            drawPath(
                path = buildPath(ghost),
                color = color.copy(alpha = alpha * 0.18f * (1f - lag)),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }

    val path = buildPath(travel)

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
    val tip = arrow.endPoint
    val tangent = arrow.direction
    val tipX = tip.x * scaleX + shakeOffset + travel.x
    val tipY = tip.y * scaleY + travel.y
    val headLength = strokeWidth * 2.5f
    val headWidth = strokeWidth * 1.8f
    val angle = kotlin.math.atan2(tangent.y, tangent.x)

    val arrowPath = Path().apply {
        moveTo(tipX + cos(angle) * headLength, tipY + sin(angle) * headLength)
        lineTo(tipX + cos(angle + 2.5f) * headWidth, tipY + sin(angle + 2.5f) * headWidth)
        lineTo(tipX, tipY)
        lineTo(tipX + cos(angle - 2.5f) * headWidth, tipY + sin(angle - 2.5f) * headWidth)
        close()
    }

    drawPath(path = arrowPath, color = color.copy(alpha = alpha))
}

