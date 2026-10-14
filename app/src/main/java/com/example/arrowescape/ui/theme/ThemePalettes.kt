package com.example.arrowescape.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Material colours for each theme. */
fun AppTheme.colorScheme(): ColorScheme = when (this) {
    AppTheme.CLASSIC -> ClassicColorScheme
    AppTheme.MIDNIGHT -> MidnightColorScheme
    AppTheme.FOREST -> ForestColorScheme
    AppTheme.SUNSET -> SunsetColorScheme
}

/** Game-specific colours (board, cards, arrows) for each theme. */
fun AppTheme.gameColors(): GameColors = when (this) {
    AppTheme.CLASSIC -> ClassicGameColors
    AppTheme.MIDNIGHT -> MidnightGameColors
    AppTheme.FOREST -> ForestGameColors
    AppTheme.SUNSET -> SunsetGameColors
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

// ── Forest: soft mint and leaf green ────────────────────────────────────

private val ForestColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF0B2E10),
    secondary = Color(0xFF558B2F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF0B2E10),
    background = Color(0xFFF1F7F1),
    onBackground = Color(0xFF15251A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15251A),
    surfaceVariant = Color(0xFFE1EEE2),
    error = ErrorLight,
    onError = Color.White,
    outline = Color(0xFFC5D8C7),
    outlineVariant = Color(0xFFC5D8C7),
    scrim = ScrimLight
)

private val ForestGameColors = ClassicGameColors.copy(
    arrowPrimary = Color(0xFF2E7D32),
    arrowSecondary = Color(0xFF66BB6A),
    boardBackground = Color(0xFFE6F2E7),
    gridLine = Color(0xFFCFE3D1),
    textSecondary = Color(0xFF4F6B55),
    divider = Color(0xFFC5D8C7)
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
