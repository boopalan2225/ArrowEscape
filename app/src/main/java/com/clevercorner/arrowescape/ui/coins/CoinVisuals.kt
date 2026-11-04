package com.clevercorner.arrowescape.ui.coins

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.ui.components.GlyphIcon
import kotlin.math.abs
import kotlin.math.cos

/**
 * The currency, drawn.
 *
 * Gold rather than the theme's accent on purpose: a coin that changed colour
 * with the scenery would read as decoration. This one reads as money in every
 * theme, which is the only job it has.
 */
object CoinArt {
    val Rim = Color(0xFF8A5A08)
    val Face = Color(0xFFFFD75E)
    val Shine = Color(0xFFFFF4C2)
    val Shadow = Color(0xFFE09B12)

    /** A coin catches the light from above-left, so the gradient does too. */
    val faceBrush = Brush.linearGradient(listOf(Shine, Face, Shadow))
}

/**
 * One coin. [flipping] sends it spinning edge-on about its vertical axis -
 * the universal "you just got paid" gesture, and cheap: a graphics layer
 * rotation, no new composition per frame.
 */
@Composable
fun CoinIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    flipping: Boolean = false
) {
    val spin = rememberInfiniteTransition(label = "coinSpin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "coinAngle"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                if (flipping) {
                    rotationY = angle
                    // Edge-on a coin is a sliver, not a disc - without this
                    // the spin looks like a sticker turning around.
                    cameraDistance = 12f * density
                }
            }
            .clip(CircleShape)
            .background(CoinArt.faceBrush),
        contentAlignment = Alignment.Center
    ) {
        // The arrow on the face: this is Arrow Escape's money, not a generic token.
        GlyphIcon(
            glyph = "\u27A4",
            fontSize = (size.value * 0.5f).sp,
            color = CoinArt.Rim,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * The balance, always visible, always honest.
 *
 * The number *counts* to its new value rather than cutting to it, and the
 * pill gives a small shove outwards when it grows: the eye catches motion far
 * better than it catches a digit changing, and a reward nobody noticed may as
 * well not have been paid.
 */
@Composable
fun CoinPill(
    balance: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shown by animateIntAsState(
        targetValue = balance,
        animationSpec = tween(700, easing = LinearEasing),
        label = "coinBalance"
    )

    // A bump on every *increase*. Spending is quiet - the unlock animation is
    // already doing the talking there.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(balance) {
        bump.snapTo(1f)
        bump.animateTo(1.18f, tween(140, easing = EaseOutBack))
        bump.animateTo(1f, tween(260))
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp,
        modifier = modifier
            .scale(bump.value)
            .clip(CircleShape)
            .let { base -> if (onClick == null) base else base.clickable(onClick = onClick) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CoinIcon(size = 18.dp)
            Text(
                text = "$shown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * A fistful of coins thrown outward and gone.
 *
 * Purely decorative and deliberately short: [progress] runs 0..1 once, the
 * caller owns the clock, and when it is done there is nothing left on screen
 * to clean up. Positions are a cheap deterministic fan rather than a random
 * scatter, so the burst looks the same every time and cannot flicker on
 * recomposition.
 */
@Composable
fun CoinBurst(
    progress: Float,
    count: Int = 9,
    spread: Dp = 110.dp,
    modifier: Modifier = Modifier
) {
    if (progress <= 0f || progress >= 1f) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        repeat(count) { index ->
            // Fan the coins across a half-turn, alternating sides so the
            // throw reads as symmetrical rather than as a drift.
            val slot = (index.toFloat() / (count - 1)) * 2f - 1f
            val rise = 1f - (1f - progress) * (1f - progress) // ease out
            val fall = progress * progress                     // gravity wins late

            Box(
                Modifier
                    .graphicsLayer {
                        translationX = slot * spread.toPx() * rise
                        translationY = (-spread.toPx() * 0.9f * rise) + (spread.toPx() * fall)
                        rotationZ = slot * 240f * progress
                        alpha = (1f - fall).coerceIn(0f, 1f)
                        val pop = 0.6f + 0.4f * cos(abs(slot))
                        scaleX = pop
                        scaleY = pop
                    }
            ) {
                CoinIcon(size = 20.dp)
            }
        }
    }
}
