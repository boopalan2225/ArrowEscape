package com.clevercorner.arrowescape.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.clevercorner.arrowescape.game.curve.ArrowHeadGeometry
import com.clevercorner.arrowescape.game.curve.ArrowMotion
import com.clevercorner.arrowescape.game.curve.CurvedArrow
import com.clevercorner.arrowescape.game.curve.Point2D

/**
 * Drawing for curved arrows. Kept apart from the board composable so gestures
 * and pixels can change independently.
 *
 * Everything an arrow does - resting, escaping, bumping into a blocker - is one
 * idea: the body slides some distance along its own curve. Escape and bump
 * therefore share [drawSlid] and differ only in how far, how opaque and what
 * tint.
 */
internal data class ArrowRenderState(
    val escapeProgress: Float = 0f,
    val bump: ArrowMotion.Bump? = null,
    val isHinted: Boolean = false,
    val hintGlow: Float = 0f,
    /** Board colour, drawn as a thin rim round the head so it reads on top of neighbours. */
    val haloColor: Color = Color.Transparent,
    /** Replaces the arrow's own baked colour - how one theme recolours every shape consistently. See [duotone]. */
    val colorOverride: Color? = null,
    /**
     * Whether a numbered arrow's turn has come.
     *
     * Supplied by the caller rather than worked out here, because the engine
     * owns the rule and a badge that disagreed with the tap would be worse
     * than no badge at all.
     */
    val sequenceReady: Boolean = false
)

/** Flashed over an arrow that was tapped while blocked. */
private val BLOCKED_TINT = Color(0xFFE53935)

private val PREVIEW_CLEAR = Color(0xFF34C759)
private val PREVIEW_BLOCKED = Color(0xFFFF3B30)

internal fun DrawScope.drawArrow(
    arrow: CurvedArrow,
    scaleX: Float,
    scaleY: Float,
    state: ArrowRenderState
) {
    if (arrow.segments.isEmpty()) return

    val color = state.colorOverride ?: Color(arrow.color)
    val project = projector(scaleX, scaleY)
    val strokePx = arrow.strokeWidth * (scaleX + scaleY) / 2f

    val bump = state.bump
    when {
        state.escapeProgress > 0f -> {
            // Ease in then out: a gentle release, a confident exit, no hard stop.
            val eased = ArrowMotion.easeEscape(state.escapeProgress)
            // Long enough for the tail to clear the board from anywhere on it.
            val runway = (size.width + size.height) / ((scaleX + scaleY) / 2f)
            // Stay solid while visible; fade only as the tail leaves the board.
            val alpha = (1f - eased * eased).coerceIn(0f, 1f)
            drawSlid(arrow, eased * (arrow.length + runway), color.copy(alpha = alpha), state.haloColor.copy(alpha = alpha), strokePx, project)
        }

        bump != null -> {
            val shape = ArrowMotion.bumpProfile(bump.progress)
            val tinted = lerp(color, BLOCKED_TINT, shape * BLOCKED_TINT_STRENGTH)
            drawSlid(arrow, shape * bump.distance, tinted, state.haloColor, strokePx, project)
        }

        else -> drawResting(arrow, color, strokePx, state, project)
    }

    // Badges ride on top of the resting arrow only. During an escape or a
    // bump the arrow is mid-animation and a numbered disc sliding along with
    // it reads as debris rather than as information.
    if (state.escapeProgress == 0f && bump == null) {
        arrow.sequenceKey?.let { key ->
            drawSequencePips(
                centre = project(arrow.startPoint),
                radiusPx = strokePx * 1.5f,
                key = key,
                ready = state.sequenceReady,
                color = color
            )
        }
    }
}

/**
 * The number on a sequenced arrow, as pips on a ring.
 *
 * Pips rather than a numeral for the same reason as the classic board: no
 * text measurer in a DrawScope, and a digit at this size would be a smudge.
 * Drawn at the arrow's *tail* so it does not sit under the arrowhead, which
 * is the part the player is reading for direction.
 *
 * [ready] carries the whole fairness argument - a numbered arrow waiting its
 * turn is dim and hollow, the one that may go is solid and bright. Without
 * that, tapping a clear lane and getting nothing looks like a bug.
 */
private fun DrawScope.drawSequencePips(
    centre: Offset,
    radiusPx: Float,
    key: Int,
    ready: Boolean,
    color: Color
) {
    val alpha = if (ready) 1f else 0.40f
    drawCircle(Color.Black.copy(alpha = 0.60f * alpha), radiusPx, centre)
    drawCircle(
        color.copy(alpha = alpha),
        radiusPx,
        centre,
        style = Stroke(width = radiusPx * (if (ready) 0.30f else 0.18f))
    )

    val count = key.coerceIn(1, 8)
    val ring = radiusPx * 0.52f
    val pip = radiusPx * 0.19f
    for (i in 0 until count) {
        val angle = -Math.PI / 2 + 2 * Math.PI * i / count
        drawCircle(
            color.copy(alpha = alpha),
            pip,
            centre + Offset((Math.cos(angle) * ring).toFloat(), (Math.sin(angle) * ring).toFloat())
        )
    }
}

private const val BLOCKED_TINT_STRENGTH = 0.85f

/**
 * The body advanced [travelled] units along its own curve - the tail follows
 * exactly where the head went, like a train on a track - continuing straight
 * once past the tip.
 */
private fun DrawScope.drawSlid(
    arrow: CurvedArrow,
    travelled: Float,
    color: Color,
    halo: Color,
    strokePx: Float,
    project: (Point2D) -> Offset
) {
    val body = arrow.polyline
    val spacing = if (body.size > 1) arrow.length / (body.size - 1) else 0f

    // Ordered tail -> head, so `last()` is the arrowhead.
    val slid = body.indices.map { i ->
        val behindHead = (body.size - 1 - i) * spacing
        arrow.pointAtDistance(arrow.length + travelled - behindHead, arrow.direction)
    }

    val head = headPath(arrow, slid, project)
    drawHeadHalo(head, halo, strokePx)

    if (slid.size >= 2) {
        val path = Path()
        project(slid.first()).let { path.moveTo(it.x, it.y) }
        for (i in 1 until slid.size) project(slid[i]).let { path.lineTo(it.x, it.y) }
        drawPath(path, color, style = bodyStroke(strokePx))
    }

    head?.let { drawPath(it, color) }
}

/** Resting render: the arrow sits exactly on its bezier path. */
private fun DrawScope.drawResting(
    arrow: CurvedArrow,
    color: Color,
    strokePx: Float,
    state: ArrowRenderState,
    project: (Point2D) -> Offset
) {
    val path = Path()
    arrow.segments.forEachIndexed { i, seg ->
        if (i == 0) project(seg.p0).let { path.moveTo(it.x, it.y) }
        val c1 = project(seg.p1)
        val c2 = project(seg.p2)
        val end = project(seg.p3)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
    }

    if (state.isHinted) {
        drawPath(path, color.copy(alpha = state.hintGlow), style = bodyStroke(strokePx * HINT_GLOW_WIDTH))
    }

    val head = headPath(arrow, arrow.polyline, project)
    drawHeadHalo(head, state.haloColor, strokePx)
    drawPath(path, color, style = bodyStroke(strokePx))
    head?.let { drawPath(it, color) }
}

private const val HINT_GLOW_WIDTH = 2.5f

/** Rim width round a head, as a fraction of the body stroke (half shows outside the edge). */
private const val HEAD_HALO_WIDTH = 0.8f

/**
 * Solid triangular head, flush on the end of [body] and aimed along the way
 * the body is genuinely travelling (see [ArrowHeadGeometry]). Null for an
 * empty body.
 */
private fun headPath(
    arrow: CurvedArrow,
    body: List<Point2D>,
    project: (Point2D) -> Offset
): Path? {
    val anchor = body.lastOrNull() ?: return null
    val heading = ArrowHeadGeometry.headingOf(body, arrow.strokeWidth, arrow.direction)
    val tri = ArrowHeadGeometry.triangle(anchor, heading, arrow.strokeWidth)

    return Path().apply {
        project(tri.backLeft).let { moveTo(it.x, it.y) }
        project(tri.tip).let { lineTo(it.x, it.y) }
        project(tri.backRight).let { lineTo(it.x, it.y) }
        close()
    }
}

/**
 * A rim of [halo] colour round the head, separating it from whatever it
 * overlaps. Painted BEFORE the arrow's own body and head: drawn after the
 * body it cut a visible notch out of it and the head looked detached.
 */
private fun DrawScope.drawHeadHalo(head: Path?, halo: Color, strokePx: Float) {
    if (head == null || halo.alpha <= 0f) return
    drawPath(head, halo, style = Stroke(width = strokePx * HEAD_HALO_WIDTH, join = StrokeJoin.Round))
}

/**
 * Long-press preview: the straight line an arrow would travel to leave the
 * board. Green when the way is clear, red when something is in it.
 */
internal fun DrawScope.drawExitPreview(
    from: Point2D,
    to: Point2D,
    isClear: Boolean,
    dashPhase: Float,
    scaleX: Float,
    scaleY: Float,
    strokeWidth: Float
) {
    val project = projector(scaleX, scaleY)
    val base = if (isClear) PREVIEW_CLEAR else PREVIEW_BLOCKED
    val strokePx = strokeWidth * (scaleX + scaleY) / 2f

    val line = Path().apply {
        project(from).let { moveTo(it.x, it.y) }
        project(to).let { lineTo(it.x, it.y) }
    }

    // Soft wide glow underneath, crisp marching dashes on top.
    drawPath(line, base.copy(alpha = 0.30f), style = bodyStroke(strokePx * 2.4f))
    drawPath(
        line,
        base,
        style = Stroke(
            width = strokePx * 0.45f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(strokePx * 1.6f, strokePx * 1.2f), dashPhase * strokePx * 2.8f)
        )
    )
}

private fun projector(scaleX: Float, scaleY: Float): (Point2D) -> Offset =
    { p -> Offset(p.x * scaleX, p.y * scaleY) }

private fun bodyStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/**
 * Recolours [original] onto a two-stop gradient between [dark] and [light],
 * using the original's own luminance as the mix point.
 *
 * Shape art is deliberately painted with shaded regions - an owl's belly is
 * lighter than its wing, a yin-yang's swirl alternates tones - and that
 * shading is what makes the arrows read as a shape rather than a flat heap
 * of lines. A straight colour swap would throw that away; keeping the
 * original's brightness as the blend factor carries the shading across into
 * a theme's own two colours instead.
 */
internal fun duotone(original: Color, dark: Color, light: Color): Color {
    val luminance = 0.299f * original.red + 0.587f * original.green + 0.114f * original.blue
    return lerp(dark, light, luminance.coerceIn(0f, 1f))
}
