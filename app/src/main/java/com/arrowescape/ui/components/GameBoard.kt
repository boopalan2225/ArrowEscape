package com.arrowescape.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arrowescape.game.level.LevelShape
import com.arrowescape.game.model.Arrow
import com.arrowescape.game.model.Cell
import com.arrowescape.game.model.Direction
import com.arrowescape.ui.theme.ArrowEscapeTheme
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
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        gameColors.boardBackground,
                        gameColors.arrowPrimary.copy(alpha = 0.06f)
                    )
                )
            )
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
        val latestCell by rememberUpdatedState(cell)
        val latestOffX by rememberUpdatedState(offX)
        val latestOffY by rememberUpdatedState(offY)
        val latestOnArrowTap by rememberUpdatedState(onArrowTap)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val col = ((tap.x - latestOffX) / latestCell).toInt()
                        val row = ((tap.y - latestOffY) / latestCell).toInt()
                        latestArrows.firstOrNull { it.isActive && it.occupies(row, col) }
                            ?.let { latestOnArrowTap(it) }
                    }
                }
        ) {
            drawDotGrid(gridRows, gridCols, cell, offX, offY, gameColors.gridLine)

            arrows.filter { it.isActive || escapingArrows.containsKey(it.id) }.forEach { arrow ->
                val progress = escapeAnim[arrow.id] ?: 0f
                val shake = shakeAnim[arrow.id] ?: 0f
                val escaping = escapingArrows.containsKey(arrow.id)

                val isHinted = arrow.id == hintArrowId
                val c1 = if (isHinted) gameColors.arrowHint else gameColors.arrowPrimary
                val c2 = if (isHinted) gameColors.arrowHint else gameColors.arrowSecondary

                val basePoints = arrow.cells.map { centerOf(it) }
                val points: List<Offset> = if (escaping && progress > 0f) {
                    // Snake-peeling: head leads out along its direction, body
                    // follows the exact trail the head carved (slithers away).
                    slitherPoints(basePoints, arrow.direction, progress, gridW + gridH)
                } else {
                    basePoints.map { Offset(it.x + shake, it.y) }
                }

                if (isHinted) {
                    points.forEach {
                        drawCircle(gameColors.arrowHint.copy(alpha = hintGlow), cell * 0.5f, it)
                    }
                }

                drawSnakeArrow(points, arrow.direction, cell, c1, c2)
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
 * Draws a bendable arrow as a thick continuous pipe with SOFT rounded bends
 * and a solid filled arrowhead at the tip. [points] are ordered tail -> head.
 */
private fun DrawScope.drawSnakeArrow(
    points: List<Offset>,
    direction: Direction,
    cell: Float,
    color1: Color,
    color2: Color
) {
    val stroke = cell * 0.15f
    val head = points.last()

    val dirVec = when (direction) {
        Direction.UP -> Offset(0f, -1f)
        Direction.DOWN -> Offset(0f, 1f)
        Direction.LEFT -> Offset(-1f, 0f)
        Direction.RIGHT -> Offset(1f, 0f)
    }
    val perp = Offset(-dirVec.y, dirVec.x)

    // Arrowhead geometry
    val tip = head + dirVec * (cell * 0.42f)
    val headHalf = cell * 0.27f
    val headBack = tip - dirVec * (cell * 0.44f)

    // Branded gradient brush running from the tail to the tip.
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(color1, color2),
        start = points.first(),
        end = tip
    )

    // Build the body vertex list (tail -> ... -> headBack). Single-cell arrows
    // get an extended tail so a stem is visible.
    val vertices = ArrayList<Offset>()
    if (points.size == 1) {
        vertices.add(head - dirVec * (cell * 0.40f))
    } else {
        vertices.add(points.first())
        for (i in 1 until points.size - 1) vertices.add(points[i])
    }
    vertices.add(headBack)

    val bodyPath = roundedPolyline(vertices, cornerRadius = cell * 0.40f)
    drawPath(
        bodyPath, brush,
        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Solid filled triangular head
    val headPath = Path().apply {
        val left = headBack + perp * headHalf
        val right = headBack - perp * headHalf
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(headPath, brush, style = Fill)
}

/**
 * Builds a poly-line through [pts] with rounded corners of up to [cornerRadius]
 * at each interior vertex (quadratic bezier), giving soft snake bends.
 */
private fun roundedPolyline(pts: List<Offset>, cornerRadius: Float): Path {
    val path = Path()
    if (pts.isEmpty()) return path
    if (pts.size < 3) {
        path.moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        return path
    }

    path.moveTo(pts.first().x, pts.first().y)
    for (i in 1 until pts.size - 1) {
        val prev = pts[i - 1]
        val curr = pts[i]
        val next = pts[i + 1]

        val r1 = minOf(cornerRadius, distance(prev, curr) / 2f)
        val r2 = minOf(cornerRadius, distance(curr, next) / 2f)

        val entry = curr + unit(prev - curr) * r1
        val exit = curr + unit(next - curr) * r2

        path.lineTo(entry.x, entry.y)
        path.quadraticBezierTo(curr.x, curr.y, exit.x, exit.y)
    }
    val last = pts.last()
    path.lineTo(last.x, last.y)
    return path
}

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x; val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun unit(v: Offset): Offset {
    val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y)
    return if (len == 0f) Offset.Zero else Offset(v.x / len, v.y / len)
}

/**
 * Computes the slithering positions of each body point as the snake exits.
 * The head runs off along [dir]; every other point advances the same arc
 * distance along the shared track, so the body follows the head's trail
 * (snake going into a hole) rather than the whole shape sliding rigidly.
 */
private fun slitherPoints(
    base: List<Offset>,
    dir: Direction,
    progress: Float,
    extend: Float
): List<Offset> {
    val dirVec = when (dir) {
        Direction.UP -> Offset(0f, -1f)
        Direction.DOWN -> Offset(0f, 1f)
        Direction.LEFT -> Offset(-1f, 0f)
        Direction.RIGHT -> Offset(1f, 0f)
    }
    // Track = body (tail..head) + a long straight extension past the head.
    val track = ArrayList<Offset>(base.size + 1)
    track.addAll(base)
    track.add(base.last() + dirVec * extend)

    val cum = FloatArray(track.size)
    for (i in 1 until track.size) cum[i] = cum[i - 1] + distance(track[i - 1], track[i])
    val total = cum.last()
    val d = progress * total

    return base.indices.map { i -> pointAlong(track, cum, cum[i] + d) }
}

private fun pointAlong(pts: List<Offset>, cum: FloatArray, s: Float): Offset {
    if (s <= 0f) return pts.first()
    if (s >= cum.last()) return pts.last()
    var i = 1
    while (i < cum.size && cum[i] < s) i++
    val segLen = (cum[i] - cum[i - 1]).coerceAtLeast(0.0001f)
    val t = (s - cum[i - 1]) / segLen
    return Offset(
        pts[i - 1].x + (pts[i].x - pts[i - 1].x) * t,
        pts[i - 1].y + (pts[i].y - pts[i - 1].y) * t
    )
}

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
                gridRows = 8,
                gridCols = 8,
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
                    gridRows = 10,
                    gridCols = 10,
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
                                    gridRows = 8,
                                    gridCols = 8,
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
