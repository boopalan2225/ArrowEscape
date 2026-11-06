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
import com.clevercorner.arrowescape.BuildConfig
import com.clevercorner.arrowescape.ads.BannerAdView
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.game.curve.CurvedArrow
import com.clevercorner.arrowescape.game.curve.CurvedJourney
import com.clevercorner.arrowescape.game.model.GameStatus
import com.clevercorner.arrowescape.ui.components.*
import com.clevercorner.arrowescape.review.ReviewPromptEffect
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
        GameScaffold(
            modifier = Modifier
                .fillMaxSize()
                .then(if (shouldBlur) Modifier.blur(8.dp) else Modifier),
            topBar = {
                GameTopBar(
                    modifier = Modifier.readableWidth(BoardSizing.maxExtent),
                    level = gameState.level,
                    subtitle = "${gameState.difficulty} \u00B7 ${gameState.shapeName}",
                    onBackClick = onBackClick,
                    trailing = { BoardLegendButton(GameMode.CURVED) }
                )
            },
            stats = {
                GameStatsRow(
                    // Same reasoning as the classic screen: the chrome
                    // lines up with the board's ceiling, not the glass.
                    modifier = Modifier
                        .readableWidth(BoardSizing.maxExtent)
                        .spotlightTarget(SpotlightTarget.LIVES),
                    remainingArrows = gameState.remainingCount,
                    lives = gameState.lives
                )
            },
            board = { pane ->
                CurvedBoardArea(
                    viewModel = viewModel,
                    level = gameState.level,
                    arrows = gameState.arrows,
                    hintArrowId = gameState.hintArrowId,
                    modifier = pane
                )
            },
            controls = {
                GameBottomBar(
                    modifier = Modifier
                        .readableWidth(BoardSizing.maxExtent)
                        .spotlightTarget(SpotlightTarget.HINT),
                    hintsRemaining = hintsRemaining,
                    isHintAdAvailable = viewModel.isRewardedAdAvailable(),
                    // Back to anything already played; forward only as far as
                    // the player has actually earned - except in dev mode,
                    // which trades that gate for the real ceiling (curved
                    // genuinely ends at CurvedJourney.authoredLevelCount, same
                    // as classic's own dev ceiling) so testing isn't stuck
                    // behind whatever the player's own save file happens to say.
                    onPreviousLevel = { viewModel.stepLevel(-1) }
                        .takeIf { gameState.level > 1 },
                    onNextLevel = { viewModel.stepLevel(+1) }
                        .takeIf {
                            val ceiling = if (prefs.devMode) CurvedJourney.authoredLevelCount else prefs.highestLevel
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
            },
            banner = {
                // True bottom of the screen, below everything else including
                // the hint bar - same spot the system nav bar sits just under.
                BannerAdView(
                    adUnitId = BuildConfig.BANNER_AD_UNIT_ID,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        )

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
                        // The authored catalogue ends somewhere (see
                        // CurvedJourney.authoredLevelCount), and this overlay
                        // has no "that was the last one" state of its own to
                        // show instead - it was built back when curved
                        // content had no end. Routing home rather than
                        // calling onNextLevel's no-op past the ceiling means
                        // the tap still goes somewhere: Home shows the
                        // player's progress at a genuine 100%, with the
                        // Journey Map's own "more is coming" card one tap
                        // away. A dedicated end-of-catalogue moment on this
                        // overlay itself would read better; this is the
                        // interim until that is designed.
                        val finishedCatalogue = gameState.level >= CurvedJourney.authoredLevelCount
                        val proceed = if (finishedCatalogue) onHomeClick else viewModel::onNextLevel
                        activity?.let { act ->
                            viewModel.showInterstitialIfNeeded(act, proceed)
                        } ?: proceed()
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
        // Tags the board rather than the slot it is centred in - see the
        // same move in the classic screen's board area.
        val spotlight = Modifier.spotlightTarget(SpotlightTarget.BOARD)

        if (isGenerating) {
            BoardPlaceholder(modifier = spotlight)
        } else {
            CurvedGameBoard(
                modifier = spotlight,
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
