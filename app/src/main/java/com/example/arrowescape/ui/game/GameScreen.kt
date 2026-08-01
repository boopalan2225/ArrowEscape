package com.example.arrowescape.ui.game

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.model.GameStatus
import com.example.arrowescape.ui.components.*
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsState()
    val escapingArrows by viewModel.escapingArrows.collectAsState()
    val shakingArrows by viewModel.shakingArrows.collectAsState()
    val gridRows by viewModel.gridRows.collectAsState()
    val gridCols by viewModel.gridCols.collectAsState()
    val prefs by viewModel.prefs.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val gameColors = ArrowEscapeTheme.gameColors

    // Start level when screen is shown
    LaunchedEffect(Unit) {
        viewModel.startLevel(prefs.currentLevel)
    }

    // Save game state when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveGameState()
        }
    }

    // Determine if we should blur the background
    val shouldBlur = gameState.status == GameStatus.OUT_OF_LIVES ||
            gameState.status == GameStatus.LEVEL_COMPLETE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main game content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (shouldBlur) Modifier.blur(8.dp) else Modifier
                )
        ) {
            // Top bar with level, lives, back button
            GameTopBar(
                level = gameState.level,
                lives = gameState.lives,
                onBackClick = onBackClick
            )

            // Game board - centered with padding
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                GameBoard(
                    arrows = gameState.arrows,
                    gridRows = gridRows,
                    gridCols = gridCols,
                    animatingArrows = escapingArrows.keys + shakingArrows,
                    escapingArrows = escapingArrows,
                    shakingArrows = shakingArrows,
                    onArrowTap = { arrow ->
                        viewModel.onArrowTap(arrow)
                    }
                )
            }

            // Bottom bar with remaining count
            GameBottomBar(
                remainingArrows = gameState.remainingCount
            )
        }

        // Overlay for out of lives
        AnimatedVisibility(
            visible = gameState.status == GameStatus.OUT_OF_LIVES,
            enter = fadeIn(animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)) +
                    scaleOut(targetScale = 0.9f, animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                OutOfLivesModal(
                    clearedPercentage = gameState.clearedPercentage,
                    isAdAvailable = viewModel.isRewardedAdAvailable(),
                    onContinueClick = {
                        activity?.let { viewModel.onContinueWithAd(it) }
                    },
                    onRestartClick = {
                        viewModel.onRestartLevel()
                    }
                )
            }
        }

        // Overlay for level complete
        AnimatedVisibility(
            visible = gameState.status == GameStatus.LEVEL_COMPLETE,
            enter = fadeIn(animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                LevelCompleteOverlay(
                    level = gameState.level,
                    timeSeconds = ((System.currentTimeMillis() - gameState.startTimeMillis) / 1000).toInt(),
                    onNextLevel = {
                        activity?.let { act ->
                            viewModel.showInterstitialIfNeeded(act) {
                                viewModel.onNextLevel()
                            }
                        } ?: viewModel.onNextLevel()
                    }
                )
            }
        }
    }
}
