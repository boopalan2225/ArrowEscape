package com.clevercorner.arrowescape.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Material colours for each theme. */
fun AppTheme.colorScheme(): ColorScheme = when (this) {
    AppTheme.CLASSIC -> ClassicColorScheme
    AppTheme.MIDNIGHT -> MidnightColorScheme
    AppTheme.SUNSET -> SunsetColorScheme
    AppTheme.PIRATE -> PirateColorScheme
    AppTheme.MONO -> MonoColorScheme
}

/** Game-specific colours (board, cards, arrows) for each theme. */
fun AppTheme.gameColors(): GameColors = when (this) {
    AppTheme.CLASSIC -> ClassicGameColors
    AppTheme.MIDNIGHT -> MidnightGameColors
    AppTheme.SUNSET -> SunsetGameColors
    AppTheme.PIRATE -> PirateGameColors
    AppTheme.MONO -> MonoGameColors
}

// ── Classic: the original light look ────────────────────────────────────

private val ClassicColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = PrimaryContainer,
    onSecondaryContainer = OnPrimaryContainer,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    error = ErrorLight,
    onError = Color.White,
    outline = DividerLight,
    outlineVariant = DividerLight,
    scrim = ScrimLight
)

internal val ClassicGameColors = GameColors(
    arrowUp = ArrowColors.UpLight,
    arrowDown = ArrowColors.DownLight,
    arrowLeft = ArrowColors.LeftLight,
    arrowRight = ArrowColors.RightLight,
    arrowPrimary = ArrowGrad1Light,
    arrowSecondary = ArrowGrad2Light,
    arrowHint = ArrowHintAmber,
    heartFull = HeartFull,
    heartEmpty = HeartEmpty,
    boardBackground = BoardBackgroundLight,
    gridLine = GridLineLight,
    textSecondary = TextSecondaryLight,
    cardBackground = CardLight,
    divider = DividerLight,
    success = SuccessLight,
    warning = WarningLight
)

// ── Midnight: the original dark look ────────────────────────────────────

private val MidnightColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = SecondaryDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF004A77),
    onSecondaryContainer = Color(0xFFD3E3FD),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    error = ErrorDark,
    onError = Color.Black,
    outline = DividerDark,
    outlineVariant = DividerDark,
    scrim = ScrimDark
)

private val MidnightGameColors = GameColors(
    arrowUp = ArrowColors.UpDark,
    arrowDown = ArrowColors.DownDark,
    arrowLeft = ArrowColors.LeftDark,
    arrowRight = ArrowColors.RightDark,
    arrowPrimary = ArrowGrad1Dark,
    arrowSecondary = ArrowGrad2Dark,
    arrowHint = ArrowHintAmber,
    heartFull = HeartFullDark,
    heartEmpty = HeartEmptyDark,
    boardBackground = BoardBackgroundDark,
    gridLine = GridLineDark,
    textSecondary = TextSecondaryDark,
    cardBackground = CardDark,
    divider = DividerDark,
    success = SuccessDark,
    warning = WarningDark
)

// ── Pirate: parchment, deep sea and gold ────────────────────────────────

private val PirateColorScheme = lightColorScheme(
    primary = Color(0xFF0E5055),
    onPrimary = Color(0xFFF7ECD2),
    primaryContainer = Color(0xFFE2CCA0),
    onPrimaryContainer = Color(0xFF2B1E12),
    secondary = Color(0xFFB07B13),
    onSecondary = Color(0xFFFFF8E8),
    secondaryContainer = Color(0xFFE2CCA0),
    onSecondaryContainer = Color(0xFF2B1E12),
    background = Color(0xFFF5E7C8),
    onBackground = Color(0xFF2B1E12),
    surface = Color(0xFFFCF3DF),
    onSurface = Color(0xFF2B1E12),
    surfaceVariant = Color(0xFFEADCBB),
    error = Color(0xFF9B2226),
    onError = Color.White,
    outline = Color(0xFFC9B088),
    outlineVariant = Color(0xFFC9B088),
    scrim = ScrimLight
)

private val PirateGameColors = ClassicGameColors.copy(
    arrowPrimary = Color(0xFF0E5055),
    arrowSecondary = Color(0xFFB07B13),
    boardBackground = Color(0xFFEFDFBC),
    gridLine = Color(0xFFD9C097),
    textSecondary = Color(0xFF6B5636),
    cardBackground = Color(0xFFFCF3DF),
    divider = Color(0xFFC9B088),
    warning = Color(0xFFB07B13)
)

// ── Ink: pure black on white, nothing else ──────────────────────────────

private val MonoColorScheme = lightColorScheme(
    primary = Color(0xFF14110F),
    onPrimary = Color(0xFFFBFAF7),
    primaryContainer = Color(0xFFE7E4DD),
    onPrimaryContainer = Color(0xFF14110F),
    secondary = Color(0xFF4A4642),
    onSecondary = Color(0xFFFBFAF7),
    secondaryContainer = Color(0xFFE7E4DD),
    onSecondaryContainer = Color(0xFF14110F),
    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF14110F),
    surface = Color(0xFFFFFFFE),
    onSurface = Color(0xFF14110F),
    surfaceVariant = Color(0xFFEFEDE7),
    error = Color(0xFF14110F),
    onError = Color(0xFFFBFAF7),
    outline = Color(0xFFD4D0C8),
    outlineVariant = Color(0xFFD4D0C8),
    scrim = ScrimLight
)

private val MonoGameColors = GameColors(
    arrowUp = Color(0xFF14110F),
    arrowDown = Color(0xFF454039),
    arrowLeft = Color(0xFF75706A),
    arrowRight = Color(0xFFA8A49C),
    arrowPrimary = Color(0xFF14110F),
    arrowSecondary = Color(0xFF6B665F),
    arrowHint = Color(0xFF8F8A82),
    heartFull = Color(0xFF14110F),
    heartEmpty = Color(0xFFDDD9D1),
    boardBackground = Color(0xFFF4F2EC),
    gridLine = Color(0xFFDDD9D1),
    textSecondary = Color(0xFF6B665F),
    cardBackground = Color(0xFFFFFFFE),
    divider = Color(0xFFDDD9D1),
    success = Color(0xFF14110F),
    warning = Color(0xFF4A4642)
)

// ── Sunset: warm cream and burnt orange ─────────────────────────────────

private val SunsetColorScheme = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF3A1500),
    secondary = Color(0xFF8E3B46),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF3A1500),
    background = Color(0xFFFFF7F0),
    onBackground = Color(0xFF2B1A12),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B1A12),
    surfaceVariant = Color(0xFFFBE8D9),
    error = ErrorLight,
    onError = Color.White,
    outline = Color(0xFFEBCDB5),
    outlineVariant = Color(0xFFEBCDB5),
    scrim = ScrimLight
)

private val SunsetGameColors = ClassicGameColors.copy(
    arrowPrimary = Color(0xFFE65100),
    arrowSecondary = Color(0xFFFFA040),
    boardBackground = Color(0xFFFCEBDD),
    gridLine = Color(0xFFF0D5C0),
    textSecondary = Color(0xFF7A5848),
    divider = Color(0xFFEBCDB5)
)
