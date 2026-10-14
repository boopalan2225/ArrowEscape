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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.milestone.MilestoneData.LevelState
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

/** Vibrant accent color per chapter (cycles if more chapters than colors). */
private val ChapterColors = listOf(
    Color(0xFF34A853), Color(0xFFFBBC04), Color(0xFF4285F4), Color(0xFF00ACC1),
    Color(0xFFE91E63), Color(0xFFEA4335), Color(0xFF9C27B0), Color(0xFFFF9800),
    Color(0xFF3F51B5), Color(0xFFFFD700),
    // Hard category chapters (bold, intense colors)
    Color(0xFF00BFA5), Color(0xFFFF6D00), Color(0xFF6200EA), Color(0xFF00C853),
    Color(0xFFD500F9)
)

@Composable
fun MilestoneScreen(
    prefs: GamePreferences.Prefs,
    onBackClick: () -> Unit,
    onPlayLevel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val highestLevel = prefs.highestLevel
    val levelStars = prefs.levelStars
    val listState = rememberLazyListState()
    // The map shows whichever game the player is actually playing.
    val journey = MilestoneData.forMode(prefs.curvedMode)

    // Auto-scroll to the current chapter.
    LaunchedEffect(journey) {
        listState.scrollToItem(journey.chapterIndexOf(highestLevel))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            JourneyTopBar(highestLevel, journey.totalLevels, prefs.totalLevelsCompleted, onBackClick)

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(journey.milestones) { _, milestone ->
                    ChapterCard(
                        milestone = milestone,
                        color = ChapterColors[milestone.index % ChapterColors.size],
                        highestLevel = highestLevel,
                        levelStars = levelStars,
                        onPlayLevel = onPlayLevel
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyTopBar(
    highestLevel: Int,
    totalLevels: Int,
    completed: Int,
    onBackClick: () -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val progress = (highestLevel - 1).coerceIn(0, totalLevels) /
            totalLevels.toFloat()
    val animated by animateFloatAsState(progress, tween(800), label = "prog")

    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
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
                        "Level ${highestLevel.coerceAtMost(totalLevels)} / $totalLevels  \u2022  $completed cleared",
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
private fun ChapterCard(
    milestone: Milestone,
    color: Color,
    highestLevel: Int,
    levelStars: Map<Int, Int>,
    onPlayLevel: (Int) -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val unlocked = highestLevel >= milestone.startLevel
    val done = (highestLevel - milestone.startLevel).coerceIn(0, MilestoneData.LEVELS_PER_MILESTONE)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = gameColors.cardBackground)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = if (unlocked) 0.10f else 0.03f), Color.Transparent)
                    )
                )
                .padding(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp))
                        .background(
                            if (unlocked) color.copy(alpha = 0.16f)
                            else gameColors.textSecondary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (unlocked) MilestoneIconView(milestone.icon, color, Modifier.size(30.dp))
                    else Text("\uD83D\uDD12", fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(milestone.emoji, fontSize = 14.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            milestone.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (unlocked) MaterialTheme.colorScheme.onSurface
                            else gameColors.textSecondary
                        )
                    }
                    Text(
                        "Levels ${milestone.levelRange}  \u2022  ${milestone.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = gameColors.textSecondary
                    )
                }
                // Progress pill
                Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f)) {
                    Text(
                        "$done/${MilestoneData.LEVELS_PER_MILESTONE}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Level bubbles in two rows of five.
            val levels = (milestone.startLevel..milestone.endLevel).toList()
            levels.chunked(5).forEach { rowLevels ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowLevels.forEach { lvl ->
                        LevelBubble(
                            level = lvl,
                            state = MilestoneData.levelState(lvl, highestLevel),
                            stars = levelStars[lvl] ?: 0,
                            color = color,
                            onClick = { onPlayLevel(lvl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelBubble(
    level: Int,
    state: LevelState,
    stars: Int,
    color: Color,
    onClick: () -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val starGold = Color(0xFFFFD700)

    val pulse = rememberInfiniteTransition(label = "bubblePulse")
    val pulseScale by pulse.animateFloat(
        1f, if (state == LevelState.CURRENT) 1.12f else 1f,
        infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ps"
    )

    val clickable = state != LevelState.LOCKED
    Box(
        modifier = Modifier
            .scale(if (state == LevelState.CURRENT) pulseScale else 1f)
            .size(if (state == LevelState.CURRENT) 52.dp else 46.dp)
            .shadow(if (state == LevelState.LOCKED) 0.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(
                when (state) {
                    LevelState.COMPLETED -> Brush.linearGradient(listOf(color, color.copy(alpha = 0.8f)))
                    LevelState.CURRENT -> Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f)))
                    LevelState.LOCKED -> Brush.linearGradient(
                        listOf(
                            gameColors.textSecondary.copy(alpha = 0.14f),
                            gameColors.textSecondary.copy(alpha = 0.14f)
                        )
                    )
                }
            )
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            LevelState.COMPLETED -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$level", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                // Show star rating
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    repeat(3) { index ->
                        Text(
                            text = "\u2605",
                            fontSize = 8.sp,
                            color = if (index < stars) starGold else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            LevelState.CURRENT -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$level", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text("PLAY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 7.sp)
            }
            LevelState.LOCKED -> Text(
                "$level",
                color = gameColors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

// ======================= PREVIEWS =======================

@Preview(showBackground = true, name = "Journey - Light")
@Composable
private fun MilestoneScreenPreviewLight() {
    ArrowEscapeTheme(darkTheme = false) {
        MilestoneScreen(
            prefs = GamePreferences.Prefs(
                highestLevel = 25,
                totalLevelsCompleted = 24,
                levelStars = mapOf(1 to 3, 2 to 2, 3 to 3, 4 to 1, 5 to 2)
            ),
            onBackClick = {},
            onPlayLevel = {}
        )
    }
}

@Preview(showBackground = true, name = "Journey - Dark")
@Composable
private fun MilestoneScreenPreviewDark() {
    ArrowEscapeTheme(darkTheme = true) {
        MilestoneScreen(
            prefs = GamePreferences.Prefs(
                highestLevel = 105,
                totalLevelsCompleted = 104,
                levelStars = (1..104).associateWith { (it % 3) + 1 }
            ),
            onBackClick = {},
            onPlayLevel = {}
        )
    }
}
