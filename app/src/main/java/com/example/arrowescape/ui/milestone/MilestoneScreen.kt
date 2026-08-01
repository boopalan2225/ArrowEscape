package com.example.arrowescape.ui.milestone

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.milestone.MilestoneData.MilestoneState
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

@Composable
fun MilestoneScreen(
    prefs: GamePreferences.Prefs,
    onBackClick: () -> Unit,
    onPlayMilestone: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val highestLevel = prefs.highestLevel

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Top bar
            item {
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
                        Text("\u2190", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Your Journey",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Hero progress header
            item {
                JourneyHeader(
                    currentLevel = prefs.currentLevel,
                    highestLevel = highestLevel,
                    totalCompleted = prefs.totalLevelsCompleted
                )
            }

            // Milestone nodes
            itemsIndexed(MilestoneData.milestones) { index, milestone ->
                val state = MilestoneData.stateFor(milestone, highestLevel)
                MilestoneNode(
                    milestone = milestone,
                    state = state,
                    progress = MilestoneData.progressWithin(milestone, highestLevel),
                    isFirst = index == 0,
                    isLast = index == MilestoneData.milestones.lastIndex,
                    alignLeft = index % 2 == 0,
                    onClick = {
                        if (state != MilestoneState.LOCKED) {
                            // Resume at current level if it's inside this milestone,
                            // otherwise start at the milestone's first level.
                            val targetLevel =
                                if (prefs.currentLevel in milestone.startLevel..milestone.endLevel)
                                    prefs.currentLevel
                                else
                                    milestone.startLevel
                            onPlayMilestone(targetLevel)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun JourneyHeader(
    currentLevel: Int,
    highestLevel: Int,
    totalCompleted: Int
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val overallProgress = (highestLevel - 1).coerceIn(0, MilestoneData.TOTAL_LEVELS) /
            MilestoneData.TOTAL_LEVELS.toFloat()

    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = gameColors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LEVEL ${highestLevel.coerceAtMost(MilestoneData.TOTAL_LEVELS)} / ${MilestoneData.TOTAL_LEVELS}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = gameColors.textSecondary
            )

            Spacer(Modifier.height(16.dp))

            // Big animated progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    gameColors.arrowHint
                                )
                            )
                        )
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HeaderStat("${(overallProgress * 100).toInt()}%", "Complete")
                HeaderStat("$totalCompleted", "Cleared")
                HeaderStat("$currentLevel", "Playing")
            }
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ArrowEscapeTheme.gameColors.textSecondary
        )
    }
}

@Composable
private fun MilestoneNode(
    milestone: Milestone,
    state: MilestoneState,
    progress: Float,
    isFirst: Boolean,
    isLast: Boolean,
    alignLeft: Boolean,
    onClick: () -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors

    val nodeColor = when (state) {
        MilestoneState.COMPLETED -> gameColors.success
        MilestoneState.CURRENT -> MaterialTheme.colorScheme.primary
        MilestoneState.LOCKED -> gameColors.textSecondary.copy(alpha = 0.4f)
    }

    // Pulse for current milestone
    val pulse = rememberInfiniteTransition(label = "nodePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (state == MilestoneState.CURRENT) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = if (alignLeft) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(vertical = 8.dp)
                .scale(if (state == MilestoneState.CURRENT) pulseScale else 1f)
                .then(
                    if (state != MilestoneState.LOCKED)
                        Modifier.clickable(onClick = onClick) else Modifier
                )
                .shadow(
                    elevation = if (state == MilestoneState.CURRENT) 12.dp else 3.dp,
                    shape = RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (state == MilestoneState.LOCKED)
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else gameColors.cardBackground
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (state == MilestoneState.CURRENT) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        Color.Transparent
                                    )
                                )
                            )
                        } else Modifier
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shape badge
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(nodeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (state == MilestoneState.LOCKED) {
                        Text("\uD83D\uDD12", fontSize = 22.sp)
                    } else {
                        ShapeIcon(
                            shape = milestone.shape,
                            color = nodeColor,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Text info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = milestone.emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = milestone.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state == MilestoneState.LOCKED)
                                gameColors.textSecondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Levels ${milestone.levelRange}",
                        style = MaterialTheme.typography.bodySmall,
                        color = gameColors.textSecondary
                    )

                    if (state == MilestoneState.CURRENT) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Trailing status
                when (state) {
                    MilestoneState.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(gameColors.success),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\u2713", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    MilestoneState.CURRENT -> {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "PLAY",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    MilestoneState.LOCKED -> {
                        Text(
                            text = "\uD83D\uDD12",
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}
