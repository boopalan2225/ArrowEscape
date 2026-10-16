package com.example.arrowescape.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.ui.theme.ArrowEscapeTheme
import com.example.arrowescape.ui.theme.ThemePersona
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The living wallpaper behind Home: a themed wash, two slow glow blobs, and a
 * field of drifting glyphs.
 *
 * There is exactly one of these for every theme - what changes is the
 * [ThemePersona] data, never the code - so arrows soar on Classic, stars
 * settle on Midnight, leaves fall on Forest and embers rise on Sunset without
 * a single `when (theme)` in sight.
 */
@Composable
fun HomeBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val persona = ArrowEscapeTheme.persona

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        persona.glow.copy(alpha = 0.10f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        GlowBlobs(persona)
        MoteField(persona)
        content()
    }
}

/** Two soft, slowly breathing colour pools - depth without a texture file. */
@Composable
private fun GlowBlobs(persona: ThemePersona) {
    val infinite = rememberInfiniteTransition(label = "blobs")
    val sway by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(persona.driftMillis / 2, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "sway"
    )

    Blob(persona, sizeDp = 260, xDp = (-70 + sway * 20).toInt(), yDp = (40 - sway * 30).toInt(), alpha = 0.22f)
    Blob(persona, sizeDp = 200, xDp = (210 - sway * 25).toInt(), yDp = (420 + sway * 35).toInt(), alpha = 0.16f)
}

@Composable
private fun Blob(persona: ThemePersona, sizeDp: Int, xDp: Int, yDp: Int, alpha: Float) {
    Box(
        Modifier
            .offset(x = xDp.dp, y = yDp.dp)
            .size(sizeDp.dp)
            .blur(60.dp)
            .clip(CircleShape)
            .background(persona.glow.copy(alpha = alpha))
    )
}

/**
 * All motes share one animation clock and differ only by phase, so twenty
 * glyphs cost one running animation rather than twenty.
 */
@Composable
private fun MoteField(persona: ThemePersona) {
    val motes = remember(persona) { motesFor(persona) }
    val infinite = rememberInfiniteTransition(label = "motes")
    val clock by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(persona.driftMillis, easing = LinearEasing)),
        label = "drift"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight

        motes.forEach { mote ->
            // Each mote runs the same 0..1 lap, just started at its own point.
            val lap = (clock + mote.phase) % 1f
            val travelled = if (persona.driftDown) lap else 1f - lap

            Text(
                text = mote.glyph,
                fontSize = mote.size.sp,
                color = persona.glow,
                modifier = Modifier
                    .offset(x = width * mote.x, y = height * travelled - height * 0.1f)
                    .rotate(mote.spin * lap * 360f)
                    // Fade in and out at the edges so nothing pops into view.
                    .alpha(mote.alpha * sin(PI * lap).toFloat())
            )
        }
    }
}

private data class Mote(
    val glyph: String,
    val x: Float,
    val size: Float,
    val phase: Float,
    val spin: Float,
    val alpha: Float
)

/** Fixed seed: the scenery is scattered, but the same scatter every launch. */
private fun motesFor(persona: ThemePersona): List<Mote> {
    val random = Random(persona.tagline.hashCode())
    return List(persona.moteCount) { index ->
        Mote(
            glyph = persona.motes[index % persona.motes.size],
            x = random.nextFloat() * 0.92f,
            size = 10f + random.nextFloat() * 14f,
            phase = random.nextFloat(),
            spin = if (persona.driftDown) random.nextFloat() * 0.6f else 0f,
            alpha = 0.18f + random.nextFloat() * 0.3f
        )
    }
}
