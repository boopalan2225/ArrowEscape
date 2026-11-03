package com.clevercorner.arrowescape.ui.daily

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.ui.theme.LocalThemePersona

/** How much of the top/bottom the legibility scrim covers. */
private val ScrimHeight = 230.dp

/** Softens the photo without hiding it - just enough to keep it a backdrop, not the thing to look at. */
private val BackdropBlur = 0.dp

/**
 * The daily challenge's whole scene - one theme-specific photo behind the
 * header, the board and the controls alike, instead of a decorated header
 * sitting above a plain body.
 *
 * The art is softened with a blur: this chrome sets a mood, it is not the
 * thing to look at, and a crisp photo would compete with the puzzle for
 * attention. Two scrims fade in from the top and bottom edges - just enough
 * to keep the date and the action row legible - while the middle, where the
 * board floats, is left clearest so the scene still reads through its glass.
 */
@Composable
fun DailyBackdrop(modifier: Modifier = Modifier) {
    val style = LocalThemePersona.current.dailyHeader

    Box(modifier = modifier) {
        Image(
            painter = painterResource(style.art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // The art is a tall portrait, taller than most phone screens.
            // Biasing to the top keeps the sky - sun, moon or mast - in
            // frame instead of losing it to a bottom-anchored crop.
            alignment = BiasAlignment(horizontalBias = 0f, verticalBias = -1f),
            modifier = Modifier
                .matchParentSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScrimHeight)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(style.scrim.copy(alpha = 0.55f), Color.Transparent)
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScrimHeight)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, style.scrim.copy(alpha = 0.6f))
                    )
                )
        )
    }
}
