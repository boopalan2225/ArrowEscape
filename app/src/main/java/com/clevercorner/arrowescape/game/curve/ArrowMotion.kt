package com.clevercorner.arrowescape.game.curve

import kotlin.math.PI
import kotlin.math.sin

/**
 * Timing and shape of arrow animations, as pure functions.
 *
 * The Flutter game scales exit time with arrow length (`0.4s + 0.08s/cell`) and
 * answers a blocked tap by pushing the arrow forward until it touches the
 * blocker and pulling it back.  The old Android version used one fixed 800ms
 * for everything and a sideways shake that had nothing to do with the arrow's
 * heading.  Keeping the maths here means the feel is tunable - and testable -
 * in one place.
 */
object ArrowMotion {

    /**
     * A blocked arrow mid-bump: how far it lunges, and how far through the
     * out-and-back it is (0..1).
     */
    data class Bump(val distance: Float, val progress: Float)

    private const val ESCAPE_BASE_MS = 450f
    private const val ESCAPE_MS_PER_UNIT = 1.5f
    private const val ESCAPE_MIN_MS = 600L
    private const val ESCAPE_MAX_MS = 1200L

    private const val BUMP_BASE_MS = 160f
    private const val BUMP_MS_PER_UNIT = 3f
    private const val BUMP_MIN_MS = 220L
    private const val BUMP_MAX_MS = 420L

    /** Smallest visible nudge, in stroke widths, even when already touching. */
    private const val MIN_BUMP_STROKES = 0.6f

    /** Longest lunge, in stroke widths - keeps a bump a bump, not an escape. */
    private const val MAX_BUMP_STROKES = 8f

    /** Longer arrows take longer to pull through; short ones stay snappy. */
    fun escapeDurationMs(arrowLength: Float): Long =
        (ESCAPE_BASE_MS + arrowLength * ESCAPE_MS_PER_UNIT)
            .toLong()
            .coerceIn(ESCAPE_MIN_MS, ESCAPE_MAX_MS)

    /** Gentle release, confident exit, no hard stop. Smoothstep. */
    fun easeEscape(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Distance an arrow lunges forward when its tap is blocked.
     *
     * [runway] is the free space between the tip and the blocker. The drawn
     * head reaches [ArrowHeadGeometry.depth] past the tip, so that comes off
     * the lunge or the head would visibly bury itself in the blocker. Head
     * size depends on the arrow's own [bodyLength], so that is needed here too
     * - subtracting a head bigger than the one drawn would short the lunge.
     */
    fun bumpDistance(
        runway: Float,
        strokeWidth: Float,
        bodyLength: Float = ArrowHeadGeometry.UNBOUNDED
    ): Float {
        val headRoom = runway - ArrowHeadGeometry.depth(strokeWidth, bodyLength)
        return headRoom.coerceIn(strokeWidth * MIN_BUMP_STROKES, strokeWidth * MAX_BUMP_STROKES)
    }

    fun bumpDurationMs(distance: Float): Long =
        (BUMP_BASE_MS + distance * BUMP_MS_PER_UNIT).toLong().coerceIn(BUMP_MIN_MS, BUMP_MAX_MS)

    /** 0 -> 1 -> 0 over the bump: out to the blocker and back home. */
    fun bumpProfile(progress: Float): Float = sin(progress.coerceIn(0f, 1f) * PI.toFloat())
}
