package com.example.arrowescape.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Custom color extensions for game-specific colors
data class GameColors(
    val arrowUp: Color,
    val arrowDown: Color,
    val arrowLeft: Color,
    val arrowRight: Color,
    val arrowPrimary: Color,
    val arrowSecondary: Color,
    val arrowHint: Color,
    val heartFull: Color,
    val heartEmpty: Color,
    val boardBackground: Color,
    val gridLine: Color,
    val textSecondary: Color,
    val cardBackground: Color,
    val divider: Color,
    val success: Color,
    val warning: Color
)

val LocalGameColors = compositionLocalOf {
    GameColors(
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
}

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = SecondaryLight,
    onSecondary = Color.White,
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

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = SecondaryDark,
    onSecondary = Color.Black,
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

private val LightGameColors = GameColors(
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

private val DarkGameColors = GameColors(
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

@Composable
fun ArrowEscapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to true to use Material You on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val gameColors = if (darkTheme) DarkGameColors else LightGameColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalGameColors provides gameColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

// Extension to easily access game colors
object ArrowEscapeTheme {
    val gameColors: GameColors
        @Composable
        get() = LocalGameColors.current
}
