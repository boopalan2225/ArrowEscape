package com.arrowescape.game.curve

/**
 * Where an arrowhead sits and which way it points.
 *
 * The head is a solid caret: a triangle whose base butts onto the end of the
 * body, aimed along the direction the body is genuinely travelling.
 *
 * Its size is **relative to the arrow it belongs to**, not just to the stroke.
 * A triangle sized purely off stroke width is effectively fixed, and on the
 * short, tightly curled arrows that fill the edges of a shape it swamped the
 * body: the head became most of the arrow and stopped reading as a direction.
 * Worse, the heading is measured over the stretch of body the triangle covers,
 * so an oversized head measured its own direction across the curl and came out
 * visibly crooked. Shrinking the head therefore fixes the aim as well as the
 * bulk - the two are the same bug.
 *
 * The heading computed here is also what [CurvedArrow.direction] returns, so
 * the way the head points and the way the arrow escapes can never disagree.
 *
 * Pure math, no drawing, so placement is unit-testable.
 */
object ArrowHeadGeometry {

    /** Triangle length along the heading, in stroke widths. */
    const val DEPTH = 2.3f

    /** Triangle half-width across the heading, in stroke widths. */
    const val HALF_WIDTH = 1.7f

    /**
     * Floors for the two sizes above, in board units. Dense late-game levels
     * use hair-thin strokes, and a head scaled purely off stroke width shrank
     * to a speck that could not be read as a direction.
     */
    private const val MIN_DEPTH = 6f
    private const val MIN_HALF_WIDTH = 3f

    /**
     * The most of an arrow's own length the head may occupy.
     *
     * This is a *safety net*, not the main defence. The real fix is that the
     * generator refuses to emit an arrow too short to carry a head (see
     * [minBodyLength]); this only catches the leftovers, e.g. a body truncated
     * mid-escape. Shrinking a head is always a lesser evil than letting it
     * swallow its arrow, but a shrunk head still has to be *visible* - hence
     * [MIN_SHRINK].
     */
    private const val MAX_LENGTH_FRACTION = 0.38f

    /**
     * The head never shrinks below this fraction of its ideal size. Scaling a
     * head down without a floor is how a stubby arrow ends up with a speck
     * that reads as no direction at all - worse than a head that is slightly
     * too big for its shaft.
     */
    private const val MIN_SHRINK = 0.75f

    /** Base tucks this far back into the body so no seam shows on curves. */
    private const val OVERLAP = 0.25f

    /** Stand-in length for callers that have no body to measure. */
    const val UNBOUNDED = Float.MAX_VALUE

    /**
     * Shortest arrow that can carry a full-size head and still read as an
     * arrow rather than a triangle with a stump.
     *
     * The generator uses this as its minimum arrow length, so that by
     * construction almost no arrow ever needs shrinking.
     */
    fun minBodyLength(strokeWidth: Float): Float =
        idealDepth(strokeWidth) / MAX_LENGTH_FRACTION

    private fun idealDepth(strokeWidth: Float): Float =
        maxOf(strokeWidth * DEPTH, MIN_DEPTH)

    private fun idealHalfWidth(strokeWidth: Float): Float =
        maxOf(strokeWidth * HALF_WIDTH, MIN_HALF_WIDTH)

    /**
     * How much of its ideal size the head may actually use on a body of
     * [bodyLength], as a factor in 0..1. Applied to depth *and* half-width so
     * a shrunken head keeps its shape instead of turning into a stubby wedge.
     */
    private fun fit(strokeWidth: Float, bodyLength: Float): Float {
        if (bodyLength <= 0f) return 1f
        val allowed = bodyLength * MAX_LENGTH_FRACTION
        return (allowed / idealDepth(strokeWidth)).coerceIn(MIN_SHRINK, 1f)
    }

    /** Triangle length for a body of [strokeWidth] and [bodyLength], in board units. */
    fun depth(strokeWidth: Float, bodyLength: Float = UNBOUNDED): Float =
        idealDepth(strokeWidth) * fit(strokeWidth, bodyLength)

    /** Triangle half-width for a body of [strokeWidth] and [bodyLength], in board units. */
    fun halfWidth(strokeWidth: Float, bodyLength: Float = UNBOUNDED): Float =
        idealHalfWidth(strokeWidth) * fit(strokeWidth, bodyLength)

    /** Corners of the head triangle: [back-left, tip, back-right]. */
    data class Triangle(val backLeft: Point2D, val tip: Point2D, val backRight: Point2D)

    /**
     * Head triangle for a body ending at [anchor] and travelling along
     * [heading] (unit vector), sized to fit an arrow of [bodyLength].
     */
    fun triangle(
        anchor: Point2D,
        heading: Point2D,
        strokeWidth: Float,
        bodyLength: Float = UNBOUNDED
    ): Triangle {
        val dir = heading.normalize()
        val side = dir.perpendicular()
        val baseCentre = anchor - dir * (strokeWidth * OVERLAP)
        val tip = baseCentre + dir * depth(strokeWidth, bodyLength)
        val half = side * halfWidth(strokeWidth, bodyLength)
        return Triangle(baseCentre + half, tip, baseCentre - half)
    }

    /**
     * Heading at the end of a tail-to-head [body] polyline.
     *
     * Measured against a point one head-length back - the stretch of body the
     * triangle actually sits on - rather than the immediately previous sample,
     * which is close enough that float noise (or a sharp bend) made the head
     * twitch. Because that reach is now capped by the body's own length, a
     * short curled arrow measures over a short stretch and the head follows
     * the curl instead of cutting across it.
     *
     * Falls back to [fallback] for a degenerate body.
     */
    fun headingOf(body: List<Point2D>, strokeWidth: Float, fallback: Point2D): Point2D {
        val head = body.lastOrNull() ?: return fallback
        val reach = depth(strokeWidth, pathLength(body))
        val behind = body.asReversed().drop(1).firstOrNull { it.distanceTo(head) >= reach }
            ?: body.firstOrNull { it != head }
            ?: return fallback
        val dir = (head - behind).normalize()
        return if (dir == Point2D.ZERO) fallback else dir
    }

    /** Arc length of a polyline, in board units. */
    fun pathLength(body: List<Point2D>): Float {
        if (body.size < 2) return 0f
        var total = 0f
        for (i in 1..body.lastIndex) total += body[i - 1].distanceTo(body[i])
        return total
    }
}
