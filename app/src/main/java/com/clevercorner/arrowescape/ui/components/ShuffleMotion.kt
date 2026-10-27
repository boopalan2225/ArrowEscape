package com.clevercorner.arrowescape.ui.components

import androidx.compose.ui.geometry.Offset
import com.clevercorner.arrowescape.game.curve.CurvedArrow
import kotlin.math.hypot

/**
 * Geometry for the daily challenge's re-arrangement animation.
 *
 * The board folds every arrow flat, swaps in the new arrangement while
 * nothing is visible, and unfolds. These two functions decide the *shape* of
 * that movement; the ViewModel owns its timing.
 */

/** Where an arrow sits on the canvas, for ripple and pivot purposes. */
internal fun CurvedArrow.screenCentre(scaleX: Float, scaleY: Float): Offset {
    val (min, max) = getBounds()
    return Offset(
        (min.x + max.x) / 2f * scaleX,
        (min.y + max.y) / 2f * scaleY
    )
}

/**
 * How far out from the middle of the board a point is, as 0..1.
 *
 * Drives the stagger: the centre folds first and unfolds first, so the
 * change visibly travels outwards and then back in, like a ripple.
 */
internal fun rippleOf(centre: Offset, canvasWidth: Float, canvasHeight: Float): Float {
    val dx = centre.x - canvasWidth / 2f
    val dy = centre.y - canvasHeight / 2f
    val reach = hypot(canvasWidth, canvasHeight) / 2f
    if (reach <= 0f) return 0f
    return (hypot(dx, dy) / reach).coerceIn(0f, 1f)
}

/**
 * The scale to draw an arrow at, given the global [phase] (1 settled, 0
 * fully folded) and its own [ripple] position.
 *
 * Every arrow must reach zero *before* the swap happens, so the stagger is
 * squeezed into the first part of each half rather than spread across all of
 * it: a late arrow that was still visible at the swap would show its head
 * jumping, which is the exact artefact this animation exists to hide.
 */
internal fun shuffleScaleFor(phase: Float, ripple: Float): Float {
    val folded = 1f - phase.coerceIn(0f, 1f)
    val start = ripple * STAGGER
    val local = ((folded - start) / (1f - STAGGER)).coerceIn(0f, 1f)
    // Never exactly zero: a zero-scale transform collapses the whole matrix
    // and some renderers take that badly.
    return (1f - local).coerceAtLeast(0.001f)
}

/** Share of each half-animation given over to staggering. */
private const val STAGGER = 0.45f
