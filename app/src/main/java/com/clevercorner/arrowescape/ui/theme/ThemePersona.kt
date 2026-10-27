package com.clevercorner.arrowescape.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R

/** The hero animation a theme paints behind Home. */
enum class HomeMotif { ARROWS, MOON, SUN, SAILS, INK }

/**
 * Everything that makes a theme feel like *itself* beyond its colours: the
 * home screen's motif, its motto, its corners, and its voice.
 */
data class ThemePersona(
    @StringRes val taglineRes: Int,
    val crest: String,
    val motif: HomeMotif,
    /** Small glyphs drifting behind the motif. */
    val motes: List<String>,
    val moteCount: Int,
    /** Milliseconds for one mote to cross the screen - the theme's tempo. */
    val driftMillis: Int,
    val driftDown: Boolean,
    val corner: Dp,
    val glow: Color,
    val sounds: SoundPack,
    /** The daily challenge's header: the sky art behind it and the inks that read on it. */
    val dailyHeader: DailyHeaderStyle
)

/**
 * How the daily challenge's header looks in one theme. The inks are chosen
 * per theme because each sky is different - a dark night needs light text.
 */
data class DailyHeaderStyle(
    @DrawableRes val art: Int,
    val ink: Color,
    val mutedInk: Color,
    val accent: Color,
    /** Translucent surface behind the back button, chip and stat cards. */
    val glass: Color
)

/** The three clips a theme speaks with. */
data class SoundPack(
    @RawRes val success: Int,
    @RawRes val error: Int,
    @RawRes val complete: Int
)

fun AppTheme.persona(): ThemePersona = when (this) {
    AppTheme.CLASSIC -> ClassicPersona
    AppTheme.MIDNIGHT -> MidnightPersona
    AppTheme.SUNSET -> SunsetPersona
    AppTheme.PIRATE -> PiratePersona
    AppTheme.MONO -> MonoPersona
}

fun AppTheme.soundPack(): SoundPack = persona().sounds

private val ClassicPersona = ThemePersona(
    taglineRes = R.string.persona_classic_tagline,
    crest = "\u2192",
    motif = HomeMotif.ARROWS,
    motes = listOf("\u2191", "\u2192", "\u2193", "\u2190"),
    moteCount = 10,
    driftMillis = 14_000,
    driftDown = false,
    corner = 18.dp,
    glow = Color(0xFF1A73E8),
    sounds = SoundPack(R.raw.tap_success,         R.raw.tap_error,
        R.raw.level_complete
    ),
    dailyHeader = DailyHeaderStyle(
        art = R.drawable.theme_bg_classic,
        ink = Color(0xFF1B2A44),
        mutedInk = Color(0xFF4A5568),
        accent = Color(0xFF1A73E8),
        glass = Color(0xE6FFFFFF)
    )
)

private val MidnightPersona = ThemePersona(
    taglineRes = R.string.persona_midnight_tagline,
    crest = "\u263D",
    motif = HomeMotif.MOON,
    motes = listOf("\u00B7", "\u2726", "\u2727"),
    moteCount = 22,
    driftMillis = 26_000,
    driftDown = true,
    corner = 22.dp,
    glow = Color(0xFF6C8CFF),
    sounds = SoundPack(
        R.raw.midnight_tap_success,
        R.raw.midnight_tap_error,
        R.raw.midnight_level_complete
    ),
    dailyHeader = DailyHeaderStyle(
        art = R.drawable.theme_bg_midnight,
        ink = Color(0xFFF2F6FF),
        mutedInk = Color(0xFFB8C6E6),
        accent = Color(0xFF9DB4FF),
        glass = Color(0xB3101A3A)
    )
)

private val SunsetPersona = ThemePersona(
    taglineRes = R.string.persona_sunset_tagline,
    crest = "\u2600",
    motif = HomeMotif.SUN,
    motes = listOf("\u25CF", "\u25CB"),
    moteCount = 10,
    driftMillis = 19_000,
    driftDown = false,
    corner = 14.dp,
    glow = Color(0xFFFF8A3D),
    sounds = SoundPack(
        R.raw.sunset_tap_success,
        R.raw.sunset_tap_error,
        R.raw.sunset_level_complete
    ),
    dailyHeader = DailyHeaderStyle(
        art = R.drawable.theme_bg_sunset,
        ink = Color(0xFF1B2A44),
        mutedInk = Color(0xFF4A5568),
        accent = Color(0xFFB4530B),
        glass = Color(0xE6FFFFFF)
    )
)

private val PiratePersona = ThemePersona(
    taglineRes = R.string.persona_pirate_tagline,
    crest = "\u2620",
    motes = listOf("\u2693", "\u2726", "\u25CF"),
    motif = HomeMotif.SAILS,
    moteCount = 8,
    driftMillis = 16_000,
    driftDown = false,
    corner = 10.dp,
    glow = Color(0xFF0E5055),
    sounds = SoundPack(
        R.raw.pirate_tap_success,
        R.raw.pirate_tap_error,
        R.raw.pirate_level_complete
    ),
    dailyHeader = DailyHeaderStyle(
        art = R.drawable.theme_bg_pirate,
        ink = Color(0xFF1E2A2E),
        mutedInk = Color(0xFF3F4F52),
        accent = Color(0xFF9A5B13),
        glass = Color(0xE0FFFFFF)
    )
)

private val MonoPersona = ThemePersona(
    taglineRes = R.string.persona_mono_tagline,
    crest = "\u25C6",
    motif = HomeMotif.INK,
    motes = listOf("\u25C6", "\u25C7", "\u25CF", "\u25CB"),
    moteCount = 8,
    driftMillis = 20_000,
    driftDown = true,
    corner = 6.dp,
    glow = Color(0xFF14110F),
    sounds = SoundPack(
        R.raw.mono_tap_success,
        R.raw.mono_tap_error,
        R.raw.mono_level_complete
    ),
    dailyHeader = DailyHeaderStyle(
        art = R.drawable.theme_bg_mono,
        ink = Color(0xFF14110F),
        mutedInk = Color(0xFF4A4540),
        accent = Color(0xFF5A5149),
        glass = Color(0xE0FFFFFF)
    )
)
