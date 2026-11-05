package com.clevercorner.arrowescape.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.game.curve.CurvedJourney
import com.clevercorner.arrowescape.game.curve.ShapeCatalog

/**
 * Developer-only: play a specific journey design inside the daily challenge.
 *
 * Lists every authored shape with the first level that plays it, so a
 * developer can run "the owl" through the daily frame and shuffle and see it
 * exactly as a player would - same generator, same tier tuning, because the
 * level number carries both. [selectedLevel] null means today's real puzzle.
 */
@Composable
fun DevDesignPicker(
    selectedLevel: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val current = selectedLevel?.let { level ->
        val chapter = (level - 1) / CurvedJourney.LEVELS_PER_CHAPTER
        "${ShapeCatalog.designForChapter(chapter).name} · L$level"
    } ?: stringResource(R.string.dev_daily_design_today)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.dev_daily_design_label),
            style = MaterialTheme.typography.labelMedium
        )
        Row {
            OutlinedButton(onClick = { expanded = true }) {
                Text(current, style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dev_daily_design_today)) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    }
                )
                ShapeCatalog.ALL.forEachIndexed { chapter, design ->
                    val first = CurvedJourney.firstLevelOf(chapter)
                    DropdownMenuItem(
                        text = { Text("${design.name} · L$first") },
                        onClick = {
                            onSelect(first)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
