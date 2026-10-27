package com.clevercorner.arrowescape.ui.daily

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.game.daily.DailyShuffle
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * The daily challenge's header - deliberately nothing like the journey's.
 *
 * The journey's chrome is quiet because a journey level waits for you. This
 * one is built around a draining ring, because the daily does not: the ring
 * IS the mode. Everything else (date, streak, lives, arrows) is tucked
 * around it so the one thing that moves is the one thing you look at.
 */
@Composable
fun DailyHud(
    dateLabel: String,
    streak: Int,
    secondsLeft: Int?,
    shuffleCount: Int,
    lives: Int,
    remainingArrows: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Surface(
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            gameColors.arrowHint.copy(alpha = 0.18f),
                            androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.daily_badge).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = gameColors.arrowHint
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                ShuffleRing(secondsLeft = secondsLeft)
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DailyStat(glyph = "\u2764", value = "$lives")
                DailyStat(glyph = "\u2197", value = "$remainingArrows")
                if (streak > 0) DailyStat(glyph = "\uD83D\uDD25", value = "$streak")
                if (shuffleCount > 0) {
                    DailyStat(glyph = "\uD83D\uDD00", value = "$shuffleCount")
                }
            }
        }
    }
}

@Composable
private fun DailyStat(glyph: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(text = glyph, fontSize = 13.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A ring that drains over the minute, with the seconds in the middle.
 *
 * Reads at a glance from across the room, which a row of text never does -
 * and in the last few seconds it flushes red and throbs, which is the cue to
 * either commit to a move or wait out the shuffle.
 */
@Composable
private fun ShuffleRing(secondsLeft: Int?, modifier: Modifier = Modifier) {
    if (secondsLeft == null) return

    val gameColors = ArrowEscapeTheme.gameColors
    val urgent = secondsLeft <= DailyShuffle.WARN_SECONDS
    val accent = if (urgent) gameColors.arrowDown else gameColors.arrowHint

    // Animated so the ring glides between ticks instead of stepping.
    val sweep by animateFloatAsState(
        targetValue = secondsLeft.toFloat() / DailyShuffle.INTERVAL_SECONDS,
        animationSpec = tween(durationMillis = 950, easing = androidx.compose.animation.core.LinearEasing),
        label = "ringSweep"
    )

    val pulse = rememberInfiniteTransition(label = "ringPulse")
    val throb by pulse.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "throb"
    )

    Box(
        modifier = modifier
            .size(52.dp)
            .alpha(if (urgent) throb else 1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.11f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = accent.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = -360f * sweep.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "$secondsLeft",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
    }
}

/**
 * The "it just happened" banner. Brief on purpose: the board itself is the
 * real announcement, this only names what the player just saw.
 */
@Composable
fun ShuffleFlash(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150)),
        exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.1f, animationSpec = tween(250)),
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.daily_shuffle_flash),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
