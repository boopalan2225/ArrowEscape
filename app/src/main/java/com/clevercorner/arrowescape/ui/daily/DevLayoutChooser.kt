package com.clevercorner.arrowescape.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.game.daily.DailyBoardLayout

/**
 * Developer-only switch between daily board framings, so a developer can
 * validate how the same puzzle plays on each one. Shown only in dev mode.
 *
 * Changing it starts a fresh run of today's puzzle - the board is re-dealt
 * in the new frame, which is the only way to see the difference honestly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevLayoutChooser(
    selected: DailyBoardLayout,
    onSelect: (DailyBoardLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.dev_daily_layout_label),
            style = MaterialTheme.typography.labelMedium
        )
        DailyBoardLayout.entries.forEach { layout ->
            FilterChip(
                selected = layout == selected,
                onClick = { onSelect(layout) },
                label = {
                    Text(
                        stringResource(
                            when (layout) {
                                DailyBoardLayout.PLAQUE -> R.string.dev_daily_layout_plaque
                                DailyBoardLayout.JOURNEY -> R.string.dev_daily_layout_journey
                            }
                        )
                    )
                }
            )
        }
    }
}
