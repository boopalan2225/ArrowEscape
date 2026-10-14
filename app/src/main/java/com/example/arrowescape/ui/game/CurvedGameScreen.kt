package com.example.arrowescape.ui.game

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.arrowescape.game.curve.CurvedJourney
import com.example.arrowescape.game.model.GameStatus
import com.example.arrowescape.ui.components.*
import com.example.arrowescape.ui.dev.DevLevelBar

/**
 * Main game screen for curved arrow puzzles.
 * Uses bezier-curve-based arrows with organic, flowing patterns.
 */
@Composable
fun CurvedGameScreen(
    viewModel: CurvedGameViewModel,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsState()
    val escapingArrows by viewModel.escapingArrows.collectAsState()
    val bumpingArrows by viewModel.bumpingArrows.collectAsState()
    val boardWidth by viewModel.boardWidth.collectAsState()
    val boardHeight by viewModel.boardHeight.collectAsState()
    val hintsRemaining by viewModel.hintsRemaining.collectAsState()
    val combo by viewModel.combo.collectAsState()
    val prefs by viewModel.prefs.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        onDispose { viewModel.saveGameState() }
    }

    val shouldBlur = gameState.status == GameStatus.OUT_OF_LIVES ||
            gameState.status == GameStatus.LEVEL_COMPLETE ||
            gameState.isStuck

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
            // Header band
            Surface(
                shape = RoundedCornerShape(
                    bottomStart = 28.dp, bottomEnd = 28.dp
                ),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                ) {
                    GameTopBar(
                        level = gameState.level,
                        onBackClick = onBackClick
                    )

                    GameStatsRow(
                        remainingArrows = gameState.remainingCount,
                        lives = gameState.lives,
                        difficultyLabel = gameState.difficulty
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Game board - centered with the curved arrows
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CurvedGameBoard(
                    arrows = gameState.arrows,
                    boardWidth = boardWidth,
                    boardHeight = boardHeight,
                    escapingArrows = escapingArrows,
                    bumpingArrows = bumpingArrows,
                    hintArrowId = gameState.hintArrowId,
                    resetKey = gameState.level,
                    isArrowFree = viewModel::isArrowFree,
                    exitPointOf = viewModel::exitPointOf,
                    onArrowTap = { arrow -> viewModel.onArrowTap(arrow) }
                )
            }

            if (prefs.devMode) {
                DevLevelBar(
                    level = gameState.level,
                    shapeName = gameState.shapeName,
                    difficulty = gameState.difficulty,
                    arrowCount = gameState.arrows.size,
                    canGoPrevious = gameState.level > 1,
                    canGoNext = gameState.level < CurvedJourney.totalLevels,
                    onPrevious = { viewModel.devStepLevel(-1) },
                    onNext = { viewModel.devStepLevel(+1) }
                )
            }

            GameBottomBar(
                shapeName = gameState.shapeName,
                hintsRemaining = hintsRemaining,
                onHintClick = { viewModel.useHint() }
            )
        }

        // Combo badge
        ComboBadge(
            combo = combo,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 120.dp)
        )

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

        // Stuck-puzzle overlay: no remaining arrow can ever escape.
        AnimatedVisibility(
            visible = gameState.isStuck,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                PuzzleStuckModal(
                    onSkipClick = { viewModel.onSkipStuckLevel() },
                    onRetryClick = { viewModel.onRestartLevel() }
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
                    mistakes = gameState.mistakes,
                    shapeName = gameState.shapeName,
                    difficultyLabel = gameState.difficulty,
                    onNextLevel = {
                        activity?.let { act ->
                            viewModel.showInterstitialIfNeeded(act) { viewModel.onNextLevel() }
                        } ?: viewModel.onNextLevel()
                    },
                    onHome = onHomeClick
                )
            }
        }
    }
}
