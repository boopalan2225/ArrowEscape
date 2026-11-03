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
 * How the daily challenge's whole screen looks in one theme - the scene
 * behind header, board and controls alike, and the inks that read on it.
 * The inks are chosen per theme because each sky is different - a dark
 * night needs light text.
 */
data class DailyHeaderStyle(
    /** The full-bleed scene behind the entire screen, not just the header. */
    @DrawableRes val art: Int,
    val ink: Color,
    val mutedInk: Color,
    val accent: Color,
    /** Translucent surface for cards, pills and the board's glass pane. */
    val glass: Color,
    /** Edge-fade colour behind the header and action row, chosen so [ink] stays readable over the photo. */
    val scrim: Color,
    /**
     * An ornate plaque drawn under the board's arrows instead of a flat
     * tinted glass pane - a theme can give the puzzle its own table, not
     * just its own sky. Null keeps the existing whisper-of-glass look.
     *
     * The art is a square asset; the board itself is not (its canvas is a
     * fixed 2:3 rectangle, so every level's curves stay correctly
     * proportioned). Rather than stretch the plaque's corners into ovals or
     * crop its edges off to force a fit, it is shown whole and centred; the
     * existing whisper-of-glass pane still fills the rest of the board, so
     * any sliver left over top or bottom still reads as this scene's glass,
     * not a seam - and it works on any device's aspect ratio.
     */
    @DrawableRes val boardArt: Int? = null,
    /**
     * Recolours every arrow onto this theme's own two-stop gradient, so an
     * owl's tan belly and a yin-yang's ink swirl both read as one theme's
     * palette on the daily screen. Null keeps each shape's own baked colours.
     */
    val arrowDuotone: Pair<Color, Color>? = null
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
        art = R.drawable.daily_bg_classic,
        ink = Color(0xFF1B2A44),
        mutedInk = Color(0xFF4A5568),
        accent = Color(0xFF1A73E8),
        glass = Color(0xE6FFFFFF),
        scrim = Color.White,
        boardArt = R.drawable.daily_board_classic,
        arrowDuotone = Color(0xFF123B66) to Color(0xFFBEE6FF)
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
        art = R.drawable.daily_bg_midnight,
        ink = Color(0xFFF2F6FF),
        mutedInk = Color(0xFFB8C6E6),
        accent = Color(0xFF9DB4FF),
        glass = Color(0xB3101A3A),
        scrim = Color.Black
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
        art = R.drawable.daily_bg_sunset,
        ink = Color(0xFF1B2A44),
        mutedInk = Color(0xFF4A5568),
        accent = Color(0xFFB4530B),
        glass = Color(0xE6FFFFFF),
        scrim = Color.White
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
        art = R.drawable.daily_bg_pirate,
        ink = Color(0xFF1E2A2E),
        mutedInk = Color(0xFF3F4F52),
        accent = Color(0xFF9A5B13),
        glass = Color(0xE0FFFFFF),
        // Warm ivory, not pure white - a light edge-fade that still reads
        // as this theme's sunset, dark ink needs the lift for contrast.
        scrim = Color(0xFFFFF2DC)
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
        art = R.drawable.daily_bg_mono,
        ink = Color(0xFF14110F),
        mutedInk = Color(0xFF4A4540),
        accent = Color(0xFF5A5149),
        glass = Color(0xE0FFFFFF),
        scrim = Color(0xFFF4EFE6)
    )
)
