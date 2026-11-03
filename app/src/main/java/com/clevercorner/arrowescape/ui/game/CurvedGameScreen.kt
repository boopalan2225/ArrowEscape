package com.clevercorner.arrowescape.ui.game

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
import com.clevercorner.arrowescape.game.curve.CurvedArrow
import com.clevercorner.arrowescape.game.curve.CurvedJourney
import com.clevercorner.arrowescape.game.model.GameStatus
import com.clevercorner.arrowescape.ui.components.*
import com.clevercorner.arrowescape.review.ReviewPromptEffect
import com.clevercorner.arrowescape.ui.dev.DevLevelBar
import com.clevercorner.arrowescape.ui.tutorial.SpotlightTarget
import com.clevercorner.arrowescape.ui.tutorial.spotlightTarget

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
                    modifier = Modifier
                        .statusBarsPadding()
                        .background(
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
                        subtitle = "${gameState.difficulty} \u00B7 ${gameState.shapeName}",
                        onBackClick = onBackClick
                    )

                    GameStatsRow(
                        modifier = Modifier.spotlightTarget(SpotlightTarget.LIVES),
                        remainingArrows = gameState.remainingCount,
                        lives = gameState.lives
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Game board - centered with the curved arrows
            CurvedBoardArea(
                viewModel = viewModel,
                level = gameState.level,
                arrows = gameState.arrows,
                hintArrowId = gameState.hintArrowId,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .spotlightTarget(SpotlightTarget.BOARD)
            )

            if (prefs.devMode) {
                DevLevelBar(
                    level = gameState.level,
                    shapeName = gameState.shapeName,
                    difficulty = gameState.difficulty,
                    arrowCount = gameState.arrows.size,
                    canGoPrevious = gameState.level > 1,
                    canGoNext = gameState.level < CurvedJourney.totalLevels,
                    onPrevious = { viewModel.stepLevel(-1) },
                    onNext = { viewModel.stepLevel(+1) }
                )
            }

            GameBottomBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .spotlightTarget(SpotlightTarget.HINT),
                hintsRemaining = hintsRemaining,
                isHintAdAvailable = viewModel.isRewardedAdAvailable(),
                onPreviousLevel = { viewModel.stepLevel(-1) }
                    .takeIf { gameState.level > 1 },
                onNextLevel = { viewModel.stepLevel(+1) }
                    .takeIf { gameState.level < prefs.highestLevel },
                onHintClick = {
                    if (hintsRemaining > 0) {
                        viewModel.useHint()
                    } else {
                        activity?.let { viewModel.onWatchAdForHints(it) }
                    }
                }
            )
        }

        // Combo badge
        ComboBadge(
            combo = combo,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
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

        // Same celebration as the grid mode, same reason it lives out here:
        // the scrim's scale-in must not touch the falling paper.
        ConfettiBurst(
            active = gameState.status == GameStatus.LEVEL_COMPLETE,
            modifier = Modifier.fillMaxSize()
        )

        // Asks for a store review when the player is clearly having fun.
        ReviewPromptEffect(viewModel.reviewPrompter)
    }
}

/**
 * The board, and the three things that change while arrows are in flight.
 *
 * Split out deliberately. Escaping and bumping arrows update roughly every
 * frame of an animation, and reading them in [CurvedGameScreen] meant the
 * whole screen body - header, stats, dev bar, bottom bar, every overlay -
 * re-ran at animation rate for state only the board cares about. Reading
 * them here confines that churn to the subtree that has to redraw anyway.
 *
 * The callbacks are remembered for the same reason: a bound method
 * reference allocates a fresh object on every recomposition, and a board
 * whose parameters are never equal is a board Compose can never skip.
 */
@Composable
private fun CurvedBoardArea(
    viewModel: CurvedGameViewModel,
    level: Int,
    arrows: List<CurvedArrow>,
    hintArrowId: Int?,
    modifier: Modifier = Modifier
) {
    val escapingArrows by viewModel.escapingArrows.collectAsState()
    val bumpingArrows by viewModel.bumpingArrows.collectAsState()
    val boardWidth by viewModel.boardWidth.collectAsState()
    val boardHeight by viewModel.boardHeight.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()

    val isArrowFree = remember(viewModel) { viewModel::isArrowFree }
    val exitPointOf = remember(viewModel) { viewModel::exitPointOf }
    val onArrowTap = remember(viewModel) { viewModel::onArrowTap }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isGenerating) {
            BoardPlaceholder()
        } else {
            CurvedGameBoard(
                arrows = arrows,
                boardWidth = boardWidth,
                boardHeight = boardHeight,
                escapingArrows = escapingArrows,
                bumpingArrows = bumpingArrows,
                hintArrowId = hintArrowId,
                resetKey = level,
                isArrowFree = isArrowFree,
                exitPointOf = exitPointOf,
                onArrowTap = onArrowTap
            )
        }
    }
}
