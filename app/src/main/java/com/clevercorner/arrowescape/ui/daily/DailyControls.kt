package com.clevercorner.arrowescape.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R

/** The hint glow. Fixed like the sunrise: it is a light, not a theme colour. */
private val HintGlow = Color(0xFFFFD35C)

/**
 * The daily's three actions in one row: step back, ask for help, start over.
 *
 * Hint sits in the middle as the one large target - it is the thing people
 * reach for when stuck. Undo is greyed out, not hidden, when there is
 * nothing to take back, so the row never shifts under the player's thumb.
 */
@Composable
fun DailyActionBar(
    canUndo: Boolean,
    hintsRemaining: Int,
    onUndoClick: () -> Unit,
    onHintClick: () -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ActionPill(
            icon = Icons.Filled.Refresh,
            mirrored = true,
            label = stringResource(R.string.daily_undo),
            enabled = canUndo,
            onClick = onUndoClick
        )
        HintOrb(hintsRemaining = hintsRemaining, onClick = onHintClick)
        ActionPill(
            icon = Icons.Filled.Refresh,
            label = stringResource(R.string.daily_restart),
            enabled = true,
            onClick = onRestartClick
        )
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    /** Flips the glyph, turning a clockwise arrow into an undo. */
    mirrored: Boolean = false
) {
    val ink = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier
            .width(96.dp)
            .height(64.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The label carries the meaning; the icon is decoration.
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.graphicsLayer { scaleX = if (mirrored) -1f else 1f }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = ink
            )
        }
    }
}

/**
 * A glowing bulb with the hints left as a badge. With none left the bulb
 * becomes a play glyph: tapping it offers an ad for another, which is the
 * same button doing a different job, not a second control.
 */
@Composable
private fun HintOrb(hintsRemaining: Int, onClick: () -> Unit) {
    val hasHints = hintsRemaining > 0
    val description = if (hasHints) {
        stringResource(R.string.daily_hint_left, hintsRemaining)
    } else {
        stringResource(R.string.daily_hint_watch_ad)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = HintGlow,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(68.dp)
                    .semantics { contentDescription = description }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (hasHints) "\uD83D\uDCA1" else "\u25B6",
                        fontSize = 28.sp
                    )
                }
            }

            if (hasHints) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$hintsRemaining",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.daily_hint),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
