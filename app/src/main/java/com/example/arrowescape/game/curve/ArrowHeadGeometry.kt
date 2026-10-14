package com.example.arrowescape.game.curve

/**
 * Where an arrowhead sits and which way it points.
 *
 * The old head was a barbed dart whose wings were angled off adjacent polyline
 * samples, so on tight curves it pointed a different way from the body and
 * poked out past the outline.  This is the Flutter game's clean solid caret:
 * a triangle whose base butts onto the end of the body, aimed along the
 * direction the body is genuinely travelling.
 *
 * The heading computed here is also what [CurvedArrow.direction] returns, so
 * the way the head points and the way the arrow escapes can never disagree.
 *
 * Pure math, no drawing, so placement is unit-testable.
 */
object ArrowHeadGeometry {

    /** Triangle length along the heading, in stroke widths. */
    const val DEPTH = 2.6f

    /** Triangle half-width across the heading, in stroke widths. */
    const val HALF_WIDTH = 2.0f

    /**
     * Floors for the two sizes above, in board units. Dense late-game levels
     * use hair-thin strokes, and a head scaled purely off stroke width shrank
     * to a speck that could not be read as a direction.
     */
    private const val MIN_DEPTH = 9f
    private const val MIN_HALF_WIDTH = 4.5f

    /** Base tucks this far back into the body so no seam shows on curves. */
    private const val OVERLAP = 0.25f

    /** Triangle length for a body of [strokeWidth], in board units. */
    fun depth(strokeWidth: Float): Float = maxOf(strokeWidth * DEPTH, MIN_DEPTH)

    /** Triangle half-width for a body of [strokeWidth], in board units. */
    fun halfWidth(strokeWidth: Float): Float = maxOf(strokeWidth * HALF_WIDTH, MIN_HALF_WIDTH)

    /** Corners of the head triangle: [back-left, tip, back-right]. */
    data class Triangle(val backLeft: Point2D, val tip: Point2D, val backRight: Point2D)

    /**
     * Head triangle for a body ending at [anchor] and travelling along
     * [heading] (unit vector).
     */
    fun triangle(anchor: Point2D, heading: Point2D, strokeWidth: Float): Triangle {
        val dir = heading.normalize()
        val side = dir.perpendicular()
        val baseCentre = anchor - dir * (strokeWidth * OVERLAP)
        val tip = baseCentre + dir * depth(strokeWidth)
        val half = side * halfWidth(strokeWidth)
        return Triangle(baseCentre + half, tip, baseCentre - half)
    }

    /**
     * Heading at the end of a tail-to-head [body] polyline.
     *
     * Measured against a point one head-length back - the stretch of body the
     * triangle actually sits on - rather than the immediately previous sample,
     * which is close enough that float noise (or a sharp bend) made the head
     * twitch.  Falls back to [fallback] for a degenerate body.
     */
    fun headingOf(body: List<Point2D>, strokeWidth: Float, fallback: Point2D): Point2D {
        val head = body.lastOrNull() ?: return fallback
        val reach = depth(strokeWidth)
        val behind = body.asReversed().drop(1).firstOrNull { it.distanceTo(head) >= reach }
            ?: body.firstOrNull { it != head }
            ?: return fallback
        val dir = (head - behind).normalize()
        return if (dir == Point2D.ZERO) fallback else dir
    }
}
