package com.example.arrowescape.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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

val LocalGameColors = compositionLocalOf { ClassicGameColors }

/** Applies [theme] to everything below it. */
@Composable
fun ArrowEscapeTheme(
    theme: AppTheme,
    content: @Composable () -> Unit
) {
    val colorScheme = theme.colorScheme()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !theme.isDark
                isAppearanceLightNavigationBars = !theme.isDark
            }
        }
    }

    CompositionLocalProvider(LocalGameColors provides theme.gameColors()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

/** Light or dark default look - for previews and anywhere no theme was chosen. */
@Composable
fun ArrowEscapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = ArrowEscapeTheme(AppTheme.defaultFor(darkTheme), content)

// Extension to easily access game colors
object ArrowEscapeTheme {
    val gameColors: GameColors
        @Composable
        get() = LocalGameColors.current
}
