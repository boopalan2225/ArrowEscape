package com.example.arrowescape.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * Animated combo badge - pops in when the player chains valid moves.
 */
@Composable
fun ComboBadge(
    combo: Int,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val visible = combo >= 2

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = androidx.compose.animation.scaleIn(
            initialScale = 0.5f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut(targetScale = 0.5f) +
                androidx.compose.animation.fadeOut()
    ) {
        // Bounce each time the combo increments
        val bounce by animateFloatAsState(
            targetValue = combo.toFloat(),
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "comboBounce"
        )
        val scale = 1f + (0.12f * kotlin.math.sin(bounce * 3f))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = comboColor(combo, gameColors),
            shadowElevation = 8.dp,
            modifier = Modifier.scale(scale)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "\uD83D\uDD25", fontSize = 20.sp)
                Text(
                    text = "COMBO x$combo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

private fun comboColor(combo: Int, gameColors: com.example.arrowescape.ui.theme.GameColors): Color {
    return when {
        combo >= 8 -> gameColors.arrowLeft   // red-hot
        combo >= 5 -> gameColors.warning     // orange
        else -> gameColors.success           // green
    }
}

/**
 * Displays the current lives as animated hearts.
 */
@Composable
fun LivesDisplay(
    lives: Int,
    maxLives: Int = 3,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(maxLives) { index ->
            val isFilled = index < lives
            
            // Animate heart scale when losing a life
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1f else 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "heartScale"
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                // Heart shape using text (can be replaced with vector)
                Text(
                    text = if (isFilled) "\u2764" else "\u2661",
                    fontSize = 22.sp,
                    color = if (isFilled) gameColors.heartFull else gameColors.heartEmpty
                )
            }
        }
    }
}

/**
 * Top bar for the game screen: back button, level, and settings/pause.
 */
@Composable
fun GameTopBar(
    level: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "Level $level",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Spacer to balance the back button (settings lives on home)
        Spacer(modifier = Modifier.size(40.dp))
    }
}

/**
 * Stats row: arrow counter chip | lives | difficulty badge.
 */
@Composable
fun GameStatsRow(
    remainingArrows: Int,
    lives: Int,
    difficultyLabel: String,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Arrow counter chip
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "\u2197",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                AnimatedContent(
                    targetState = remainingArrows,
                    transitionSpec = {
                        (slideInVertically { -it } + fadeIn()) togetherWith
                                (slideOutVertically { it } + fadeOut())
                    },
                    label = "count"
                ) { count ->
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Lives
        LivesDisplay(lives = lives)

        // Difficulty badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = difficultyColor(difficultyLabel, gameColors).copy(alpha = 0.15f)
        ) {
            Text(
                text = difficultyLabel,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = difficultyColor(difficultyLabel, gameColors)
            )
        }
    }
}

private fun difficultyColor(label: String, gameColors: com.example.arrowescape.ui.theme.GameColors): Color {
    return when (label) {
        "Easy" -> gameColors.success
        "Normal" -> gameColors.arrowDown
        "Hard" -> gameColors.warning
        else -> gameColors.arrowLeft
    }
}

/**
 * Bottom bar with a hint button and the current shape name.
 */
@Composable
fun GameBottomBar(
    shapeName: String,
    hintsRemaining: Int,
    onHintClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shape label
        Column {
            Text(
                text = "SHAPE",
                style = MaterialTheme.typography.labelSmall,
                color = gameColors.textSecondary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = shapeName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Hint button with badge
        Box(contentAlignment = Alignment.TopEnd) {
            FilledIconButton(
                onClick = onHintClick,
                enabled = hintsRemaining > 0,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = "\uD83D\uDCA1", fontSize = 22.sp)
            }

            if (hintsRemaining > 0) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$hintsRemaining",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Modal for out of lives state with polished design.
 */
@Composable
fun OutOfLivesModal(
    clearedPercentage: Int,
    isAdAvailable: Boolean,
    onContinueClick: () -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = gameColors.cardBackground
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sad emoji/icon
            Text(
                text = "\uD83D\uDE1E",
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Out of Lives!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clearedPercentage / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$clearedPercentage% cleared",
                style = MaterialTheme.typography.bodyMedium,
                color = gameColors.textSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isAdAvailable) {
                // Continue button (primary action)
                Button(
                    onClick = onContinueClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "\u25B6",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Watch Ad to Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Restore all 3 lives",
                    style = MaterialTheme.typography.bodySmall,
                    color = gameColors.textSecondary
                )
            } else {
                // Ad not available message
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = gameColors.warning.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Continue unavailable right now",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = gameColors.warning,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Restart button (secondary action)
            OutlinedButton(
                onClick = onRestartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    text = "Restart Level",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

/**
 * Level complete overlay with celebration animation.
 */
@Composable
fun LevelCompleteOverlay(
    level: Int,
    timeSeconds: Int,
    onNextLevel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    // Scale animation for the card
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .scale(scale)
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = gameColors.cardBackground
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success checkmark with glow effect
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                gameColors.success.copy(alpha = 0.2f),
                                gameColors.success.copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(gameColors.success),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\u2713",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Level Complete!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Level $level cleared",
                style = MaterialTheme.typography.bodyLarge,
                color = gameColors.textSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Time display
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "\u23F1",
                        fontSize = 20.sp
                    )
                    val minutes = timeSeconds / 60
                    val seconds = timeSeconds % 60
                    Text(
                        text = String.format("%d:%02d", minutes, seconds),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Next level button
            Button(
                onClick = onNextLevel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Next Level",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\u2192",
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
