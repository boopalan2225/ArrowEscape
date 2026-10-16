package com.arrowescape.data

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
    val label: String,
    val tagline: String,
    val emoji: String
) {
    CURVED(
        id = "curved",
        label = "Curved",
        tagline = "Flowing bezier paths",
        emoji = "\uD83C\uDF00"
    ),
    CLASSIC(
        id = "grid",
        label = "Classic",
        tagline = "Straight arrows on a grid",
        emoji = "\u25A6"
    );

    companion object {
        val DEFAULT = CURVED

        fun fromId(id: String?): GameMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
