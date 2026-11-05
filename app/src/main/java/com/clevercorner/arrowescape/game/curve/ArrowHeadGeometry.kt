package com.clevercorner.arrowescape.game.curve

import kotlin.math.hypot

/**
 * Where an arrowhead sits and which way it points.
 *
 * The head is a solid caret: a triangle whose base butts onto the end of the
 * body, aimed along the direction the body is genuinely travelling.
 *
 * Its size depends on the stroke width and nothing else. Every head on a
 * board is therefore identical, which is how the reference art reads as drawn
 * rather than assembled. Heads used to shrink to fit short arrows; that made
 * sizes vary visibly across one board, and it was treating the symptom. The
 * cure is upstream - the generator now refuses to emit an arrow too small to
 * carry a head (see [minBodyLength] and [minVisualExtent]), so there is
 * nothing left to shrink for.
 *
 * Size and aim are two separate questions, answered separately. They were
 * once entangled - the heading was measured over a reach that shrank with the
 * head - which meant a short arrow silently re-aimed itself as a side effect
 * of being short. [headingOf] walks a fixed stretch of arc length and does
 * not care how big the triangle ended up.
 *
 * The heading computed here is also what [CurvedArrow.direction] returns, so
 * the way the head points and the way the arrow escapes can never disagree.
 * That makes a wrong heading a *fairness* bug and not only an ugly one: the
 * player reads the head to predict the exit.
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
     * Shortest arrow the generator may emit, in head depths of **arc length**.
     *
     * Below this the head is most of the arrow, and a triangle with a stump
     * behind it conveys no direction - which is the entire job of an arrow.
     */
    private const val MIN_BODY_IN_DEPTHS = 2.6f

    /**
     * Smallest patch of board an arrow may occupy, in head depths, measured
     * as the diagonal of its bounding box.
     *
     * Arc length alone is not legibility. A stroke with ample arc length that
     * curls through 270 degrees has spent all of it turning and occupies a
     * smudge the size of its own head. Arc length says fine; the eye says
     * debris. A U-turn clears this bar comfortably; a coil does not, which is
     * exactly the line we want to draw.
     */
    private const val MIN_EXTENT_IN_DEPTHS = 1.8f

    /** Base tucks this far back into the body so no seam shows on curves. */
    private const val OVERLAP = 0.25f

    /**
     * Shortest arrow that can carry a head and still read as an arrow rather
     * than a triangle with a stump. Measured along the curve.
     */
    fun minBodyLength(strokeWidth: Float): Float =
        idealDepth(strokeWidth) * MIN_BODY_IN_DEPTHS

    /**
     * Smallest bounding-box diagonal an arrow may occupy. Companion to
     * [minBodyLength]: that one asks "is there enough stroke?", this one asks
     * "did the stroke go anywhere?".
     */
    fun minVisualExtent(strokeWidth: Float): Float =
        idealDepth(strokeWidth) * MIN_EXTENT_IN_DEPTHS

    private fun idealDepth(strokeWidth: Float): Float =
        maxOf(strokeWidth * DEPTH, MIN_DEPTH)

    private fun idealHalfWidth(strokeWidth: Float): Float =
        maxOf(strokeWidth * HALF_WIDTH, MIN_HALF_WIDTH)

    /** Triangle length for a body of [strokeWidth], in board units. */
    fun depth(strokeWidth: Float): Float = idealDepth(strokeWidth)

    /** Triangle half-width for a body of [strokeWidth], in board units. */
    fun halfWidth(strokeWidth: Float): Float = idealHalfWidth(strokeWidth)

    /** Corners of the head triangle: [back-left, tip, back-right]. */
    data class Triangle(val backLeft: Point2D, val tip: Point2D, val backRight: Point2D)

    /**
     * Head triangle for a body ending at [anchor] and travelling along
     * [heading] (unit vector).
     */
    fun triangle(
        anchor: Point2D,
        heading: Point2D,
        strokeWidth: Float
    ): Triangle {
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
     * Measured over the last [idealDepth] of **arc length** - the stretch of
     * body the triangle actually sits on - averaging each segment's direction
     * weighted by how much of it falls inside that stretch. Averaging is what
     * absorbs the float noise that made a head twitch when it read only the
     * final sample.
     *
     * Arc length is the whole point. The previous version walked back to the
     * first sample lying [idealDepth] away in *straight-line* distance, which
     * is the same thing only while the body is straight. On a curl it picked a
     * sample from a different limb of the coil, and on a tight enough curl no
     * sample was ever far enough, so it fell back to the tail - aiming the
     * head along the tail-to-tip chord, which can point sideways or backwards.
     * Walking the curve instead of measuring across it cannot do either.
     *
     * The reach is the *ideal* depth, deliberately not the shrunk-to-fit one:
     * how a head aims and how big it is are two questions, and tying them
     * together meant a short arrow re-aimed itself as a side effect of being
     * short.
     *
     * Falls back to [fallback] for a degenerate body - never to the tail.
     */
    fun headingOf(body: List<Point2D>, strokeWidth: Float, fallback: Point2D): Point2D {
        if (body.size < 2) return fallback
        val reach = idealDepth(strokeWidth)

        // Walk tip -> tail. Each segment contributes its own direction, scaled
        // by the length of it that lies within reach, so a segment only
        // partly inside the stretch only partly counts.
        var sumX = 0f
        var sumY = 0f
        var walked = 0f

        for (i in body.lastIndex downTo 1) {
            val to = body[i]
            val from = body[i - 1]
            val stepX = to.x - from.x
            val stepY = to.y - from.y
            val step = hypot(stepX, stepY)
            if (step <= 0f) continue

            val used = minOf(step, reach - walked)
            sumX += stepX / step * used
            sumY += stepY / step * used
            walked += used
            if (walked >= reach) break
        }

        if (walked <= 0f) return fallback
        val dir = Point2D(sumX, sumY).normalize()
        return if (dir == Point2D.ZERO) fallback else dir
    }
}
