package com.clevercorner.arrowescape.data

import androidx.annotation.StringRes
import com.clevercorner.arrowescape.R

/**
 * The two games that ship in Arrow Escape.
 *
 * Each mode has its own shapes, its own journey length and - crucially - its
 * own saved progress, so switching modes never costs a player their place.
 * Everything that used to branch on a `curvedMode` boolean should take one of
 * these instead: a boolean cannot name itself, and cannot grow a third option.
 */
enum class GameMode(
    /** Stable key persisted in preferences - never rename once shipped. */
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val taglineRes: Int,
    val emoji: String
) {
    CURVED(
        id = "curved",
        labelRes = R.string.mode_curved_label,
        taglineRes = R.string.mode_curved_tagline,
        emoji = "\uD83C\uDF00"
    ),
    CLASSIC(
        id = "grid",
        labelRes = R.string.mode_classic_label,
        taglineRes = R.string.mode_classic_tagline,
        emoji = "\u25A6"
    );

    companion object {
        val DEFAULT = CURVED

        fun fromId(id: String?): GameMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
