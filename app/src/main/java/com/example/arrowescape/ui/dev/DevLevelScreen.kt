package com.example.arrowescape.ui.dev

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.game.curve.CurvedJourney
import com.example.arrowescape.game.curve.DifficultyTuning
import com.example.arrowescape.game.curve.ShapeCatalog

/**
 * Developer-only level picker: jump straight to any level, or to a given shape
 * at a given difficulty tier, to check how it looks and plays.
 */
@Composable
fun DevLevelScreen(
    onBackClick: () -> Unit,
    onPlayLevel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        DevHeader(onBackClick)

        var typed by remember { mutableStateOf("") }
        val parsed = DevLevels.parseLevel(typed)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.filter(Char::isDigit).take(4) },
                label = { Text("Level (1-${DevLevels.MAX_LEVEL})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { parsed?.let(onPlayLevel) }),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { parsed?.let(onPlayLevel) },
                enabled = parsed != null
            ) {
                Text("Open")
            }
        }

        Text(
            text = "OR JUMP TO A SHAPE'S CHAPTER",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(ShapeCatalog.ALL) { chapter, design ->
                ChapterRow(chapter = chapter, name = design.name, onPlayLevel = onPlayLevel)
            }
        }
    }
}

/** One shape's chapter: where it lives in the journey, and jump-in buttons. */
@Composable
private fun ChapterRow(chapter: Int, name: String, onPlayLevel: (Int) -> Unit) {
    val first = CurvedJourney.firstLevelOf(chapter)
    val last = CurvedJourney.lastLevelOf(chapter)
    val master = DevLevels.masterReplay(chapter)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$name  \u00B7  chapter ${chapter + 1}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Levels $first\u2013$last  \u00B7  ${DifficultyTuning.tierOf(first)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LevelButton("First", first, onPlayLevel, Modifier.weight(1f))
                LevelButton("Last", last, onPlayLevel, Modifier.weight(1f))
                // Same shape, one lap later - always the hardest tier.
                LevelButton("Master", master, onPlayLevel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LevelButton(
    label: String,
    level: Int,
    onPlayLevel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = { onPlayLevel(level) },
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("L$level", fontSize = 10.sp)
        }
    }
}

@Composable
private fun DevHeader(onBackClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "\u2190",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Developer · Levels",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
