package com.arrowescape.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * In-game strip shown only in developer mode: what level/shape/tier this is,
 * how many arrows it has, and previous/next buttons to flip through levels.
 *
 * A side with nowhere to go is left BLANK rather than greyed out - at level 1
 * only Next shows, at the last defined level only Prev shows - so the bar
 * never implies a destination that does not exist.
 */
@Composable
fun DevLevelBar(
    level: Int,
    shapeName: String,
    difficulty: String,
    arrowCount: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (canGoPrevious) {
            OutlinedButton(
                onClick = onPrevious,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("\u25C0 Prev")
            }
        } else {
            NavSlotSpacer()
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "L$level · $shapeName",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "$difficulty · $arrowCount arrows",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (canGoNext) {
            OutlinedButton(
                onClick = onNext,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Next \u25B6")
            }
        } else {
            NavSlotSpacer()
        }
    }
}

/** Keeps the centre label centred when one side's button is hidden. */
@Composable
private fun NavSlotSpacer() {
    Spacer(Modifier.width(1.dp))
}
