package com.arrowescape.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.sp
import com.arrowescape.ui.theme.ArrowEscapeTheme
import com.arrowescape.ui.theme.HomeMotif
import com.arrowescape.ui.theme.ThemePersona
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The living wallpaper behind Home: a themed wash, one hero motif, and a few
 * drifting specks. Every theme shares the same machinery and differs only by
 * its persona, so a new look never touches this file's layout code.
 */
@Composable
fun HomeBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    val persona = ArrowEscapeTheme.persona

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        persona.glow.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Motif(persona)
        MoteField(persona)
        content()
    }
}

@Composable
private fun Motif(persona: ThemePersona) {
    val clock = clock(persona.driftMillis * 2)
    val background = MaterialTheme.colorScheme.background

    Canvas(Modifier.fillMaxSize().alpha(0.7f)) {
        when (persona.motif) {
            HomeMotif.MOON -> drawMoon(persona.glow, background, clock)
            HomeMotif.SUN -> drawSun(persona.glow, clock)
            HomeMotif.SAILS -> drawSails(persona.glow, clock)
            HomeMotif.INK -> drawInk(persona.glow, clock)
            HomeMotif.ARROWS -> drawArrowsWash(persona.glow, clock)
        }
    }
}

/** A moon walking its phases: full to new and back, once per long cycle. */
private fun DrawScope.drawMoon(glow: Color, background: Color, clock: Float) {
    val radius = size.minDimension * 0.17f
    val centre = Offset(size.width * 0.74f, size.height * 0.16f)

    repeat(3) { ring ->
        drawCircle(
            color = glow.copy(alpha = 0.08f - ring * 0.02f),
            radius = radius * (1.35f + ring * 0.45f),
            center = centre
        )
    }
    drawCircle(color = glow.copy(alpha = 0.85f), radius = radius, center = centre)

    // The shadow disc slides across to carve crescents, gibbous and new.
    val phase = cos(2 * PI * clock).toFloat()
    drawCircle(
        color = background,
        radius = radius,
        center = centre.copy(x = centre.x + phase * radius * 1.9f)
    )
    drawCircle(
        color = glow.copy(alpha = 0.25f),
        radius = radius,
        center = centre,
        style = Stroke(width = 1.5f)
    )
}

/** A low sun with slowly turning rays, sinking and rising a little. */
private fun DrawScope.drawSun(glow: Color, clock: Float) {
    val radius = size.minDimension * 0.2f
    val bob = sin(2 * PI * clock).toFloat() * size.height * 0.02f
    val centre = Offset(size.width * 0.5f, size.height * 0.18f + bob)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glow.copy(alpha = 0.5f), Color.Transparent),
            center = centre,
            radius = radius * 3f
        ),
        radius = radius * 3f,
        center = centre
    )
    drawCircle(color = glow.copy(alpha = 0.8f), radius = radius, center = centre)

    rotate(degrees = clock * 360f, pivot = centre) {
        repeat(12) { index ->
            val angle = (PI * index / 6).toFloat()
            val from = Offset(
                centre.x + cos(angle) * radius * 1.3f,
                centre.y + sin(angle) * radius * 1.3f
            )
            val to = Offset(
                centre.x + cos(angle) * radius * 1.9f,
                centre.y + sin(angle) * radius * 1.9f
            )
            drawLine(glow.copy(alpha = 0.35f), from, to, strokeWidth = 4f)
        }
    }
}

/** Rolling swells with a ship riding them - the pirate horizon. */
private fun DrawScope.drawSails(glow: Color, clock: Float) {
    val swell = sin(2 * PI * clock).toFloat()
    val waterline = size.height * 0.845f

    listOf(0.80f to 0.20f, 0.86f to 0.30f).forEachIndexed { index, (baseline, alpha) ->
        drawSwell(glow, baseline, alpha, amplitude = size.height * 0.014f * (index + 1), clock = clock, reversed = index == 1)
    }

    drawShip(
        glow = glow,
        centre = Offset(size.width * (0.12f + 0.76f * clock), waterline),
        swell = swell
    )

    drawSwell(glow, baseline = 0.9f, alpha = 0.45f, amplitude = size.height * 0.016f, clock = clock, reversed = false)
}

private fun DrawScope.drawSwell(
    glow: Color,
    baseline: Float,
    alpha: Float,
    amplitude: Float,
    clock: Float,
    reversed: Boolean
) {
    val y = size.height * baseline
    val shift = clock * 2 * PI.toFloat() * if (reversed) -1f else 1f
    val wave = Path().apply {
        moveTo(0f, y)
        var x = 0f
        while (x <= size.width) {
            lineTo(x, y + amplitude * sin(x / size.width * 4 * PI.toFloat() + shift))
            x += 12f
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(wave, glow.copy(alpha = alpha))
}

private fun DrawScope.drawShip(glow: Color, centre: Offset, swell: Float) {
    val unit = size.minDimension * 0.1f
    val bob = swell * unit * 0.18f
    val pivot = Offset(centre.x, centre.y + bob)

    rotate(degrees = swell * 4f, pivot = pivot) {
        val hull = Path().apply {
            moveTo(pivot.x - unit * 1.5f, pivot.y)
            lineTo(pivot.x + unit * 1.5f, pivot.y)
            lineTo(pivot.x + unit * 0.95f, pivot.y + unit * 0.55f)
            lineTo(pivot.x - unit * 0.95f, pivot.y + unit * 0.55f)
            close()
        }
        drawPath(hull, glow.copy(alpha = 0.85f))

        drawLine(
            glow.copy(alpha = 0.85f),
            Offset(pivot.x, pivot.y),
            Offset(pivot.x, pivot.y - unit * 2.4f),
            strokeWidth = unit * 0.11f
        )

        val mainSail = Path().apply {
            moveTo(pivot.x + unit * 0.1f, pivot.y - unit * 2.3f)
            lineTo(pivot.x + unit * 1.25f, pivot.y - unit * 0.25f)
            lineTo(pivot.x + unit * 0.1f, pivot.y - unit * 0.25f)
            close()
        }
        val jib = Path().apply {
            moveTo(pivot.x - unit * 0.12f, pivot.y - unit * 2.1f)
            lineTo(pivot.x - unit * 1.05f, pivot.y - unit * 0.25f)
            lineTo(pivot.x - unit * 0.12f, pivot.y - unit * 0.25f)
            close()
        }
        drawPath(mainSail, glow.copy(alpha = 0.7f))
        drawPath(jib, glow.copy(alpha = 0.55f))

        val flag = Path().apply {
            moveTo(pivot.x, pivot.y - unit * 2.4f)
            lineTo(pivot.x + unit * 0.7f, pivot.y - unit * 2.18f)
            lineTo(pivot.x, pivot.y - unit * 1.95f)
            close()
        }
        drawPath(flag, glow.copy(alpha = 0.9f))
    }
}

/** Halftone print: a dot grid whose size ripples, pure ink on paper. */
private fun DrawScope.drawInk(glow: Color, clock: Float) {
    val columns = 11
    val step = size.width / columns
    val maxRadius = step * 0.3f
    var y = step * 0.5f
    while (y < size.height) {
        var x = step * 0.5f
        while (x < size.width) {
            val ripple = sin((x + y) / size.width * 3f * PI.toFloat() - 2 * PI.toFloat() * clock)
            val radius = maxRadius * (0.25f + 0.75f * (ripple + 1f) / 2f)
            drawCircle(glow.copy(alpha = 0.16f), radius = radius, center = Offset(x, y))
            x += step
        }
        y += step
    }

    val sweepY = size.height * (0.1f + 0.8f * clock)
    drawLine(
        glow.copy(alpha = 0.5f),
        Offset(0f, sweepY),
        Offset(size.width, sweepY - size.height * 0.04f),
        strokeWidth = 2f
    )
}

/** Classic keeps it plain: one soft band of colour sliding down the screen. */
private fun DrawScope.drawArrowsWash(glow: Color, clock: Float) {
    val centre = size.height * (0.2f + 0.6f * clock)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, glow.copy(alpha = 0.18f), Color.Transparent),
            startY = centre - size.height * 0.25f,
            endY = centre + size.height * 0.25f
        )
    )
}

/**
 * All motes share one animation clock and differ only by phase, so twenty
 * glyphs cost one running animation rather than twenty.
 */
@Composable
private fun MoteField(persona: ThemePersona) {
    val motes = remember(persona) { motesFor(persona) }
    val clock = clock(persona.driftMillis)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight

        motes.forEach { mote ->
            val lap = (clock + mote.phase) % 1f
            val travelled = if (persona.driftDown) lap else 1f - lap

            Text(
                text = mote.glyph,
                fontSize = mote.size.sp,
                color = persona.glow,
                modifier = Modifier
                    .offset(x = width * mote.x, y = height * travelled - height * 0.1f)
                    .alpha(mote.alpha * sin(PI * lap).toFloat())
            )
        }
    }
}

/** One shared 0..1 loop of [millis], used by every animated piece here. */
@Composable
private fun clock(millis: Int): Float {
    val infinite = rememberInfiniteTransition(label = "backdrop")
    val value by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(millis, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "clock"
    )
    return value
}

private data class Mote(
    val glyph: String,
    val x: Float,
    val size: Float,
    val phase: Float,
    val alpha: Float
)

/** Fixed seed: the specks are scattered, but the same scatter every launch. */
private fun motesFor(persona: ThemePersona): List<Mote> {
    val random = Random(persona.tagline.hashCode())
    return List(persona.moteCount) { index ->
        Mote(
            glyph = persona.motes[index % persona.motes.size],
            x = random.nextFloat() * 0.92f,
            size = 9f + random.nextFloat() * 10f,
            phase = random.nextFloat(),
            alpha = 0.15f + random.nextFloat() * 0.25f
        )
    }
}
