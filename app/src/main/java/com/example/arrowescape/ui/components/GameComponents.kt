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
import androidx.compose.ui.draw.blur
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
                text = "←",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(x = (-1).dp, y = (-5).dp) // Optical centering adjustment
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
    mistakes: Int,
    shapeName: String,
    difficultyLabel: String,
    onNextLevel: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors
    
    // Calculate stars based on mistakes
    val stars = when {
        mistakes == 0 -> 3
        mistakes <= 2 -> 2
        else -> 1
    }
    
    // Calculate score
    val timeBonus = maxOf(0, 100 - timeSeconds)
    val score = maxOf(10, 100 - (mistakes * 15) + timeBonus / 2)

    // Animation states
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Card scale animation
    val cardScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )
    
    // Star animations - staggered entrance
    val star1Scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "star1"
    )
    val star2Scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "star2"
    )
    val star3Scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "star3"
    )
    val starScales = listOf(star1Scale, star2Scale, star3Scale)
    
    // Continuous star glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "starPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Theme-aware colors
    val starGold = Color(0xFFFFD700)
    val starEmpty = gameColors.textSecondary.copy(alpha = 0.3f)
    val cardBg = gameColors.cardBackground
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val accentBlue = MaterialTheme.colorScheme.primary
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = gameColors.textSecondary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .scale(cardScale)
            .shadow(24.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated stars with glow
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isFilled = index < stars
                    val starScale = starScales[index]
                    
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.scale(starScale)
                    ) {
                        // Glow effect for filled stars
                        if (isFilled && starScale > 0.5f) {
                            Text(
                                text = "\u2605",
                                fontSize = 56.sp,
                                color = starGold.copy(alpha = glowAlpha),
                                modifier = Modifier.blur(12.dp)
                            )
                        }
                        // Star with bounce
                        Text(
                            text = "\u2605",
                            fontSize = 52.sp,
                            color = if (isFilled) starGold else starEmpty,
                            modifier = Modifier.scale(if (isFilled) 1f + (starScale * 0.1f) else 1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ESCAPED! title with gradient effect
            Text(
                text = "ESCAPED!",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = accentBlue,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Level info subtitle
            Text(
                text = "Level $level \u00b7 $shapeName \u00b7 $difficultyLabel",
                style = MaterialTheme.typography.bodyLarge,
                color = textSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Stats card with animated counter effect
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Score with animation
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Animated score counter
                        val animatedScore by animateIntAsState(
                            targetValue = if (visible) score else 0,
                            animationSpec = tween(800, delayMillis = 400),
                            label = "scoreAnim"
                        )
                        Text(
                            text = "$animatedScore",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentBlue
                        )
                        Text(
                            text = "SCORE",
                            style = MaterialTheme.typography.labelMedium,
                            color = textSecondary,
                            letterSpacing = 2.sp
                        )
                    }
                    
                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(50.dp)
                            .background(borderColor)
                    )
                    
                    // Stars earned
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$stars",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = starGold
                            )
                            Text(
                                text = "\u2605",
                                fontSize = 28.sp,
                                color = starGold,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        Text(
                            text = "STARS",
                            style = MaterialTheme.typography.labelMedium,
                            color = textSecondary,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Home button
                OutlinedButton(
                    onClick = onHome,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                ) {
                    Text(
                        text = "\u2302",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                // Next Level button with pulse
                val buttonScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.03f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "buttonPulse"
                )
                
                Button(
                    onClick = onNextLevel,
                    modifier = Modifier
                        .weight(1.5f)
                        .height(56.dp)
                        .scale(buttonScale),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentBlue
                    )
                ) {
                    Text(
                        text = "NEXT LEVEL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "\u203a",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
