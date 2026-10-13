package com.example.arrowescape.ui.dev

import com.example.arrowescape.game.curve.DifficultyTuning
import com.example.arrowescape.game.curve.ShapeCatalog

/**
 * Level-number arithmetic for the developer picker.
 *
 * Level N plays catalogue entry `N % size`, so "show me the Tree at Master
 * difficulty" means finding the first level at or after the Master tier whose
 * remainder is the Tree's index.
 *
 * A tier is only a few levels wide - narrower than the shape rotation - so
 * most shapes never occur in the early tiers at all. [levelInTier] reports that
 * honestly instead of returning a level from some later tier.
 */
object DevLevels {

    const val MAX_LEVEL = 9999

    /**
     * Smallest level `L >= atLeast` that plays the shape at [shapeIndex].
     */
    fun levelFor(
        shapeIndex: Int,
        atLeast: Int,
        shapeCount: Int = ShapeCatalog.ALL.size
    ): Int {
        val start = atLeast.coerceAtLeast(1)
        return start + Math.floorMod(shapeIndex - start, shapeCount)
    }

    /**
     * The level that plays the shape at [shapeIndex] *within* the tier at
     * [tierIndex] (see [DifficultyTuning.TIER_STARTS]), or null if that tier is
     * too short for the shape to come round.
     */
    fun levelInTier(
        shapeIndex: Int,
        tierIndex: Int,
        shapeCount: Int = ShapeCatalog.ALL.size
    ): Int? {
        val tiers = DifficultyTuning.TIER_STARTS
        val first = tiers[tierIndex].second
        val last = tiers.getOrNull(tierIndex + 1)?.second?.minus(1) ?: MAX_LEVEL
        return levelFor(shapeIndex, first, shapeCount).takeIf { it <= last }
    }

    /** Parse what the developer typed into a playable level, or null. */
    fun parseLevel(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 1..MAX_LEVEL }
}
