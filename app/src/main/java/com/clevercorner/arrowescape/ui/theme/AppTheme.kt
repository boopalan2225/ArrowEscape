package com.clevercorner.arrowescape.ui.theme

/**
 * The app's selectable looks.
 *
 * Only identity lives here; the colours are in `ThemePalettes.kt`, so adding a
 * theme is one entry here plus one palette there.
 */
enum class AppTheme(
    /** Stable key persisted in preferences - never rename once shipped. */
    val id: String,
    val label: String,
    val isDark: Boolean
) {
    CLASSIC("classic", "Classic", isDark = false),
    MIDNIGHT("midnight", "Midnight", isDark = true),
    SUNSET("sunset", "Sunset", isDark = false),
    PIRATE("pirate", "Pirate", isDark = false),
    MONO("mono", "Ink", isDark = false);

    companion object {
        /**
         * Saved when the player wants the app to follow the phone. It is
         * deliberately *not* an enum entry: "system" is a rule for picking a
         * look, not a look, and [resolve] already treats anything unknown as
         * "match the phone".
         */
        const val SYSTEM_ID = "system"

        fun fromId(id: String?): AppTheme? = entries.firstOrNull { it.id == id }

        /** True when [savedId] means "follow the phone" rather than a fixed look. */
        fun followsSystem(savedId: String?): Boolean = fromId(savedId) == null

        /** What a player who never chose a theme gets: light or dark to match their phone. */
        fun defaultFor(systemDark: Boolean): AppTheme = if (systemDark) MIDNIGHT else CLASSIC

        /** The saved choice, or the phone-matching default for anyone who has not picked. */
        fun resolve(savedId: String?, systemDark: Boolean): AppTheme =
            fromId(savedId) ?: defaultFor(systemDark)
    }
}
