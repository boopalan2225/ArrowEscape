package com.clevercorner.arrowescape.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.game.curve.ArrowHeadGeometry
import com.clevercorner.arrowescape.game.curve.ArrowMotion
import com.clevercorner.arrowescape.game.curve.ArrowPicker
import com.clevercorner.arrowescape.game.curve.CurvedArrow
import com.clevercorner.arrowescape.game.curve.Point2D
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
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
                .triangle(arrow.endPoint, heading, arrow.strokeWidth)
                .tip
            return ExitPreview(arrow, isClear, tip, exit)
        }
    }
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
    modifier: Modifier = Modifier,
    /** 1 = settled. Below that the board is mid-re-arrangement. */
    shufflePhase: Float = 1f,
    /** Which mode's surface to paint. Daily looks deliberately unlike the rest. */
    skin: BoardSkin = BoardSkin.CURVED,
    /** A theme's own painted board, whose frame is the four walls. Null keeps [skin]'s flat surface. */
    plaque: BoardPlaque? = null,
    /** Recolours every arrow onto a theme's two-stop gradient. Null keeps each arrow's own baked colour. See [duotone]. */
    arrowDuotone: Pair<Color, Color>? = null
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val skinColors = skin.colors()
    val boardDescription = stringResource(R.string.board_content_description)

    /**
     * The colour an arrow should draw in - its own, unless this theme
     * recolours the whole board - and in either case lifted until it is
     * actually visible against this board. See [ContrastSafe].
     *
     * Applied after the duotone rather than before it: the duotone is what
     * decides the final colour on a themed board, so checking the arrow's
     * baked colour would be checking something that never gets drawn.
     */
    fun recolor(arrow: CurvedArrow): Color {
        val own = arrowDuotone?.let { (dark, light) -> duotone(Color(arrow.color), dark, light) }
            ?: Color(arrow.color)
        return with(ContrastSafe) { own.readableOn(gameColors.boardBackground) }
    }

    // Split once per change rather than once per frame. The canvas repaints
    // continuously while the hint pulses, and re-partitioning a few hundred
    // arrows on every one of those frames is work nobody asked for.
    val (resting, moving) = remember(arrows, escapingArrows, bumpingArrows) {
        val (still, busy) = arrows.partition {
            it.id !in escapingArrows && it.id !in bumpingArrows
        }
        still.filter { it.isActive } to busy
    }

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

    Surface(
        modifier = modifier
            // Fit inside whatever space the chrome leaves, on BOTH axes.
            // Sizing by width alone let a tall board grow past the bottom
            // of its slot, where the parent simply clipped it off.
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .aspectRatio(boardWidth / boardHeight),
        // A plaque brings its own corners; rounding the card again would
        // shave them off. Everything else keeps the house shape.
        shape = if (plaque != null) RectangleShape else MaterialTheme.shapes.extraLarge,
        // A theme's own plaque is the boundary the player sees - this
        // Surface exists only to host the Canvas, not to draw a second,
        // larger frame of its own behind it (its elevation shadow, being a
        // soft rounded rectangle taller than the square plaque, used to
        // read as a stray blurred box floating behind the card).
        color = if (plaque != null) Color.Transparent else skinColors.surface,
        shadowElevation = if (plaque != null) 0.dp else 4.dp
    ) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val canvasWidth = this.constraints.maxWidth.toFloat()
        val canvasHeight = this.constraints.maxHeight.toFloat()

        // A themed plaque's own painted frame is the board's boundary, so
        // the puzzle is laid out inside that frame rather than across the
        // whole card. The card itself matches the playfield's aspect ratio,
        // so the plaque fits it exactly - there is no second, invisible
        // border around it any more.
        val playFraction = plaque?.playArea ?: 1f
        val playWidth = canvasWidth * playFraction
        val playHeight = canvasHeight * playFraction
        val scaleX = playWidth / boardWidth
        val scaleY = playHeight / boardHeight
        val playOrigin = Offset((canvasWidth - playWidth) / 2f, (canvasHeight - playHeight) / 2f)
        val playBounds = BoardBounds(
            size = Point2D(playWidth, playHeight),
            origin = Point2D(playOrigin.x, playOrigin.y),
            // Painted walls are walls: nothing drifts past them. A plain
            // card has no walls to respect, so it keeps its thumb-room.
            slack = if (plaque != null) 0f else BoardViewport.BOUNDARY_FRACTION
        )

        // Latest layout, readable from long-lived gesture coroutines.
        val latestLayout by rememberUpdatedState(Triple(scaleX, scaleY, playBounds))
        val latestInset by rememberUpdatedState(playOrigin)

        // The card and the plaque share an aspect ratio, so a plain fit is
        // already edge to edge: no zoom, no cropped corners, no letterbox.
        plaque?.let { board ->
            Image(
                painter = painterResource(board.art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        /** Which arrow is under this screen-space touch, accounting for pan, zoom and the content inset. */
        fun arrowAt(touch: Offset): CurvedArrow? {
            val (sx, sy, _) = latestLayout
            val inset = latestInset
            val canvas = viewport.toCanvas(Point2D(touch.x, touch.y))
            val board = Point2D((canvas.x - inset.x) / sx, (canvas.y - inset.y) / sy)
            return ArrowPicker.pick(latestArrows, board, BoardViewport.tapRadius(TAP_RADIUS, viewport.zoom))
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = boardDescription
                }
                // Slide around / pinch-zoom. Consumes events only once a drag
                // passes touch slop, so plain taps fall through to the next modifier.
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewport = viewport.transformedBy(
                            centroid = Point2D(centroid.x, centroid.y),
                            panDelta = Point2D(pan.x, pan.y),
                            zoomFactor = zoom,
                            bounds = latestLayout.third
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
            // A plaque paints its own surface; a second wash of skin colour
            // over it would only dull the art and bleed onto its frame.
            if (plaque == null) drawBoardBackdrop(skin, skinColors)

            val vp = viewport
            withTransform({
                // The wall first, in fixed canvas space: declared BEFORE pan
                // and zoom, so it stays pinned to the plaque's painted frame
                // instead of riding along with the content it is meant to
                // fence in. (Declared after, a zoom simply scaled the clip
                // up with the arrows - which is no clip at all.)
                if (plaque != null) clipRect(
                    left = playOrigin.x,
                    top = playOrigin.y,
                    right = playOrigin.x + playWidth,
                    bottom = playOrigin.y + playHeight
                )
                // Pan and zoom act on the whole canvas, and the play area is
                // placed *inside* that - which is exactly the mapping tap
                // hit-testing inverts. Insetting outside the zoom instead
                // (as this once did) made every zoomed tap land off-target.
                translate(vp.pan.x, vp.pan.y)
                scale(vp.zoom, vp.zoom, pivot = Offset.Zero)
                translate(playOrigin.x, playOrigin.y)
            }) {
                // Resting arrows first so anything moving is drawn on top.
                //
                // Which numbers are live is computed once for the frame
                // rather than per arrow: it is the same answer for all of
                // them, and asking per arrow would be a scan inside a scan
                // on every redraw.
                val lowestLiveKey = arrows
                    .filter { it.isActive }
                    .mapNotNull { it.sequenceKey }
                    .minOrNull()

                resting.forEach { arrow ->
                    val hinted = arrow.id == hintArrowId
                    val render = {
                        drawArrow(
                            arrow, scaleX, scaleY,
                            ArrowRenderState(
                                isHinted = hinted,
                                hintGlow = if (hinted) hintGlow.value else 0f,
                                haloColor = plaque?.halo ?: skinColors.halo,
                                colorOverride = recolor(arrow),
                                sequenceReady = arrow.sequenceKey == lowestLiveKey
                            )
                        )
                    }
                    if (shufflePhase >= 1f) {
                        render()
                    } else {
                        // Fold this arrow towards its own centre. The stagger
                        // turns a uniform blink into a wave crossing the
                        // board, which is what makes it read as the maze
                        // re-arranging rather than the screen glitching.
                        val centre = arrow.screenCentre(scaleX, scaleY)
                        val fold = shuffleScaleFor(
                            phase = shufflePhase,
                            ripple = rippleOf(centre, playWidth, playHeight)
                        )
                        withTransform({ scale(fold, fold, pivot = centre) }) { render() }
                    }
                }
                moving.forEach { arrow ->
                    drawArrow(
                        arrow, scaleX, scaleY,
                        ArrowRenderState(
                            escapeProgress = escapingArrows[arrow.id] ?: 0f,
                            bump = bumpingArrows[arrow.id],
                            haloColor = plaque?.halo ?: gameColors.boardBackground,
                            colorOverride = recolor(arrow)
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
                text = stringResource(R.string.board_zoom_hint),
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
                Text(stringResource(R.string.board_reset_view), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    }
}
