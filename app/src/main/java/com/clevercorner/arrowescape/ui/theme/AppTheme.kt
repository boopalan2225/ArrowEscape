package com.clevercorner.arrowescape.ui.theme

import androidx.annotation.StringRes
import com.clevercorner.arrowescape.R

/**
 * The app's selectable looks.
 *
 * Only identity lives here; the colours are in `ThemePalettes.kt`, so adding a
 * theme is one entry here plus one palette there.
 *
 * [isFree] is the only thing the economy needs from a theme. What a *paid*
 * one costs is not written here on purpose: the price depends on how many
 * the player already owns, not on which they fancy (see
 * `economy.CoinRules.unlockPrice`). Light and dark are never for sale - a
 * player must always be able to match their phone and their eyes.
 */
enum class AppTheme(
    /** Stable key persisted in preferences - never rename once shipped. */
    val id: String,
    @StringRes val labelRes: Int,
    val isDark: Boolean,
    /** False for the looks that must be unlocked with coins. */
    val isFree: Boolean = false
) {
    CLASSIC("classic", R.string.theme_classic_label, isDark = false, isFree = true),
    MIDNIGHT("midnight", R.string.theme_midnight_label, isDark = true, isFree = true),
    SUNSET("sunset", R.string.theme_sunset_label, isDark = false),
    PIRATE("pirate", R.string.theme_pirate_label, isDark = false),
    MONO("mono", R.string.theme_mono_label, isDark = false);

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

        /**
         * The saved choice, downgraded to a free look if it is not owned.
         *
         * Belt and braces: the picker will not let anyone select a locked
         * theme, but a wallet restored from a cloud save with fewer unlocks
         * than the device had could otherwise leave a player staring at a
         * paid theme they no longer own. Falling back to the phone's own
         * light or dark is the quiet, correct answer.
         */
        fun owned(savedId: String?, systemDark: Boolean, unlocked: Set<String>): AppTheme {
            val chosen = resolve(savedId, systemDark)
            return if (chosen.isFree || chosen.id in unlocked) chosen else defaultFor(systemDark)
        }
    }
}
