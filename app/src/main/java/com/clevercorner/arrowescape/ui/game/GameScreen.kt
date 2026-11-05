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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.game.level.DifficultyConfig
import com.clevercorner.arrowescape.game.level.LevelShape
import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.Direction
import com.clevercorner.arrowescape.game.model.GameStatus
import com.clevercorner.arrowescape.ui.components.*
import com.clevercorner.arrowescape.review.ReviewPromptEffect
import com.clevercorner.arrowescape.ui.dev.DevLevelBar
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import com.clevercorner.arrowescape.ui.tutorial.SpotlightTarget
import com.clevercorner.arrowescape.ui.tutorial.spotlightTarget

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsState()
    val shapeName by viewModel.shapeName.collectAsState()
    val hintsRemaining by viewModel.hintsRemaining.collectAsState()
    val combo by viewModel.combo.collectAsState()
    val levelMetrics by viewModel.levelMetrics.collectAsState()
    val prefs by viewModel.prefs.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity

    // Level is now started from MainActivity via navigation arguments

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
            // Distinct branded header band (rounded bottom + subtle gradient)
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
                        // Difficulty and shape are static facts about the
                        // level - one quiet line, not two loud chips.
                        subtitle = "${gameState.difficultyLabel} \u00B7 $shapeName",
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

            // Game board - centered, fills available space
            ClassicBoardArea(
                viewModel = viewModel,
                level = gameState.level,
                arrows = gameState.arrows,
                board = gameState.board,
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
                    shapeName = shapeName,
                    difficulty = gameState.difficultyLabel,
                    tightness = levelMetrics.tightness,
                    longestChain = levelMetrics.longestChain,
                    arrowCount = gameState.arrows.size
                )
            }

            GameBottomBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .spotlightTarget(SpotlightTarget.HINT),
                hintsRemaining = hintsRemaining,
                isHintAdAvailable = viewModel.isRewardedAdAvailable(),
                // Back to anything already played; forward only as far as
                // the player has actually earned - except in dev mode,
                // which trades that gate for the real ceiling (classic
                // genuinely ends at TOTAL_LEVELS) so testing isn't stuck
                // behind whatever the player's own save file happens to say.
                onPreviousLevel = { viewModel.stepLevel(-1) }
                    .takeIf { gameState.level > 1 },
                onNextLevel = { viewModel.stepLevel(+1) }
                    .takeIf {
                        val ceiling = if (prefs.devMode) DifficultyConfig.TOTAL_LEVELS else prefs.highestLevel
                        gameState.level < ceiling
                    },
                onHintClick = {
                    if (hintsRemaining > 0) {
                        viewModel.useHint()
                    } else {
                        activity?.let { viewModel.onWatchAdForHints(it) }
                    }
                }
            )
        }

        // Combo indicator (floats near the top of the board)
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
                    shapeName = shapeName,
                    difficultyLabel = gameState.difficultyLabel,
                    onNextLevel = {
                        activity?.let { act ->
                            viewModel.showInterstitialIfNeeded(act) { viewModel.onNextLevel() }
                        } ?: viewModel.onNextLevel()
                    },
                    onHome = onHomeClick
                )
            }
        }

        // Celebration rains over everything, including the overlay card, and
        // sits outside the AnimatedVisibility so the scrim's scale-in does
        // not squeeze the paper on its way down.
        ConfettiBurst(
            active = gameState.status == GameStatus.LEVEL_COMPLETE,
            modifier = Modifier.fillMaxSize()
        )

        // Asks for a store review when the player is clearly having fun.
        ReviewPromptEffect(viewModel.reviewPrompter)
    }
}

/**
 * The board, and the state that changes while arrows are in flight.
 *
 * Same reasoning as the curved mode's `CurvedBoardArea`: escaping and
 * shaking arrows update about once a frame, and reading them in
 * [GameScreen] re-ran the entire screen body at animation rate for state
 * only the board cares about. The tap callback is remembered because a
 * freshly allocated lambda on every recomposition is a board Compose can
 * never skip.
 */
@Composable
private fun ClassicBoardArea(
    viewModel: GameViewModel,
    level: Int,
    arrows: List<Arrow>,
    board: Board,
    hintArrowId: Int?,
    modifier: Modifier = Modifier
) {
    val escapingArrows by viewModel.escapingArrows.collectAsState()
    val shakingArrows by viewModel.shakingArrows.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val onArrowTap = remember(viewModel) { viewModel::onArrowTap }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isGenerating) {
            BoardPlaceholder()
        } else {
            GameBoard(
                arrows = arrows,
                board = board,
                resetKey = level,
                escapingArrows = escapingArrows,
                shakingArrows = shakingArrows,
                hintArrowId = hintArrowId,
                onArrowTap = onArrowTap
            )
        }
    }
}

// ======================= PREVIEWS =======================

/**
 * Preview-friendly version of game screen content (no ViewModel needed).
 */
@Composable
private fun GameScreenPreviewContent(
    level: Int = 5,
    arrows: List<Arrow>,
    board: Board = Board(8, 8),
    lives: Int = 3,
    shapeName: String = "Spiral",
    difficultyLabel: String = "Medium",
    hintsRemaining: Int = 2,
    showLevelComplete: Boolean = false,
    showOutOfLives: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showLevelComplete || showOutOfLives) Modifier.blur(8.dp) else Modifier)
        ) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
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
                        level = level,
                        subtitle = "$difficultyLabel \u00B7 $shapeName",
                        onBackClick = {}
                    )
                    GameStatsRow(
                        remainingArrows = arrows.count { it.isActive },
                        lives = lives
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                GameBoard(
                    arrows = arrows,
                    board = board,
                    resetKey = level,
                    escapingArrows = emptyMap(),
                    shakingArrows = emptySet(),
                    hintArrowId = arrows.firstOrNull()?.id,
                    onArrowTap = {}
                )
            }

            GameBottomBar(
                hintsRemaining = hintsRemaining,
                onHintClick = {}
            )
        }

        if (showLevelComplete) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                LevelCompleteOverlay(
                    level = level,
                    mistakes = 1,
                    shapeName = shapeName,
                    difficultyLabel = difficultyLabel,
                    onNextLevel = {},
                    onHome = {}
                )
            }
        }

        if (showOutOfLives) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                OutOfLivesModal(
                    clearedPercentage = 67,
                    isAdAvailable = true,
                    onContinueClick = {},
                    onRestartClick = {}
                )
            }
        }
    }
}

private fun createPreviewArrows(shape: LevelShape, gridSize: Int = 8): List<Arrow> {
    val validCells = shape.validCells(gridSize, gridSize)
    if (validCells.isEmpty()) return emptyList()
    
    val directions = Direction.entries
    return validCells.filterIndexed { index, _ -> index % 3 == 0 }.take(10).mapIndexed { idx, (row, col) ->
        Arrow(
            id = idx,
            cells = listOf(Cell(row, col)),
            direction = directions[idx % directions.size],
            isActive = true
        )
    }
}

@Preview(showBackground = true, name = "Game - Playing")
@Composable
private fun GameScreenPreviewPlaying() {
    ArrowEscapeTheme {
        GameScreenPreviewContent(
            level = 5,
            arrows = createPreviewArrows(LevelShape.BUTTERFLY, 8),
            shapeName = "Spiral",
            difficultyLabel = "Hard"
        )
    }
}

@Preview(showBackground = true, name = "Game - Level Complete")
@Composable
private fun GameScreenPreviewLevelComplete() {
    ArrowEscapeTheme(darkTheme = true) {
        GameScreenPreviewContent(
            level = 12,
            arrows = emptyList(),
            shapeName = "Crown",
            difficultyLabel = "Hard",
            showLevelComplete = true
        )
    }
}

@Preview(showBackground = true, name = "Game - Out of Lives")
@Composable
private fun GameScreenPreviewOutOfLives() {
    ArrowEscapeTheme {
        GameScreenPreviewContent(
            level = 8,
            arrows = createPreviewArrows(LevelShape.RING),
            lives = 0,
            shapeName = "Ring",
            difficultyLabel = "Medium",
            showOutOfLives = true
        )
    }
}
