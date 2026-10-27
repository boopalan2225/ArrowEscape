package com.clevercorner.arrowescape.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.clevercorner.arrowescape.game.model.Direction

/**
 * Canvas geometry for the classic board: snake bodies, the dot grid and the
 * route preview.
 *
 * Split out of [GameBoard] so that file can stay about *behaviour* - gestures,
 * zoom, which arrow is under the finger - while this one stays about pixels.
 */

/** The faint lattice the arrows sit on. */
internal fun DrawScope.drawDotGrid(
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
internal fun DrawScope.drawSnakeArrow(
    points: List<Offset>,
    direction: Direction,
    cell: Float,
    color1: Color,
    color2: Color
) {
    val stroke = cell * 0.15f
    val head = points.last()

    val dirVec = BoardFurniture.unitVector(direction)
    val perp = Offset(-dirVec.y, dirVec.x)

    // Arrowhead geometry
    val tip = head + dirVec * (cell * 0.42f)
    val headHalf = cell * 0.27f
    val headBack = tip - dirVec * (cell * 0.44f)

    // Branded gradient brush running from the tail to the tip.
    val brush = Brush.linearGradient(
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
 * Draws where an arrow would go if tapped: a marching dashed line along the
 * real traced route, bends and all.
 *
 * This is the answer to "why did that arrow turn?" - the player sees the
 * corner before committing, and on a blocked route sees exactly which cell
 * said no.
 */
internal fun DrawScope.drawRoutePreview(
    from: Offset,
    routePoints: List<Offset>,
    blockedAt: Offset?,
    cell: Float,
    dashPhase: Float,
    clearColor: Color,
    blockedColor: Color
) {
    if (routePoints.isEmpty()) return

    val color = if (blockedAt == null) clearColor else blockedColor
    val dash = cell * 0.30f
    val path = roundedPolyline(listOf(from) + routePoints, cornerRadius = cell * 0.45f)

    drawPath(
        path,
        color.copy(alpha = 0.85f),
        style = Stroke(
            width = cell * 0.10f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dash, dash),
                phase = -dashPhase * dash * 2f
            )
        )
    )

    if (blockedAt == null) {
        // A ring where the arrow leaves the board - the destination, marked.
        drawCircle(color, cell * 0.20f, routePoints.last(), style = Stroke(width = cell * 0.07f))
    } else {
        drawCross(blockedAt, cell * 0.22f, color, cell * 0.09f)
    }
}

private fun DrawScope.drawCross(center: Offset, reach: Float, color: Color, width: Float) {
    val stroke = Stroke(width = width, cap = StrokeCap.Round)
    drawLine(
        color,
        center + Offset(-reach, -reach),
        center + Offset(reach, reach),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
    drawLine(
        color,
        center + Offset(reach, -reach),
        center + Offset(-reach, reach),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
}

/**
 * Builds a poly-line through [pts] with rounded corners of up to [cornerRadius]
 * at each interior vertex (quadratic bezier), giving soft snake bends.
 */
internal fun roundedPolyline(pts: List<Offset>, cornerRadius: Float): Path {
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

internal fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

internal fun unit(v: Offset): Offset {
    val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y)
    return if (len == 0f) Offset.Zero else Offset(v.x / len, v.y / len)
}

/**
 * Computes the slithering positions of each body point as the snake exits.
 * The head runs off along [routePoints] - the engine's traced escape route,
 * so a deflected arrow visibly turns the corner - and every other point
 * advances the same arc distance along the shared track, so the body follows
 * the head's trail (snake going into a hole) rather than sliding rigidly.
 */
internal fun slitherPoints(
    base: List<Offset>,
    routePoints: List<Offset>,
    progress: Float,
    extend: Float
): List<Offset> {
    // Track = body (tail..head) + the route + a run-off past the last step so
    // the snake clears the screen instead of parking on the edge.
    val track = ArrayList<Offset>(base.size + routePoints.size + 1)
    track.addAll(base)
    track.addAll(routePoints)
    val last = track.last()
    val heading = unit(last - track[track.size - 2])
    track.add(last + heading * extend)

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
