package com.clevercorner.arrowescape.ui.dev

/**
 * Level-number arithmetic for the developer picker.
 *
 * The structure itself (which shape owns which levels) comes from
 * [com.clevercorner.arrowescape.game.curve.CurvedJourney]; this only answers
 * the picker's own question about free-text input.
 */
object DevLevels {

    const val MAX_LEVEL = 9999

    /** Parse what the developer typed into a playable level, or null. */
    fun parseLevel(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in 1..MAX_LEVEL }
}
