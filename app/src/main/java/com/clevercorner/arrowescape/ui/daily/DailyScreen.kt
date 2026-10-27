package com.clevercorner.arrowescape.ui.daily

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.game.daily.DailyPuzzle
import com.clevercorner.arrowescape.game.model.GameStatus
import com.clevercorner.arrowescape.review.ReviewPromptEffect
import com.clevercorner.arrowescape.ui.components.BoardPlaceholder
import com.clevercorner.arrowescape.ui.components.BoardSkin
import com.clevercorner.arrowescape.ui.components.ConfettiBurst
import com.clevercorner.arrowescape.ui.components.CurvedGameBoard
import com.clevercorner.arrowescape.ui.components.OutOfLivesModal
import com.clevercorner.arrowescape.ui.components.PuzzleStuckModal
import com.clevercorner.arrowescape.ui.game.CurvedGameViewModel
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import java.time.format.DateTimeFormatter

private val TITLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")

/**
 * One puzzle a day, on a screen with nothing on it.
 *
 * Everything decorative that the journey screens carry - gradient header,
 * shape name, stars, dev bar, combo badge, themed scenery - is deliberately
 * absent from the board itself. The chrome is a sunrise header (the day, the
 * shape, lives, best run, streak), a shuffle timer and one row of actions.
 *
 * The gameplay is the curved engine untouched: same ViewModel, same board
 * component, same ads. Only the framing is different.
 */
@Composable
fun DailyScreen(
    puzzle: DailyPuzzle,
    prefs: GamePreferences.Prefs,
    viewModel: CurvedGameViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsState()
    val escapingArrows by viewModel.escapingArrows.collectAsState()
    val bumpingArrows by viewModel.bumpingArrows.collectAsState()
    val boardWidth by viewModel.boardWidth.collectAsState()
    val boardHeight by viewModel.boardHeight.collectAsState()
    val hintsRemaining by viewModel.hintsRemaining.collectAsState()
    val shuffleSecondsLeft by viewModel.shuffleSecondsLeft.collectAsState()
    val shuffleCount by viewModel.shuffleCount.collectAsState()
    val shufflePhase by viewModel.shufflePhase.collectAsState()
    val combo by viewModel.combo.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val activity = LocalContext.current as? Activity

    // Announce each re-arrangement briefly, keyed on the counter so repeat
    // shuffles each get their own flash.
    var flashVisible by remember { mutableStateOf(false) }
    LaunchedEffect(shuffleCount) {
        if (shuffleCount == 0) return@LaunchedEffect
        flashVisible = true
        delay(1100)
        flashVisible = false
    }

    // Snapshotted on entry. Solving the puzzle flips the stored flag, and
    // this must NOT re-evaluate mid-session or the board would vanish out
    // from under the win card.
    val playable = remember(puzzle.stamp) { prefs.daily.canPlay(puzzle.date) }

    LaunchedEffect(puzzle.stamp) { if (playable) viewModel.startDaily(puzzle) }

    // The shuffle clock belongs to this screen; it must not outlive it.
    DisposableEffect(Unit) {
        onDispose { viewModel.stopShuffleClock() }
    }

    // Reached today's puzzle after already solving it (deep link, back
    // stack, a stale Home button). One a day means one a day.
    if (!playable) {
        DailyClosedScreen(
            dateLabel = puzzle.date.format(TITLE_FORMAT),
            streak = prefs.daily.streak,
            onDone = onBackClick,
            modifier = modifier
        )
        return
    }

    val solved = gameState.status == GameStatus.LEVEL_COMPLETE
    val blurred = solved || gameState.status == GameStatus.OUT_OF_LIVES || gameState.isStuck

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurred) Modifier.blur(8.dp) else Modifier)
        ) {
            DailyHud(
                dateLabel = puzzle.date.format(TITLE_FORMAT),
                shapeName = gameState.shapeName,
                lives = gameState.lives,
                // Live: the run in progress counts the moment it beats the record.
                bestRun = maxOf(prefs.daily.bestRun, combo),
                streak = prefs.daily.streak,
                onBackClick = onBackClick
            )

            // The board takes every pixel the chrome does not need.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    BoardPlaceholder()
                } else {
                    CurvedGameBoard(
                        arrows = gameState.arrows,
                        boardWidth = boardWidth,
                        boardHeight = boardHeight,
                        shufflePhase = shufflePhase,
                        escapingArrows = escapingArrows,
                        bumpingArrows = bumpingArrows,
                        hintArrowId = gameState.hintArrowId,
                        resetKey = puzzle.stamp,
                        isArrowFree = viewModel::isArrowFree,
                        exitPointOf = viewModel::exitPointOf,
                        onArrowTap = viewModel::onArrowTap,
                        // The daily's whole identity: a pale sky board under the
                        // sunrise, so it never gets mistaken for a journey level.
                        skin = BoardSkin.DAILY
                    )
                }
            }

            ShuffleTimerBar(
                secondsLeft = shuffleSecondsLeft,
                modifier = Modifier.padding(horizontal = 40.dp)
            )

            DailyActionBar(
                canUndo = canUndo,
                hintsRemaining = hintsRemaining,
                onUndoClick = viewModel::undoMove,
                onHintClick = {
                    if (hintsRemaining > 0) viewModel.useHint()
                    else activity?.let(viewModel::onWatchAdForHints)
                },
                onRestartClick = viewModel::onRestartLevel,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 16.dp)
            )
        }

        // Floats over the board, above the blur-free layer so it stays sharp.
        ShuffleFlash(
            visible = flashVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = DailyHeaderHeight + 12.dp)
        )

        Scrim(visible = gameState.status == GameStatus.OUT_OF_LIVES) {
            OutOfLivesModal(
                clearedPercentage = gameState.clearedPercentage,
                isAdAvailable = viewModel.isRewardedAdAvailable(),
                onContinueClick = { activity?.let(viewModel::onContinueWithAd) },
                onRestartClick = viewModel::onRestartLevel
            )
        }

        // A daily that cannot be finished is our bug, not the player's: the
        // only sane exits are "try again" and "leave", never "skip ahead".
        Scrim(visible = gameState.isStuck) {
            PuzzleStuckModal(
                onSkipClick = onBackClick,
                onRetryClick = viewModel::onRestartLevel
            )
        }

        Scrim(visible = solved) {
            DailySolvedCard(
                title = puzzle.date.format(TITLE_FORMAT),
                streak = prefs.daily.streak,
                onDone = onBackClick
            )
        }

        ConfettiBurst(active = solved, modifier = Modifier.fillMaxSize())

        ReviewPromptEffect(viewModel.reviewPrompter)
    }
}

/** The one dialog shape this screen uses, so three call sites stay one idea. */
@Composable
private fun Scrim(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(250)),
        exit = fadeOut(tween(150))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/**
 * What you get for walking back into a day you have already finished.
 *
 * Reuses the solved card verbatim - the message is identical, and inventing
 * a second way to say "come back tomorrow" would only be a second thing to
 * keep in step.
 */
@Composable
private fun DailyClosedScreen(
    dateLabel: String,
    streak: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        DailySolvedCard(title = dateLabel, streak = streak, onDone = onDone)
    }
}

/**
 * No stars, no score, no "next level" - there is no next level today.
 * Just the day, the run it belongs to, and the door out.
 */
@Composable
private fun DailySolvedCard(title: String, streak: Int, onDone: () -> Unit) {
    val gameColors = ArrowEscapeTheme.gameColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = gameColors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.daily_solved_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = gameColors.textSecondary
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = if (streak > 1) stringResource(R.string.daily_streak_days, streak) else stringResource(R.string.daily_come_back_tomorrow),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onDone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.daily_done_button), fontWeight = FontWeight.Bold)
            }
        }
    }
}
