package com.clevercorner.arrowescape.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.ui.components.unit
import kotlin.math.sin

/**
 * The hand that shows a player what to do, instead of telling them.
 *
 * Drawn rather than shipped as an asset: it has to stay legible on five
 * themes over any board colour, which a flat PNG cannot do, and it costs
 * nothing in the APK. The shape is deliberately a *sticker* - a dark outline
 * under a light fill - because that is the only treatment that survives both
 * the Ink theme's white paper and the daily's dark slate.
 *
 * Everything here is pure drawing. A finger that could swallow a tap would
 * be a tutorial that blocks the lesson it is teaching, so this never goes
 * near pointer input.
 */
object FingerPointer {

    /** How big the hand is drawn, as a multiple of the board's cell size. */
    const val CELL_SCALE = 1.15f

    /** Down-and-right, the angle a right hand actually approaches from. */
    private val LEAN = unit(Offset(0.38f, 0.92f))

    /**
     * One hand, fingertip exactly on [at].
     *
     * @param press 0 is hovering, 1 is pressed flat. The tip slides a hair
     *   along the lean and the whole hand shrinks, which is what selling a
     *   touch takes - a hand that only changed colour reads as a sticker.
     * @param size the hand's long dimension in pixels.
     */
    fun DrawScope.drawFinger(
        at: Offset,
        press: Float,
        size: Float,
        ink: Color,
        fill: Color,
        alpha: Float = 1f
    ) {
        if (alpha <= 0.01f) return

        val squash = 1f - 0.07f * press
        val s = size * squash
        val tip = at + LEAN * (size * 0.07f * press)

        val knuckle = tip + LEAN * (s * 0.92f)
        val outline = s * 0.075f

        // A contact shadow, offset along the lean so the hand looks lifted
        // off the board rather than printed on it.
        drawOval(
            color = Color.Black.copy(alpha = 0.16f * alpha * (1f - 0.4f * press)),
            topLeft = knuckle + LEAN * (s * 0.18f) - Offset(s * 0.40f, s * 0.30f),
            size = Size(s * 0.80f, s * 0.60f)
        )

        // Ink first at a wider stroke, fill second at a narrower one: two
        // passes give a clean outline without any path arithmetic.
        repeat(2) { pass ->
            val color = (if (pass == 0) ink else fill).copy(alpha = alpha)
            val pad = if (pass == 0) outline else 0f

            drawRoundRect(
                color = color,
                topLeft = knuckle - Offset(s * 0.36f + pad, s * 0.30f + pad),
                size = Size(s * 0.72f + pad * 2, s * 0.60f + pad * 2),
                cornerRadius = CornerRadius(s * 0.24f + pad, s * 0.24f + pad)
            )
            drawLine(
                color = color,
                start = tip + LEAN * (s * 0.10f),
                end = knuckle,
                strokeWidth = s * 0.30f + pad * 2,
                cap = StrokeCap.Round
            )
        }

        // The nail: a small bright crescent that tells the eye which end of
        // the hand is doing the pointing.
        drawCircle(
            color = ink.copy(alpha = 0.22f * alpha),
            radius = s * 0.07f,
            center = tip + LEAN * (s * 0.20f)
        )
    }

    /**
     * Expanding contact rings, the universal "that was a tap" signal.
     *
     * [progress] runs 0 to 1 once per tap; past 1 nothing is drawn, so a
     * caller can hand it a timeline that has moved on without guarding.
     */
    fun DrawScope.drawTapRipple(at: Offset, progress: Float, color: Color, size: Float) {
        if (progress <= 0f || progress >= 1f) return

        // Two rings a beat apart read as one spreading wave; a single ring
        // reads as a circle that happens to be growing.
        listOf(progress, progress - 0.28f).forEach { p ->
            if (p <= 0f) return@forEach
            drawCircle(
                color = color.copy(alpha = 0.55f * (1f - p)),
                radius = size * (0.18f + 0.52f * p),
                center = at,
                style = Stroke(width = size * 0.07f * (1f - p))
            )
        }
    }

    /**
     * A ring that stays put while a long press is held - the one gesture a
     * ripple cannot describe, because a ripple is over in a blink and this
     * is the opposite of over.
     */
    fun DrawScope.drawHoldRing(at: Offset, held: Float, color: Color, size: Float) {
        if (held <= 0f) return
        drawCircle(
            color = color.copy(alpha = 0.30f * held.coerceAtMost(1f)),
            radius = size * 0.34f,
            center = at
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * held.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = at - Offset(size * 0.40f, size * 0.40f),
            size = Size(size * 0.80f, size * 0.80f),
            style = Stroke(width = size * 0.07f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Progress through the slice of a timeline between [from] and [to], clamped
 * to 0..1 outside it.
 *
 * Every animation in the tutorial is "a thing that happens between two
 * moments of a loop", and writing that out by hand each time is how timing
 * bugs get in.
 */
internal fun phase(now: Float, from: Float, to: Float): Float =
    ((now - from) / (to - from).coerceAtLeast(0.001f)).coerceIn(0f, 1f)

/** Slow at both ends - how a hand actually moves, unlike a linear ramp. */
internal fun ease(t: Float): Float = t * t * (3f - 2f * t)

/**
 * A frame clock that resets to zero every [cycleMillis].
 *
 * Returned as a state object rather than a value on purpose: callers read it
 * *inside* a draw lambda, which keeps a sixty-times-a-second animation in
 * the draw phase instead of recomposing the tree around it.
 */
@Composable
internal fun rememberLoopClock(cycleMillis: Int, resetKey: Any?) =
    remember(resetKey) { mutableLongStateOf(0L) }.also { clock ->
        LaunchedEffect(resetKey) {
            var start = 0L
            withFrameMillis { start = it }
            while (true) {
                withFrameMillis { now ->
                    clock.longValue = (now - start) % cycleMillis
                }
            }
        }
    }

/**
 * A hand that taps the middle of the area it is given, over and over, until
 * the player takes the hint and does it themselves.
 *
 * This is the spotlight's half of the lesson: the card explains the move in
 * words and this shows where the words point. It draws only - the control
 * underneath stays as tappable as it ever was.
 */
@Composable
fun TapHintFinger(
    modifier: Modifier = Modifier,
    ink: Color = Color(0xFF1B1B1F),
    fill: Color = Color.White,
    ripple: Color = Color.White
) {
    val clock = rememberLoopClock(TAP_HINT_CYCLE, resetKey = Unit)
    val handSize = with(LocalDensity.current) { 64.dp.toPx() }

    Canvas(modifier.fillMaxSize()) {
        val now = clock.longValue.toFloat()
        // A hover bob keeps the hand alive between taps; a hand that freezes
        // for a second and a half looks like a rendering bug.
        val bob = sin(now / TAP_HINT_CYCLE * 2f * Math.PI).toFloat() * handSize * 0.03f
        val at = center + Offset(0f, bob)

        val press = phase(now, 700f, 840f) - phase(now, 960f, 1120f)

        with(FingerPointer) {
            drawTapRipple(at, phase(now, 840f, 1500f), ripple, handSize)
            drawFinger(at, press, handSize, ink, fill)
        }
    }
}

private const val TAP_HINT_CYCLE = 1900
