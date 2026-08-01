package com.example.arrowescape.ui.milestone

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.milestone.MilestoneData.LevelState
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

/** Vibrant accent color per chapter (index 0..9). */
private val ChapterColors = listOf(
    Color(0xFF34A853), // green
    Color(0xFFFBBC04), // amber
    Color(0xFF4285F4), // blue
    Color(0xFF00ACC1), // cyan
    Color(0xFFE91E63), // pink
    Color(0xFFEA4335), // red
    Color(0xFF9C27B0), // purple
    Color(0xFFFF9800), // orange
    Color(0xFF3F51B5), // indigo
    Color(0xFFFFD700)  // gold
)

private sealed interface JourneyEntry {
    data class Header(val milestone: Milestone) : JourneyEntry
    data class LevelDot(val level: Int) : JourneyEntry
}

@Composable
fun MilestoneScreen(
    prefs: GamePreferences.Prefs,
    onBackClick: () -> Unit,
    onPlayLevel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val highestLevel = prefs.highestLevel

    // Build a flat list: chapter header followed by its 10 level dots.
    val entries = remember {
        buildList {
            MilestoneData.milestones.forEach { m ->
                add(JourneyEntry.Header(m))
                for (lvl in m.startLevel..m.endLevel) add(JourneyEntry.LevelDot(lvl))
            }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to the current level on open.
    LaunchedEffect(Unit) {
        val idx = entries.indexOfFirst {
            it is JourneyEntry.LevelDot && it.level == highestLevel
        }
        if (idx >= 0) listState.scrollToItem((idx - 3).coerceAtLeast(0))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            JourneyTopBar(highestLevel, prefs.totalLevelsCompleted, onBackClick)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp, top = 8.dp)
            ) {
                itemsIndexed(entries) { index, entry ->
                    when (entry) {
                        is JourneyEntry.Header -> ChapterBanner(
                            milestone = entry.milestone,
                            color = ChapterColors[entry.milestone.index],
                            unlocked = highestLevel >= entry.milestone.startLevel
                        )
                        is JourneyEntry.LevelDot -> {
                            val chapterIdx = (entry.level - 1) / MilestoneData.LEVELS_PER_MILESTONE
                            LevelDotRow(
                                level = entry.level,
                                state = MilestoneData.levelState(entry.level, highestLevel),
                                color = ChapterColors[chapterIdx.coerceIn(0, ChapterColors.lastIndex)],
                                zigIndex = index,
                                onClick = { onPlayLevel(entry.level) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyTopBar(highestLevel: Int, completed: Int, onBackClick: () -> Unit) {
    val gameColors = ArrowEscapeTheme.gameColors
    val progress = (highestLevel - 1).coerceIn(0, MilestoneData.TOTAL_LEVELS) /
            MilestoneData.TOTAL_LEVELS.toFloat()
    val animated by animateFloatAsState(progress, tween(800), label = "prog")

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("\u2190", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Journey",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Level ${highestLevel.coerceAtMost(MilestoneData.TOTAL_LEVELS)} of ${MilestoneData.TOTAL_LEVELS}  \u2022  $completed cleared",
                        style = MaterialTheme.typography.bodySmall,
                        color = gameColors.textSecondary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(MaterialTheme.colorScheme.primary, gameColors.arrowHint)
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun ChapterBanner(milestone: Milestone, color: Color, unlocked: Boolean) {
    val gameColors = ArrowEscapeTheme.gameColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (unlocked) color.copy(alpha = 0.16f) else gameColors.textSecondary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (unlocked) {
                ShapeIcon(milestone.shape, color, Modifier.size(34.dp))
            } else {
                Text("\uD83D\uDD12", fontSize = 20.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(milestone.emoji, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    milestone.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (unlocked) MaterialTheme.colorScheme.onBackground else gameColors.textSecondary
                )
            }
            Text(
                "Levels ${milestone.levelRange}  \u2022  ${milestone.shape.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = gameColors.textSecondary
            )
        }
    }
}

@Composable
private fun LevelDotRow(
    level: Int,
    state: LevelState,
    color: Color,
    zigIndex: Int,
    onClick: () -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors

    // Winding zig-zag horizontal bias (-1..1).
    val pattern = floatArrayOf(-0.55f, -0.28f, 0f, 0.28f, 0.55f, 0.28f, 0f, -0.28f)
    val bias = pattern[zigIndex % pattern.size]

    // Pulse for current level
    val pulse = rememberInfiniteTransition(label = "lvlPulse")
    val pulseScale by pulse.animateFloat(
        1f, if (state == LevelState.CURRENT) 1.12f else 1f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ps"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = BiasAlignment(bias, 0f)
    ) {
        val clickable = state != LevelState.LOCKED
        Box(
            modifier = Modifier
                .scale(if (state == LevelState.CURRENT) pulseScale else 1f)
                .size(if (state == LevelState.CURRENT) 68.dp else 56.dp)
                .shadow(
                    if (state == LevelState.LOCKED) 0.dp else 6.dp,
                    CircleShape
                )
                .clip(CircleShape)
                .background(
                    when (state) {
                        LevelState.COMPLETED -> Brush.linearGradient(listOf(color, color.copy(alpha = 0.8f)))
                        LevelState.CURRENT -> Brush.linearGradient(
                            listOf(color, color.copy(alpha = 0.75f))
                        )
                        LevelState.LOCKED -> Brush.linearGradient(
                            listOf(
                                gameColors.textSecondary.copy(alpha = 0.16f),
                                gameColors.textSecondary.copy(alpha = 0.16f)
                            )
                        )
                    }
                )
                .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                LevelState.COMPLETED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$level", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("\u2605", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp)
                }
                LevelState.CURRENT -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$level", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("PLAY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
                LevelState.LOCKED -> Text(
                    "$level",
                    color = gameColors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
