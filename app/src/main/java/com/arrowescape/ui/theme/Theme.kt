package com.arrowescape.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
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
val LocalThemePersona = compositionLocalOf { AppTheme.CLASSIC.persona() }

/** How long a theme switch takes to wash across the screen. */
private const val THEME_FADE_MS = 450

/** Applies [theme] to everything below it. */
@Composable
fun ArrowEscapeTheme(
    theme: AppTheme,
    content: @Composable () -> Unit
) {
    val persona = theme.persona()
    val colorScheme = theme.colorScheme().animated()

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

    CompositionLocalProvider(
        LocalGameColors provides theme.gameColors(),
        LocalThemePersona provides persona
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = remember(persona.corner) { shapesFor(persona.corner) },
            content = content
        )
    }
}

/**
 * Swapping themes mid-session should feel like a sunrise, not a light switch,
 * so the big flat areas cross-fade. Small accents are left alone: nobody
 * tracks a 14dp dot's colour, and animating all thirty roles costs more than
 * it shows.
 */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    @Composable
    fun fade(target: Color, label: String) =
        animateColorAsState(target, tween(THEME_FADE_MS), label = label).value

    return copy(
        primary = fade(primary, "primary"),
        primaryContainer = fade(primaryContainer, "primaryContainer"),
        background = fade(background, "background"),
        onBackground = fade(onBackground, "onBackground"),
        surface = fade(surface, "surface"),
        onSurface = fade(onSurface, "onSurface"),
        surfaceVariant = fade(surfaceVariant, "surfaceVariant")
    )
}

/** Each theme carries its own roundness, all derived from one number. */
private fun shapesFor(corner: Dp) = Shapes(
    extraSmall = RoundedCornerShape(corner * 0.25f),
    small = RoundedCornerShape(corner * 0.5f),
    medium = RoundedCornerShape(corner * 0.75f),
    large = RoundedCornerShape(corner),
    extraLarge = RoundedCornerShape(corner * 1.5f)
)

/** Light or dark default look - for previews and anywhere no theme was chosen. */
@Composable
fun ArrowEscapeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = ArrowEscapeTheme(AppTheme.defaultFor(darkTheme), content)

// Extension to easily access game colors and the live theme's personality
object ArrowEscapeTheme {
    val gameColors: GameColors
        @Composable
        get() = LocalGameColors.current

    val persona: ThemePersona
        @Composable
        get() = LocalThemePersona.current
}
