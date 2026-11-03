package com.clevercorner.arrowescape.ui.daily

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.game.daily.DailyShuffle
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import com.clevercorner.arrowescape.ui.theme.LocalThemePersona

/**
 * Time until the board re-deals, as a thin bar under the board.
 *
 * It drains across the width over the shuffle interval. In the last few
 * seconds it turns to the warning colour and throbs - the cue to commit to a
 * move or wait it out. Only the bar throbs; the label stays fully opaque.
 */
@Composable
fun ShuffleTimerBar(secondsLeft: Int?, modifier: Modifier = Modifier) {
    if (secondsLeft == null) return

    val style = LocalThemePersona.current.dailyHeader
    val gameColors = ArrowEscapeTheme.gameColors
    val urgent = secondsLeft <= DailyShuffle.WARN_SECONDS
    // The calm colour is the theme's own accent, not the shared hint amber:
    // an ink-wash board had one orange bar on it and nothing else, which
    // made the timer look like it belonged to a different app.
    val accent = if (urgent) gameColors.arrowDown else style.accent

    // Animated so the bar glides between ticks instead of stepping.
    val fraction by animateFloatAsState(
        targetValue = secondsLeft.toFloat() / DailyShuffle.INTERVAL_SECONDS,
        animationSpec = tween(durationMillis = 950, easing = LinearEasing),
        label = "shuffleFill"
    )

    val pulse = rememberInfiniteTransition(label = "shufflePulse")
    val throb by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "throb"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = style.glass,
        shadowElevation = 2.dp
    ) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.daily_shuffle_in, "${secondsLeft}s"),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .alpha(if (urgent) throb else 1f)
                    .background(accent)
            )
        }
    }
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
