package com.clevercorner.arrowescape.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** How long one burst takes to fall off the bottom of the screen. */
private const val BURST_SECONDS = 3.5f

/** Paper only: the party stops before the player wants to tap "Next". */
private const val FADE_START = 0.75f

object ConfettiDefaults {
    /** Bright enough to read on both the light and the dark themes. */
    val colors = listOf(
        Color(0xFFFF4F79),
        Color(0xFFFFC53D),
        Color(0xFF3DDC97),
        Color(0xFF4D9BFF),
        Color(0xFFB36BFF),
        Color(0xFFFF8A3D)
    )
}

/**
 * One scrap of paper, described once and then replayed by maths.
 *
 * Every value is a fraction or a rate, never a pixel: the same piece looks
 * right on a watch-sized preview and on a tablet, and nothing has to be
 * recreated when the canvas is measured.
 */
private data class ConfettiPiece(
    val startX: Float,       // 0..1 across the canvas
    val startY: Float,       // negative = above the top edge, staggers entry
    val fallSpeed: Float,    // canvas heights per second
    val swayAmplitude: Float,
    val swaySpeed: Float,
    val swayPhase: Float,
    val spinSpeed: Float,
    val flipSpeed: Float,
    val widthDp: Float,
    val aspect: Float,
    val color: Color
) {
    companion object {
        fun random(random: Random, colors: List<Color>) = ConfettiPiece(
            startX = random.nextFloat(),
            startY = -random.nextFloat() * 1.2f,
            fallSpeed = 0.35f + random.nextFloat() * 0.45f,
            swayAmplitude = 0.01f + random.nextFloat() * 0.05f,
            swaySpeed = 1.5f + random.nextFloat() * 2.5f,
            swayPhase = random.nextFloat() * TWO_PI,
            spinSpeed = (random.nextFloat() - 0.5f) * 540f,
            flipSpeed = 2f + random.nextFloat() * 4f,
            widthDp = 7f + random.nextFloat() * 6f,
            aspect = 0.4f + random.nextFloat() * 0.8f,
            color = colors[random.nextInt(colors.size)]
        )
    }
}

private const val TWO_PI = (Math.PI * 2).toFloat()

/**
 * A one-shot confetti burst, drawn on a single [Canvas].
 *
 * Purely decorative: it draws nothing once the burst is over, accepts no
 * touches, and knows nothing about why it was triggered - flip [active] on
 * and it celebrates, flip it off and it stops.
 */
@Composable
fun ConfettiBurst(
    active: Boolean,
    modifier: Modifier = Modifier,
    pieceCount: Int = 80,
    colors: List<Color> = ConfettiDefaults.colors
) {
    // Seeded per burst so two celebrations never fall identically, yet one
    // celebration stays stable across recompositions.
    val pieces = remember(active, pieceCount) {
        if (!active) emptyList() else {
            val random = Random(System.nanoTime())
            List(pieceCount) { ConfettiPiece.random(random, colors) }
        }
    }

    var elapsed by remember(active) { mutableStateOf(0f) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        while (elapsed < BURST_SECONDS) {
            withFrameNanos { now -> elapsed = (now - startNanos) / 1_000_000_000f }
        }
    }

    if (pieces.isEmpty() || elapsed >= BURST_SECONDS) return

    Canvas(modifier = modifier) {
        pieces.forEach { piece -> drawPiece(piece, elapsed) }
    }
}

private fun DrawScope.drawPiece(piece: ConfettiPiece, elapsed: Float) {
    val progress = elapsed / BURST_SECONDS
    val y = (piece.startY + elapsed * piece.fallSpeed * 1.6f) * size.height
    if (y > size.height) return

    val sway = sin(piece.swayPhase + elapsed * piece.swaySpeed) * piece.swayAmplitude
    val x = (piece.startX + sway) * size.width

    // A flat rectangle whose width pinches to zero reads as a scrap of paper
    // tumbling end over end - far cheaper than drawing it in 3D.
    val flip = abs(cos(elapsed * piece.flipSpeed + piece.swayPhase))
    val width = piece.widthDp.dp.toPx() * (0.2f + 0.8f * flip)
    val height = piece.widthDp.dp.toPx() * piece.aspect

    val alpha = if (progress < FADE_START) 1f
        else 1f - (progress - FADE_START) / (1f - FADE_START)

    rotate(degrees = elapsed * piece.spinSpeed, pivot = Offset(x, y)) {
        drawRect(
            color = piece.color.copy(alpha = piece.color.alpha * alpha.coerceIn(0f, 1f)),
            topLeft = Offset(x - width / 2f, y - height / 2f),
            size = Size(width, height)
        )
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun ConfettiBurstPreview() {
    ConfettiBurst(active = true, modifier = Modifier.fillMaxSize())
}
