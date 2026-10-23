package com.clevercorner.arrowescape.ui.theme

import androidx.annotation.RawRes
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
    val tagline: String,
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
    val sounds: SoundPack
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
    tagline = "TAP  \u00B7  VANISH  \u00B7  ESCAPE",
    crest = "\u2192",
    motif = HomeMotif.ARROWS,
    motes = listOf("\u2191", "\u2192", "\u2193", "\u2190"),
    moteCount = 10,
    driftMillis = 14_000,
    driftDown = false,
    corner = 18.dp,
    glow = Color(0xFF1A73E8),
    sounds = SoundPack(R.raw.tap_success, R.raw.tap_error, R.raw.level_complete)
)

private val MidnightPersona = ThemePersona(
    tagline = "QUIET HOURS  \u00B7  CLEAR HEAD",
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
    )
)

private val SunsetPersona = ThemePersona(
    tagline = "GOLDEN HOUR  \u00B7  LAST SHOT",
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
    )
)

private val PiratePersona = ThemePersona(
    tagline = "CHART IT  \u00B7  CLAIM IT  \u00B7  SAIL ON",
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
    )
)

private val MonoPersona = ThemePersona(
    tagline = "INK  \u00B7  PAPER  \u00B7  PRECISION",
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
    )
)
