package com.example.arrowescape.ui.home

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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

@Composable
fun HomeScreen(
    prefs: GamePreferences.Prefs,
    onPlayClick: () -> Unit,
    onJourneyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // Animated logo area
            AnimatedLogo()

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "ARROW",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )
            Text(
                text = "ESCAPE",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 4.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "TAP  ·  VANISH  ·  ESCAPE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Level info card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.shadow(4.dp, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "LEVEL",
                        style = MaterialTheme.typography.labelMedium,
                        color = gameColors.textSecondary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "${prefs.currentLevel}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (prefs.highestLevel > prefs.currentLevel) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Best: ${prefs.highestLevel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = gameColors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Play button with pulse animation
            PulsatingPlayButton(onClick = onPlayClick)

            Spacer(modifier = Modifier.height(16.dp))

            // Journey / milestone map button
            OutlinedButton(
                onClick = onJourneyClick,
                modifier = Modifier
                    .width(200.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "\uD83D\uDDFA", fontSize = 18.sp)
                    Text(
                        text = "Journey Map",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Settings button
            TextButton(
                onClick = onSettingsClick,
                modifier = Modifier.padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "\u2699",
                        fontSize = 20.sp,
                        color = gameColors.textSecondary
                    )
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.bodyLarge,
                        color = gameColors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats summary
            if (prefs.totalLevelsCompleted > 0) {
                Text(
                    text = "${prefs.totalLevelsCompleted} levels completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = gameColors.textSecondary.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.weight(0.3f))
        }
    }
}

@Composable
private fun AnimatedLogo() {
    val gameColors = ArrowEscapeTheme.gameColors
    
    // Continuous rotation animation
    val infiniteTransition = rememberInfiniteTransition(label = "logoAnimation")
    
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation1"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer rotating ring of arrows
        Box(modifier = Modifier.rotate(rotation1)) {
            // Arrow indicators around the logo
            listOf(0f, 90f, 180f, 270f).forEachIndexed { index, angle ->
                val color = when (index) {
                    0 -> gameColors.arrowUp
                    1 -> gameColors.arrowRight
                    2 -> gameColors.arrowDown
                    else -> gameColors.arrowLeft
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .rotate(angle)
                        .offset(y = (-45).dp)
                ) {
                    Text(
                        text = "\u2191",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }

        // Center circle
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "→",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(x = 1.dp, y = (-1).dp) // Optical centering adjustment
            )
        }
    }
}

@Composable
private fun PulsatingPlayButton(onClick: () -> Unit) {
    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(contentAlignment = Alignment.Center) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(scale)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * 0.3f)
                )
        )

        // Main button
        Button(
            onClick = onClick,
            modifier = Modifier
                .width(160.dp)
                .height(56.dp)
                .scale(scale),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Text(
                text = "PLAY",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
