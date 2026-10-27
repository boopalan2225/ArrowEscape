package com.clevercorner.arrowescape.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * Clock widgets for a level run.
 *
 * The timer is a carrot, never a stick: nothing expires, nothing is lost by
 * being slow. It exists so a player who has already solved a board has a
 * reason to solve it again, faster.
 */

/** `m:ss`, or `h:mm:ss` for the player who wandered off mid-level. */
fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

/**
 * Live run clock for the header. Turns gold once the player is inside their
 * own record for this level.
 *
 * It deliberately does NOT print the record next to the time: two clocks side
 * by side is two things to read, and the colour already says the only thing
 * that matters mid-level. The exact numbers show up on the completion card,
 * where there is time to care.
 */
@Composable
fun LevelTimerChip(
    elapsedSeconds: Int,
    bestSeconds: Int?,
    modifier: Modifier = Modifier
) {
    val beatingBest = bestSeconds != null && elapsedSeconds < bestSeconds
    val accent = if (beatingBest) PACE_GOLD else ArrowEscapeTheme.gameColors.textSecondary

    Text(
        text = formatClock(elapsedSeconds),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accent,
        modifier = modifier.padding(end = 4.dp)
    )
}

/**
 * Result line for the completion card: this run against the record, and a
 * shout when the record just fell.
 */
@Composable
fun BestTimeRow(
    timeSeconds: Int,
    bestSeconds: Int?,
    isNewBest: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "\u23F1 ${formatClock(timeSeconds)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
        when {
            isNewBest -> Text(
                text = "\u2605 ${stringResource(R.string.best_time_badge).uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = PACE_GOLD,
                letterSpacing = 1.sp
            )

            bestSeconds != null -> Text(
                text = stringResource(R.string.best_time_label, formatClock(bestSeconds)),
                style = MaterialTheme.typography.labelMedium,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

private val PACE_GOLD = Color(0xFFFFC107)
