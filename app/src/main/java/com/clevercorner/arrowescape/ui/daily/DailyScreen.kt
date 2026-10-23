package com.clevercorner.arrowescape.ui.daily

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.game.daily.DailyPuzzle
import com.clevercorner.arrowescape.game.model.GameStatus
import com.clevercorner.arrowescape.review.ReviewPromptEffect
import com.clevercorner.arrowescape.ui.components.ConfettiBurst
import com.clevercorner.arrowescape.ui.components.CurvedGameBoard
import com.clevercorner.arrowescape.ui.components.GameStatsRow
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
 * absent. The board is the entire interface; the chrome is a date, three
 * hearts and a hint.
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
    val activity = LocalContext.current as? Activity

    LaunchedEffect(puzzle.stamp) { viewModel.startDaily(puzzle) }

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
                .systemBarsPadding()
                .then(if (blurred) Modifier.blur(8.dp) else Modifier)
        ) {
            DailyTopBar(
                title = puzzle.date.format(TITLE_FORMAT),
                streak = prefs.daily.streak,
                onBackClick = onBackClick
            )

            GameStatsRow(
                remainingArrows = gameState.remainingCount,
                lives = gameState.lives,
                difficultyLabel = gameState.difficulty
            )

            // The board takes every pixel the chrome does not need.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CurvedGameBoard(
                    arrows = gameState.arrows,
                    boardWidth = boardWidth,
                    boardHeight = boardHeight,
                    escapingArrows = escapingArrows,
                    bumpingArrows = bumpingArrows,
                    hintArrowId = gameState.hintArrowId,
                    resetKey = puzzle.stamp,
                    isArrowFree = viewModel::isArrowFree,
                    exitPointOf = viewModel::exitPointOf,
                    onArrowTap = viewModel::onArrowTap
                )
            }

            HintButton(
                hintsRemaining = hintsRemaining,
                onClick = {
                    if (hintsRemaining > 0) viewModel.useHint()
                    else activity?.let(viewModel::onWatchAdForHints)
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 20.dp)
            )
        }

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

/** Back arrow, the date, and the streak - nothing else earns a place here. */
@Composable
private fun DailyTopBar(title: String, streak: Int, onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        // Balances the back arrow so the date sits dead centre, and earns its
        // place by showing the one number worth protecting.
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            if (streak > 0) {
                Text(
                    text = "$streak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun HintButton(hintsRemaining: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(60.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = if (hintsRemaining > 0) "\uD83D\uDCA1" else "\u25B6", fontSize = 24.sp)
        }
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
                text = "Solved",
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
                text = if (streak > 1) "$streak days in a row" else "Come back tomorrow",
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
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}
