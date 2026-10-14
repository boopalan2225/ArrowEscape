package com.example.arrowescape.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.curve.ArrowHeadGeometry
import com.example.arrowescape.game.curve.ArrowMotion
import com.example.arrowescape.game.curve.ArrowPicker
import com.example.arrowescape.game.curve.CurvedArrow
import com.example.arrowescape.game.curve.Point2D
import com.example.arrowescape.ui.theme.ArrowEscapeTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAP_RADIUS = 22f
private const val LONG_PRESS_MS = 300L
private const val HINT_VISIBLE_MS = 3500L

/** Only dense boards are worth explaining pinch-to-zoom for. */
private const val DENSE_ARROW_COUNT = 30

/** Long-press preview of one arrow's exit line, from its head tip to the board edge. */
private data class ExitPreview(
    val arrow: CurvedArrow,
    val isClear: Boolean,
    val from: Point2D,
    val exit: Point2D
) {
    companion object {
        fun of(arrow: CurvedArrow, isClear: Boolean, exit: Point2D): ExitPreview {
            // Start at the visible tip so the glow never smears over the head itself.
            val heading = ArrowHeadGeometry.headingOf(arrow.polyline, arrow.strokeWidth, arrow.direction)
            val tip = ArrowHeadGeometry
                .triangle(arrow.endPoint, heading, arrow.strokeWidth, arrow.length)
                .tip
            return ExitPreview(arrow, isClear, tip, exit)
        }
    }
}

/** Gesture bookkeeping that must survive between callbacks without recomposing. */
private class PressTracker {
    var previewShown = false
}

/**
 * The game board: curved arrows you can tap, long-press to preview, and pan or
 * pinch-zoom around (like the Flutter game's InteractiveViewer).
 *
 * [resetKey] re-centres the view whenever it changes - pass the level number so
 * every level opens fully in frame.
 */
@Composable
fun CurvedGameBoard(
    arrows: List<CurvedArrow>,
    boardWidth: Float,
    boardHeight: Float,
    escapingArrows: Map<Int, Float>, // Arrow ID to escape progress (0..1)
    bumpingArrows: Map<Int, ArrowMotion.Bump>,
    hintArrowId: Int?,
    resetKey: Any,
    isArrowFree: (CurvedArrow) -> Boolean,
    exitPointOf: (CurvedArrow) -> Point2D,
    onArrowTap: (CurvedArrow) -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    // Animated values are passed as State and only read while drawing, so a
    // pulsing hint repaints the canvas instead of recomposing the whole board.
    val pulse = rememberInfiniteTransition(label = "boardPulse")
    val hintGlow = pulse.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hintGlow"
    )
    val dashPhase = pulse.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "previewDash"
    )

    var viewport by remember { mutableStateOf(BoardViewport.RESET) }
    var preview by remember { mutableStateOf<ExitPreview?>(null) }
    val tracker = remember { PressTracker() }
    val atRest by remember { derivedStateOf { viewport.isIdentity } }

    var hintVisible by remember { mutableStateOf(arrows.size >= DENSE_ARROW_COUNT) }
    LaunchedEffect(resetKey) {
        viewport = BoardViewport.RESET
        preview = null
        hintVisible = arrows.size >= DENSE_ARROW_COUNT
        delay(HINT_VISIBLE_MS)
        hintVisible = false
    }

    val latestArrows by rememberUpdatedState(arrows)
    val latestOnArrowTap by rememberUpdatedState(onArrowTap)
    val latestIsFree by rememberUpdatedState(isArrowFree)
    val latestExitPoint by rememberUpdatedState(exitPointOf)

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
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        val scaleX = canvasWidth / boardWidth
        val scaleY = canvasHeight / boardHeight
        val viewSize = Point2D(canvasWidth, canvasHeight)

        // Latest layout, readable from long-lived gesture coroutines.
        val latestLayout by rememberUpdatedState(Triple(scaleX, scaleY, viewSize))

        /** Which arrow is under this screen-space touch, accounting for pan and zoom. */
        fun arrowAt(touch: Offset): CurvedArrow? {
            val (sx, sy, _) = latestLayout
            val canvas = viewport.toCanvas(Point2D(touch.x, touch.y))
            val board = Point2D(canvas.x / sx, canvas.y / sy)
            return ArrowPicker.pick(latestArrows, board, BoardViewport.tapRadius(TAP_RADIUS, viewport.zoom))
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription =
                        "Arrow board. Tap an arrow to fire it, hold to preview its path, " +
                            "pinch to zoom and drag to move around."
                }
                // Slide around / pinch-zoom. Consumes events only once a drag
                // passes touch slop, so plain taps fall through to the next modifier.
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewport = viewport.transformedBy(
                            centroid = Point2D(centroid.x, centroid.y),
                            panDelta = Point2D(pan.x, pan.y),
                            zoomFactor = zoom,
                            viewSize = latestLayout.third
                        )
                        // Moving the board invalidates a half-shown preview.
                        preview = null
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { touch ->
                            tracker.previewShown = false
                            val target = arrowAt(touch)
                            if (target != null) {
                                coroutineScope {
                                    val pending = launch {
                                        delay(LONG_PRESS_MS)
                                        preview = ExitPreview.of(
                                            arrow = target,
                                            isClear = latestIsFree(target),
                                            exit = latestExitPoint(target)
                                        )
                                        tracker.previewShown = true
                                    }
                                    tryAwaitRelease()
                                    pending.cancel()
                                }
                            }
                            preview = null
                        },
                        onTap = { touch ->
                            // A long-press only previews - releasing must not fire.
                            if (!tracker.previewShown) arrowAt(touch)?.let { latestOnArrowTap(it) }
                        }
                    )
                }
        ) {
            val vp = viewport
            withTransform({
                translate(vp.pan.x, vp.pan.y)
                scale(vp.zoom, vp.zoom, pivot = Offset.Zero)
            }) {
                // Resting arrows first so anything moving is drawn on top.
                val (resting, moving) = arrows.partition {
                    it.id !in escapingArrows && it.id !in bumpingArrows
                }
                resting.filter { it.isActive }.forEach { arrow ->
                    val hinted = arrow.id == hintArrowId
                    drawArrow(
                        arrow, scaleX, scaleY,
                        ArrowRenderState(
                            isHinted = hinted,
                            hintGlow = if (hinted) hintGlow.value else 0f,
                            haloColor = gameColors.boardBackground
                        )
                    )
                }
                moving.forEach { arrow ->
                    drawArrow(
                        arrow, scaleX, scaleY,
                        ArrowRenderState(
                            escapeProgress = escapingArrows[arrow.id] ?: 0f,
                            bump = bumpingArrows[arrow.id],
                            haloColor = gameColors.boardBackground
                        )
                    )
                }

                preview?.let { p ->
                    drawExitPreview(
                        from = p.from,
                        to = p.exit,
                        isClear = p.isClear,
                        dashPhase = dashPhase.value,
                        scaleX = scaleX,
                        scaleY = scaleY,
                        strokeWidth = p.arrow.strokeWidth
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = hintVisible && atRest,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            Text(
                text = "Pinch to zoom · drag to move",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        AnimatedVisibility(
            visible = !atRest,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            FilledTonalButton(
                onClick = { viewport = BoardViewport.RESET },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Text("Reset view", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
