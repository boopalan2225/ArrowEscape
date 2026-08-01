package com.example.arrowescape.ui.game

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    val levelShape by viewModel.levelShape.collectAsState()
    val hintsRemaining by viewModel.hintsRemaining.collectAsState()
    val prefs by viewModel.prefs.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        val startLevel = viewModel.consumePendingLevel() ?: prefs.currentLevel
        viewModel.startLevel(startLevel)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.saveGameState() }
    }

    val shouldBlur = gameState.status == GameStatus.OUT_OF_LIVES ||
            gameState.status == GameStatus.LEVEL_COMPLETE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (shouldBlur) Modifier.blur(8.dp) else Modifier)
        ) {
            GameTopBar(
                level = gameState.level,
                onBackClick = onBackClick
            )

            GameStatsRow(
                remainingArrows = gameState.remainingCount,
                lives = gameState.lives,
                difficultyLabel = gameState.difficultyLabel
            )

            // Game board - centered, fills available space
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
                    escapingArrows = escapingArrows,
                    shakingArrows = shakingArrows,
                    hintArrowId = gameState.hintArrowId,
                    onArrowTap = { arrow -> viewModel.onArrowTap(arrow) }
                )
            }

            GameBottomBar(
                shapeName = levelShape.displayName,
                hintsRemaining = hintsRemaining,
                onHintClick = { viewModel.useHint() }
            )
        }

        // Out of lives overlay
        AnimatedVisibility(
            visible = gameState.status == GameStatus.OUT_OF_LIVES,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150))
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
                    onContinueClick = { activity?.let { viewModel.onContinueWithAd(it) } },
                    onRestartClick = { viewModel.onRestartLevel() }
                )
            }
        }

        // Level complete overlay
        AnimatedVisibility(
            visible = gameState.status == GameStatus.LEVEL_COMPLETE,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)),
            exit = fadeOut(tween(150))
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
                            viewModel.showInterstitialIfNeeded(act) { viewModel.onNextLevel() }
                        } ?: viewModel.onNextLevel()
                    }
                )
            }
        }
    }
}
