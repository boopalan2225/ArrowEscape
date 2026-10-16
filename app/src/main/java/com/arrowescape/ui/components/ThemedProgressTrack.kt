package com.arrowescape.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * A progress track that wears the current theme: the fill uses the theme's
 * glow and the thumb is the theme's crest, so the pirate sails toward the
 * horizon while Ink just advances a black square.
 */
@Composable
fun ThemedProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    showThumb: Boolean = true
) {
    val persona = ArrowEscapeTheme.persona
    val gameColors = ArrowEscapeTheme.gameColors
    val clamped = fraction.coerceIn(0f, 1f)
    val thumbSize = if (showThumb) height * 2.4f else height

    BoxWithConstraints(modifier.fillMaxWidth().height(thumbSize)) {
        val travel = maxWidth - thumbSize

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(gameColors.divider)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(clamped)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(
                        Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary, persona.glow)
                        )
                    )
            )
        }

        if (showThumb) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = travel * clamped)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(
                    glyph = persona.crest,
                    fontSize = (height.value * 1.5f).sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
