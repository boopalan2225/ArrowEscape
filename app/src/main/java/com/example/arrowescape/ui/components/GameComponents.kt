package com.example.arrowescape.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.ui.theme.HeartEmpty
import com.example.arrowescape.ui.theme.HeartFull

/**
 * Displays the current lives as hearts.
 */
@Composable
fun LivesDisplay(
    lives: Int,
    maxLives: Int = 3,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(maxLives) { index ->
            Text(
                text = if (index < lives) "" else "",
                fontSize = 24.sp,
                color = if (index < lives) HeartFull else HeartEmpty
            )
        }
    }
}

/**
 * Top bar for the game screen showing level, lives, and back button.
 */
@Composable
fun GameTopBar(
    level: Int,
    lives: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBackClick) {
            Text("←", fontSize = 24.sp)
        }

        Text(
            text = "Level $level",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        LivesDisplay(lives = lives)
    }
}

/**
 * Bottom info bar showing remaining arrows.
 */
@Composable
fun GameBottomBar(
    remainingArrows: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$remainingArrows remaining",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Modal for out of lives state.
 */
@Composable
fun OutOfLivesModal(
    clearedPercentage: Int,
    isAdAvailable: Boolean,
    onContinueClick: () -> Unit,
    onRestartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "OUT OF LIVES",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You cleared $clearedPercentage%",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isAdAvailable) {
                Button(
                    onClick = onContinueClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Watch an ad to restore 3 lives",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Text(
                    text = "Reward unavailable right now",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onRestartClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restart Level")
            }
        }
    }
}

/**
 * Level complete overlay.
 */
@Composable
fun LevelCompleteOverlay(
    level: Int,
    timeSeconds: Int,
    onNextLevel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LEVEL COMPLETE",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "",
                fontSize = 48.sp,
                color = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val minutes = timeSeconds / 60
            val seconds = timeSeconds % 60
            Text(
                text = "$minutes:${seconds.toString().padStart(2, '0')}",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNextLevel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("NEXT LEVEL")
            }
        }
    }
}
