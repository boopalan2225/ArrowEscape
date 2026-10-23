package com.clevercorner.arrowescape.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.ui.components.GlyphIcon
import com.clevercorner.arrowescape.ui.milestone.MilestoneData
import com.clevercorner.arrowescape.ui.theme.AppTheme
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import com.clevercorner.arrowescape.ui.tutorial.SpotlightTarget
import com.clevercorner.arrowescape.ui.tutorial.spotlightTarget

/**
 * The front door: pick a mode, see where you are in it, and play.
 *
 * Progress shown here is always the *selected* mode's own progress, so the
 * numbers change as you flip modes and nothing is ever lost.
 */
@Composable
fun HomeScreen(
    prefs: GamePreferences.Prefs,
    onPlayClick: () -> Unit,
    onModeSelect: (GameMode) -> Unit,
    onJourneyClick: () -> Unit,
    onDailyClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onDevClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress = prefs.activeProgress
    val journey = MilestoneData.forMode(prefs.mode)

    HomeBackdrop(modifier = modifier) {
        // heightIn(min = viewport) lets the column centre itself when the
        // content is short and still scroll when it is not.
        val viewportHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .entranceAnimation(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GameTitle()

            Spacer(Modifier.height(28.dp))

            ModeSwitcher(
                selected = prefs.mode,
                onSelect = onModeSelect,
                modifier = Modifier.spotlightTarget(SpotlightTarget.MODES)
            )

            Spacer(Modifier.height(20.dp))

            ProgressCard(
                mode = prefs.mode,
                progress = progress,
                totalLevels = journey.totalLevels,
                modifier = Modifier.spotlightTarget(SpotlightTarget.PROGRESS)
            )

            Spacer(Modifier.height(28.dp))

            PulsatingPlayButton(
                label = if (progress.currentLevel > 1) "CONTINUE" else "PLAY",
                onClick = onPlayClick,
                modifier = Modifier.spotlightTarget(SpotlightTarget.PLAY)
            )

            Spacer(Modifier.height(20.dp))

            HomeActions(
                onJourneyClick = onJourneyClick,
                onDailyClick = onDailyClick,
                dailySolved = prefs.daily.isCleared(java.time.LocalDate.now()),
                dailyStreak = prefs.daily.streak,
                onSettingsClick = onSettingsClick
            )
        }

        // Developer door: parked in a corner so it never shifts the layout,
        // and absent entirely unless developer mode is on.
        if (prefs.devMode && onDevClick != null) {
            DevCornerButton(
                onClick = onDevClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .systemBarsPadding()
                    .padding(12.dp)
            )
        }
    }
}

/** Small, unobtrusive and out of the way - a trapdoor, not a feature. */
@Composable
private fun DevCornerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        modifier = modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            GlyphIcon(
                glyph = "\u2692",
                fontSize = 18.sp,
                color = ArrowEscapeTheme.gameColors.textSecondary
            )
        }
    }
}

/** A soft rise-and-fade the first time Home appears. */
@Composable
private fun Modifier.entranceAnimation(): Modifier {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(650, easing = EaseInOutCubic)) }
    return this
        .alpha(shown.value)
        .offset(y = ((1f - shown.value) * 24).dp)
}

@Composable
private fun GameTitle() {
    Text(
        text = "ARROW",
        style = MaterialTheme.typography.displayLarge.copy(
            brush = Brush.horizontalGradient(
                listOf(MaterialTheme.colorScheme.primary, ArrowEscapeTheme.persona.glow)
            )
        ),
        fontWeight = FontWeight.Black,
        letterSpacing = 6.sp
    )
    Text(
        text = "ESCAPE",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        letterSpacing = 6.sp
    )
    Spacer(Modifier.height(6.dp))
    // Each look has its own motto, so the words change with the scenery.
    AnimatedContent(
        targetState = ArrowEscapeTheme.persona.tagline,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
        label = "tagline"
    ) { tagline ->
        Text(
            text = tagline,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 2.sp
        )
    }
}

/**
 * Where the player stands in the selected mode. The level number slides when
 * the mode changes, which is the whole point: each mode keeps its own place.
 */
@Composable
private fun ProgressCard(
    mode: GameMode,
    progress: GamePreferences.ModeProgress,
    totalLevels: Int,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val completed = progress.clearedOutOf(totalLevels)

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${mode.label.uppercase()}  \u00B7  LEVEL",
                style = MaterialTheme.typography.labelMedium,
                color = gameColors.textSecondary,
                letterSpacing = 2.sp
            )

            AnimatedContent(
                targetState = progress.currentLevel,
                transitionSpec = {
                    val goingUp = targetState > initialState
                    val offset = if (goingUp) 1 else -1
                    (slideInVertically { it * offset } + fadeIn()) togetherWith
                        (slideOutVertically { -it * offset } + fadeOut())
                },
                label = "levelNumber"
            ) { level ->
                Text(
                    text = "$level",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            JourneyProgressBar(completed = completed, total = totalLevels)

            Spacer(Modifier.height(8.dp))

            Text(
                text = "$completed of $totalLevels cleared  \u00B7  best ${progress.bestOutOf(totalLevels)}",
                style = MaterialTheme.typography.bodySmall,
                color = gameColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Secondary navigation: the map and settings. */
@Composable
private fun HomeActions(
    onJourneyClick: () -> Unit,
    onDailyClick: () -> Unit,
    dailySolved: Boolean,
    dailyStreak: Int,
    onSettingsClick: () -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors

    OutlinedButton(
        onClick = onJourneyClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .spotlightTarget(SpotlightTarget.JOURNEY),
        shape = MaterialTheme.shapes.large
    ) {
        Text(text = "\uD83D\uDDFA", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Journey Map",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }

    Spacer(Modifier.height(8.dp))

    // Today's puzzle, and whether it is still waiting. The label does the
    // nagging so no notification has to.
    OutlinedButton(
        onClick = onDailyClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Text(text = "\uD83D\uDCC5", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                dailySolved && dailyStreak > 1 -> "Daily done - $dailyStreak in a row"
                dailySolved -> "Daily done"
                else -> "Daily Puzzle"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }

    Spacer(Modifier.height(8.dp))

    TextButton(
        onClick = onSettingsClick,
        modifier = Modifier.spotlightTarget(SpotlightTarget.SETTINGS)
    ) {
        Text(text = "\u2699", fontSize = 20.sp, color = gameColors.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.bodyLarge,
            color = gameColors.textSecondary
        )
    }
}

@Composable
private fun PulsatingPlayButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "play")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "scale"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(1100, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "glow"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        // Halo tracks the button's own box, so it never peeks out as a stray slab.
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = 2.dp)
                .scale(scale * 1.02f)
                .clip(MaterialTheme.shapes.large)
                .alpha(glow)
                .background(MaterialTheme.colorScheme.primary)
        )

        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .scale(scale),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

// ======================= PREVIEWS =======================

private fun previewPrefs(mode: GameMode, level: Int, best: Int, done: Int) =
    GamePreferences.Prefs(
        mode = mode,
        progress = mapOf(
            mode to GamePreferences.ModeProgress(
                currentLevel = level,
                highestLevel = best,
                totalLevelsCompleted = done
            )
        )
    )

/** One preview body, four looks - the scenery is the point of the comparison. */
@Composable
private fun HomePreview(theme: AppTheme, mode: GameMode, level: Int) {
    ArrowEscapeTheme(theme) {
        HomeScreen(
            prefs = previewPrefs(mode, level = level, best = level, done = level - 1),
            onPlayClick = {},
            onModeSelect = {},
            onJourneyClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Home - Classic")
@Composable
private fun HomeScreenPreviewClassic() = HomePreview(AppTheme.CLASSIC, GameMode.CURVED, level = 1)

@Preview(showBackground = true, name = "Home - Midnight")
@Composable
private fun HomeScreenPreviewMidnight() = HomePreview(AppTheme.MIDNIGHT, GameMode.CLASSIC, level = 42)

@Preview(showBackground = true, name = "Home - Pirate")
@Composable
private fun HomeScreenPreviewPirate() = HomePreview(AppTheme.PIRATE, GameMode.CURVED, level = 17)

@Preview(showBackground = true, name = "Home - Sunset")
@Composable
private fun HomeScreenPreviewSunset() = HomePreview(AppTheme.SUNSET, GameMode.CLASSIC, level = 88)

@Preview(showBackground = true, name = "Home - Ink")
@Composable
private fun HomeScreenPreviewMono() = HomePreview(AppTheme.MONO, GameMode.CURVED, level = 7)
