package com.example.arrowescape.ui.game

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.model.GameStatus
import com.example.arrowescape.ui.components.*

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

    val context = LocalContext.current
    val activity = context as? Activity

    // Start level when screen is shown
    LaunchedEffect(Unit) {
        val prefs = viewModel.prefs.value
        viewModel.startLevel(prefs.currentLevel)
    }

    // Save game state when leaving
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveGameState()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                    .padding(horizontal = 16.dp),
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
        if (gameState.status == GameStatus.OUT_OF_LIVES) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
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
        if (gameState.status == GameStatus.LEVEL_COMPLETE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
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
