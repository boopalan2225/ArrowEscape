package com.example.arrowescape.ui.theme

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
    FOREST("forest", "Forest", isDark = false),
    SUNSET("sunset", "Sunset", isDark = false);

    companion object {
        fun fromId(id: String?): AppTheme? = entries.firstOrNull { it.id == id }

        /** What a player who never chose a theme gets: light or dark to match their phone. */
        fun defaultFor(systemDark: Boolean): AppTheme = if (systemDark) MIDNIGHT else CLASSIC

        /** The saved choice, or the phone-matching default for anyone who has not picked. */
        fun resolve(savedId: String?, systemDark: Boolean): AppTheme =
            fromId(savedId) ?: defaultFor(systemDark)
    }
}
