package com.clevercorner.arrowescape.ui.dev

import com.clevercorner.arrowescape.game.curve.CurvedJourney
import com.clevercorner.arrowescape.game.curve.DifficultyTuning

/**
 * Level-number arithmetic for the developer picker.
 *
 * The structure itself (which shape owns which levels) comes from
 * [CurvedJourney]; this only answers the picker's questions about it.
 */
object DevLevels {

    const val MAX_LEVEL = 9999

    /**
     * A level that plays the shape of [chapter] at the hardest tier.
     *
     * Levels past the end of the journey wrap to the first chapter again, so
     * the same shape comes round at Master difficulty one lap later.
     */
    fun masterReplay(chapter: Int): Int =
        generateSequence(CurvedJourney.firstLevelOf(chapter)) { it + CurvedJourney.totalLevels }
            .first { it > DifficultyTuning.EXPERT_MAX }

    /** Parse what the developer typed into a playable level, or null. */
    fun parseLevel(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 1..MAX_LEVEL }
}
