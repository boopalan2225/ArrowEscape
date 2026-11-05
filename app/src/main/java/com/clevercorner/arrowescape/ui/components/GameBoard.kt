package com.clevercorner.arrowescape.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.game.curve.Point2D
import com.clevercorner.arrowescape.game.engine.CollisionChecker
import com.clevercorner.arrowescape.game.engine.ExitSimulator
import com.clevercorner.arrowescape.game.level.LevelShape
import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.Deflector
import com.clevercorner.arrowescape.game.model.Direction
import com.clevercorner.arrowescape.ui.components.BoardFurniture.drawDeflector
import com.clevercorner.arrowescape.ui.components.BoardFurniture.drawLockBadge
import com.clevercorner.arrowescape.ui.components.BoardFurniture.drawSequenceBadge
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Hold this long on an arrow to see where it would go instead of firing it. */
private const val LONG_PRESS_MS = 220L

/** Boards at least this busy open with the pinch-to-zoom hint showing. */
private const val DENSE_ARROW_COUNT = 24
private const val HINT_VISIBLE_MS = 2600L

/**
 * Renders the board of bendable snake-arrows on a subtle dot grid, with pan
 * and pinch-zoom on top.
 *
 * Three things are on show: the arrows themselves, the redirect dots that
 * bend an escape route mid-flight, and colour-locked pairs - two arrows
 * sharing a hue that must leave together.
 *
 * Holding an arrow previews its route rather than firing it, which is the
 * only honest way to teach a deflected exit: the player watches the dashed
 * line turn the corner, and on a blocked route sees the cell that stops it.
 *
 * [resetKey] re-centres the view whenever it changes - pass the level number
 * so every level opens fully in frame.
 */
@Composable
fun GameBoard(
    arrows: List<Arrow>,
    board: Board,
    escapingArrows: Map<Int, List<Cell>>,
    shakingArrows: Set<Int>,
    hintArrowId: Int?,
    resetKey: Any,
    onArrowTap: (Arrow) -> Unit,
    modifier: Modifier = Modifier,
    /** Which mode's surface to paint. */
    skin: BoardSkin = BoardSkin.CLASSIC
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val skinColors = skin.colors()
    val gridRows = board.rows
    val gridCols = board.cols
    val boardDescription = stringResource(R.string.board_content_description)

    // Stable animation state holders - mutations happen in SideEffect, not during composition
    val escapeAnimatables = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
    val shakeAnimatables = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }

    // Cleanup stale animations after composition
    SideEffect {
        escapeAnimatables.keys.filter { it !in escapingArrows }.forEach { escapeAnimatables.remove(it) }
        shakeAnimatables.keys.filter { it !in shakingArrows }.forEach { shakeAnimatables.remove(it) }
    }

    // Launch escape animations for each escaping arrow
    escapingArrows.keys.forEach { id ->
        key(id) {
            val animatable = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                escapeAnimatables[id] = animatable
                animatable.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
        }
    }

    // Launch shake animations
    shakingArrows.forEach { id ->
        key(id) {
            val animatable = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                shakeAnimatables[id] = animatable
                animatable.animateTo(1f, tween(300, easing = LinearEasing))
            }
        }
    }

    val escapeAnim: Map<Int, Float> = escapeAnimatables.mapValues { it.value.value }
    val shakeAnim: Map<Int, Float> = shakeAnimatables.mapValues {
        sin(it.value.value * 4 * Math.PI.toFloat()) * 9f
    }

    val pulse = rememberInfiniteTransition(label = "boardPulse")
    val hintGlow by pulse.animateFloat(
        0.25f, 0.6f,
        infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hintGlow"
    )
    val dashPhase by pulse.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "previewDash"
    )

    val hintedGroup = arrows.firstOrNull { it.id == hintArrowId }?.colorGroup

    var viewport by remember { mutableStateOf(BoardViewport.RESET) }
    var held by remember { mutableStateOf<Int?>(null) }
    val tracker = remember { PressTracker() }
    val atRest by remember { derivedStateOf { viewport.isIdentity } }

    var zoomHintVisible by remember { mutableStateOf(false) }
    LaunchedEffect(resetKey) {
        viewport = BoardViewport.RESET
        held = null
        zoomHintVisible = arrows.size >= DENSE_ARROW_COUNT
        delay(HINT_VISIBLE_MS)
        zoomHintVisible = false
    }

    // A rejected tap explains itself: the same dashed route, in the error
    // colour, stopping dead on whatever is in the way.
    val previewArrowId = held ?: shakingArrows.firstOrNull()
    val preview = previewArrowId
        ?.let { id -> arrows.firstOrNull { it.id == id && it.isActive } }
        ?.let { arrow -> CollisionChecker.traceFor(arrow, arrows.filter { it.isActive }, board) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(gridCols.toFloat() / gridRows.toFloat()),
        shape = MaterialTheme.shapes.extraLarge,
        color = skinColors.surface,
        shadowElevation = 4.dp
    ) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        // Using constraints from BoxWithConstraintsScope
        val boardW = this.constraints.maxWidth.toFloat()
        val boardH = this.constraints.maxHeight.toFloat()
        val cell = minOf(boardW / gridCols, boardH / gridRows)
        val gridW = cell * gridCols
        val gridH = cell * gridRows
        val offX = (boardW - gridW) / 2f
        val offY = (boardH - gridH) / 2f

        fun centerOf(c: Cell) = Offset(
            offX + c.col * cell + cell / 2,
            offY + c.row * cell + cell / 2
        )

        // Keep the gesture-detector coroutine ALIVE across recompositions.
        // If we keyed pointerInput on `arrows` directly, a tap that
        // synchronously mutates gameState (see GameViewModel.handleValidMove)
        // would change `arrows`' identity mid-callback, causing Compose to
        // cancel + relaunch detectTapGestures right in the middle of the
        // gesture - which can double-fire the tap against the already-
        // updated list (two arrows escaping from a single click). Reading
        // everything through rememberUpdatedState gives fresh data (including
        // layout metrics on rotation/resize) without ever restarting the
        // coroutine mid-gesture.
        val latestArrows by rememberUpdatedState(arrows)
        val latestOnArrowTap by rememberUpdatedState(onArrowTap)
        val latestLayout by rememberUpdatedState(
            BoardLayout(cell, offX, offY, Point2D(boardW, boardH))
        )

        /** Which arrow is under this screen-space touch, pan and zoom included. */
        fun arrowAt(touch: Offset): Arrow? {
            val layout = latestLayout
            val canvas = viewport.toCanvas(Point2D(touch.x, touch.y))
            val col = ((canvas.x - layout.offX) / layout.cell).toInt()
            val row = ((canvas.y - layout.offY) / layout.cell).toInt()
            return latestArrows.firstOrNull { it.isActive && it.occupies(row, col) }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = boardDescription }
                // Slide around / pinch-zoom. Consumes events only once a drag
                // passes touch slop, so plain taps fall through to the tap
                // detector below.
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        viewport = viewport.transformedBy(
                            centroid = Point2D(centroid.x, centroid.y),
                            panDelta = Point2D(pan.x, pan.y),
                            zoomFactor = zoom,
                            // The grid board owns its whole canvas, so its
                            // walls are the card's own edges.
                            bounds = BoardBounds(latestLayout.viewSize)
                        )
                        held = null
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
                                        held = target.id
                                        tracker.previewShown = true
                                    }
                                    tryAwaitRelease()
                                    pending.cancel()
                                }
                            }
                            held = null
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
                drawBoardBackdrop(
                    skin = skin,
                    colors = skinColors,
                    rows = gridRows,
                    cols = gridCols,
                    cell = cell,
                    offsetX = offX,
                    offsetY = offY
                )

                board.deflectors.forEach { deflector ->
                    drawDeflector(deflector, centerOf(deflector.cell), cell, gameColors)
                }

                arrows.filter { it.isActive || escapingArrows.containsKey(it.id) }.forEach { arrow ->
                    val route = escapingArrows[arrow.id]
                    val progress = escapeAnim[arrow.id] ?: 0f
                    val shake = shakeAnim[arrow.id] ?: 0f

                    // A hinted half of a locked pair lights up its partner too -
                    // pointing at one of them would be half a hint.
                    val isHinted = arrow.id == hintArrowId ||
                            (hintedGroup != null && arrow.colorGroup == hintedGroup)
                    val lock = arrow.colorGroup?.let { BoardFurniture.lockColors(it) }
                    val (c1, c2) = when {
                        isHinted -> gameColors.arrowHint to gameColors.arrowHint
                        lock != null -> lock
                        else -> gameColors.arrowPrimary to gameColors.arrowSecondary
                    }

                    val basePoints = arrow.cells.map { centerOf(it) }
                    val points: List<Offset> = if (route != null && progress > 0f) {
                        // Snake-peeling: the head runs the exact route the engine
                        // traced - deflector bends included - and the body follows
                        // the trail it carved.
                        slitherPoints(basePoints, route.map { centerOf(it) }, progress, gridW + gridH)
                    } else {
                        basePoints.map { Offset(it.x + shake, it.y) }
                    }

                    if (isHinted) {
                        points.forEach {
                            drawCircle(gameColors.arrowHint.copy(alpha = hintGlow), cell * 0.5f, it)
                        }
                    }

                    drawSnakeArrow(points, arrow.direction, cell, c1, c2)
                    if (lock != null) drawLockBadge(points.first(), cell, lock.first)
                    arrow.sequenceKey?.let { key ->
                        // "Ready" is asked of the engine rather than worked
                        // out here, so the badge and the tap can never
                        // disagree about whose turn it is.
                        drawSequenceBadge(
                            center = points.last(),
                            cell = cell,
                            key = key,
                            ready = !CollisionChecker.isSequenceBlocked(arrow, arrows),
                            color = gameColors.arrowHint
                        )
                    }
                }

                preview?.forEach { (arrowId, trace) ->
                    val owner = arrows.firstOrNull { it.id == arrowId } ?: return@forEach
                    drawRoutePreview(
                        from = centerOf(owner.head),
                        routePoints = trace.path.map { centerOf(it) },
                        blockedAt = trace.stoppedAt?.let { centerOf(it) },
                        cell = cell,
                        dashPhase = dashPhase,
                        clearColor = gameColors.arrowHint,
                        blockedColor = gameColors.arrowDown
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = zoomHintVisible && atRest,
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
                Text(
                    stringResource(R.string.board_reset_view),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
    }
}

/** Board metrics a long-lived gesture coroutine needs to hit-test a touch. */
private data class BoardLayout(
    val cell: Float,
    val offX: Float,
    val offY: Float,
    val viewSize: Point2D
)

// ======================= PREVIEWS =======================

/**
 * Creates sample arrows for a given shape - useful for preview testing.
 */
private fun createSampleArrows(shape: LevelShape, gridSize: Int = 8): List<Arrow> {
    val validCells = shape.validCells(gridSize, gridSize)
    if (validCells.isEmpty()) return emptyList()
    
    val arrows = mutableListOf<Arrow>()
    val directions = Direction.entries
    
    // Create arrows at valid cells (every 3rd cell to avoid overlap)
    validCells.filterIndexed { index, _ -> index % 3 == 0 }.take(8).forEachIndexed { idx, (row, col) ->
        val dir = directions[idx % directions.size]
        arrows.add(
            Arrow(
                id = idx,
                cells = listOf(Cell(row, col)),
                direction = dir,
                isActive = true
            )
        )
    }
    return arrows
}

@Preview(showBackground = true, name = "GameBoard - Square")
@Composable
private fun GameBoardPreviewSquare() {
    ArrowEscapeTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            GameBoard(
                arrows = createSampleArrows(LevelShape.SQUARE),
                board = Board(8, 8, listOf(Deflector(Cell(4, 4), Direction.RIGHT))),
                resetKey = 0,
                escapingArrows = emptyMap(),
                shakingArrows = emptySet(),
                hintArrowId = 0,
                onArrowTap = {},
                modifier = Modifier.size(300.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "GameBoard - Spiral (Hard)")
@Composable
private fun GameBoardPreviewSpiral() {
    ArrowEscapeTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SPIRAL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE91E63)
                )
                Spacer(Modifier.height(8.dp))
                GameBoard(
                    arrows = createSampleArrows(LevelShape.SPIRAL, 10),
                    board = Board(10, 10),
                    resetKey = 0,
                escapingArrows = emptyMap(),
                    shakingArrows = emptySet(),
                    hintArrowId = null,
                    onArrowTap = {},
                    modifier = Modifier.size(280.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "GameBoard - All Hard Shapes", widthDp = 400, heightDp = 600)
@Composable
private fun GameBoardPreviewAllHardShapes() {
    val hardShapes = listOf(
        LevelShape.SPIRAL to "Spiral",
        LevelShape.LIGHTNING to "Lightning", 
        LevelShape.CROWN to "Crown",
        LevelShape.RING to "Ring",
        LevelShape.BUTTERFLY to "Butterfly"
    )
    
    ArrowEscapeTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "HARD SHAPES - GameBoard Preview",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE91E63),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                
                hardShapes.chunked(2).forEach { rowShapes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowShapes.forEach { (shape, name) ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                                GameBoard(
                                    arrows = createSampleArrows(shape, 8),
                                    board = Board(8, 8),
                                    resetKey = 0,
                escapingArrows = emptyMap(),
                                    shakingArrows = emptySet(),
                                    hintArrowId = null,
                                    onArrowTap = {},
                                    modifier = Modifier.size(150.dp)
                                )
                            }
                        }
                        // Fill empty space if odd number
                        if (rowShapes.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
