package com.example.arrowescape.game.curve

/**
 * Decides which arrow a touch means.
 *
 * With dense packing several arrows can sit under one fingertip, so taking
 * the first hit would pick whichever arrow happened to be generated earliest.
 * The nearest body wins instead.
 */
object ArrowPicker {

    /** Closest live arrow within [radius] of [point], or null. */
    fun pick(arrows: List<CurvedArrow>, point: Point2D, radius: Float): CurvedArrow? =
        arrows.asSequence()
            .filter { it.isActive }
            .mapNotNull { arrow ->
                arrow.polyline.minOfOrNull { it.distanceTo(point) }?.let { arrow to it }
            }
            .filter { it.second <= radius }
            .minByOrNull { it.second }
            ?.first
}
