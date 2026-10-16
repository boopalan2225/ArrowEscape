package com.example.arrowescape.ui.milestone

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.ui.theme.ArrowEscapeTheme
import kotlin.math.sin

/**
 * The full stop at the end of the Journey: a shimmering promise that the map
 * keeps growing.
 *
 * It exists so finishing the last chapter reads as "you are up to date"
 * instead of "that's all there is" - a very different feeling for about
 * eighty lines of wallpaper.
 */
@Composable
fun ComingSoonCard(
    nextChapter: Int,
    teasers: List<String>,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val accent = ArrowEscapeTheme.persona.glow
    val shape = RoundedCornerShape(20.dp)

    Surface(
        shape = shape,
        color = gameColors.cardBackground,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }

            Box(Modifier.fillMaxWidth().background(sweep(accent, widthPx))) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ComingSoonBadge(accent)

                    Spacer(Modifier.height(14.dp))

                    BouncingTeasers(teasers)

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Chapter $nextChapter is in the workshop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "You have cleared everything we have built so far. " +
                            "New shapes and trickier escapes are on the way - " +
                            "keep the app updated so they land the day they ship.",
                        style = MaterialTheme.typography.bodySmall,
                        color = gameColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** A highlight that slides across the card forever - the "under construction" cue. */
@Composable
private fun sweep(accent: Color, widthPx: Float): Brush {
    val infinite = rememberInfiniteTransition(label = "comingSoon")
    val position by infinite.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sweep"
    )

    return Brush.linearGradient(
        colors = listOf(Color.Transparent, accent.copy(alpha = 0.18f), Color.Transparent),
        start = Offset(position * widthPx, 0f),
        end = Offset((position + 0.45f) * widthPx, widthPx * 0.3f)
    )
}

@Composable
private fun ComingSoonBadge(accent: Color) {
    val infinite = rememberInfiniteTransition(label = "badge")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f * pulse + 0.06f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("\uD83D\uDEA7", fontSize = 13.sp)
        Text(
            text = "COMING SOON",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = accent
        )
    }
}

/** Mystery shapes hopping in a wave, each one a beat behind the last. */
@Composable
private fun BouncingTeasers(teasers: List<String>) {
    val infinite = rememberInfiniteTransition(label = "teasers")
    val wave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "wave"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        teasers.forEachIndexed { index, glyph ->
            val hop = sin(wave - index * 0.7f).coerceAtLeast(0f)
            Text(
                text = glyph,
                fontSize = 24.sp,
                modifier = Modifier
                    .offset(y = (-10 * hop).dp)
                    .height(34.dp)
            )
        }
    }
}
