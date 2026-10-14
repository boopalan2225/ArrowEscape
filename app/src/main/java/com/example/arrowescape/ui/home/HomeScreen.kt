package com.example.arrowescape.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GameMode
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.milestone.MilestoneData
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

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
    onSettingsClick: () -> Unit,
    onDevClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress = prefs.activeProgress
    val journey = MilestoneData.forMode(prefs.mode)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(homeBackdrop())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedLogo()

            Spacer(Modifier.height(16.dp))

            GameTitle()

            Spacer(Modifier.height(24.dp))

            ModeSwitcher(selected = prefs.mode, onSelect = onModeSelect)

            Spacer(Modifier.height(20.dp))

            ProgressCard(
                mode = prefs.mode,
                progress = progress,
                totalLevels = journey.totalLevels
            )

            Spacer(Modifier.height(28.dp))

            PulsatingPlayButton(
                label = if (progress.currentLevel > 1) "CONTINUE" else "PLAY",
                onClick = onPlayClick
            )

            Spacer(Modifier.height(20.dp))

            HomeActions(
                devMode = prefs.devMode,
                onJourneyClick = onJourneyClick,
                onSettingsClick = onSettingsClick,
                onDevClick = onDevClick
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A soft vertical wash so the screen is not a flat slab of one colour. */
@Composable
private fun homeBackdrop(): Brush = Brush.verticalGradient(
    listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        MaterialTheme.colorScheme.background
    )
)

@Composable
private fun GameTitle() {
    Text(
        text = "ARROW",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 4.sp
    )
    Text(
        text = "ESCAPE",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        letterSpacing = 4.sp
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "TAP  \u00B7  VANISH  \u00B7  ESCAPE",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 2.sp
    )
}

/**
 * Where the player stands in the selected mode. The level number slides when
 * the mode changes, which is the whole point: each mode keeps its own place.
 */
@Composable
private fun ProgressCard(
    mode: GameMode,
    progress: GamePreferences.ModeProgress,
    totalLevels: Int
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val completed = progress.clearedOutOf(totalLevels)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
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

/** Secondary navigation: the map, the dev door, and settings. */
@Composable
private fun HomeActions(
    devMode: Boolean,
    onJourneyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDevClick: (() -> Unit)?
) {
    val gameColors = ArrowEscapeTheme.gameColors

    OutlinedButton(
        onClick = onJourneyClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text = "\uD83D\uDDFA", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Journey Map",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }

    // Developer entry point - only while developer mode is on.
    if (devMode && onDevClick != null) {
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onDevClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "\uD83D\uDEE0  Dev: Open any level",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    TextButton(onClick = onSettingsClick) {
        Text(text = "\u2699", fontSize = 20.sp, color = gameColors.textSecondary)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.bodyLarge,
            color = gameColors.textSecondary
        )
    }
}

/**
 * The mark: four arrows orbiting a breathing core. Decorative only - it carries
 * no information, so it needs no content description.
 */
@Composable
private fun AnimatedLogo() {
    val gameColors = ArrowEscapeTheme.gameColors
    val infinite = rememberInfiniteTransition(label = "logo")

    val orbit by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "orbit"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(2200, easing = EaseInOutCubic),
            RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = Modifier.size(132.dp),
        contentAlignment = Alignment.Center
    ) {
        // Halo behind everything, pulsing in counter-phase with the core.
        Box(
            Modifier
                .size(118.dp)
                .scale(2f - breathe)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        )

        Box(Modifier.rotate(orbit)) {
            val orbiters = listOf(
                0f to gameColors.arrowUp,
                90f to gameColors.arrowRight,
                180f to gameColors.arrowDown,
                270f to gameColors.arrowLeft
            )
            orbiters.forEach { (angle, color) ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .rotate(angle)
                        .offset(y = (-48).dp)
                ) {
                    Text(
                        text = "\u2191",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(66.dp)
                .scale(breathe)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u2192",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(x = 1.dp, y = (-5).dp) // Optical centering
            )
        }
    }
}

@Composable
private fun PulsatingPlayButton(label: String, onClick: () -> Unit) {
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

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        // Halo tracks the button's own box, so it never peeks out as a stray slab.
        Box(
            Modifier
                .matchParentSize()
                .padding(vertical = 2.dp)
                .scale(scale * 1.02f)
                .clip(RoundedCornerShape(22.dp))
                .alpha(glow)
                .background(MaterialTheme.colorScheme.primary)
        )

        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .scale(scale),
            shape = RoundedCornerShape(18.dp),
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

@Preview(showBackground = true, name = "Home - Light")
@Composable
private fun HomeScreenPreviewLight() {
    ArrowEscapeTheme(darkTheme = false) {
        HomeScreen(
            prefs = previewPrefs(GameMode.CURVED, level = 1, best = 1, done = 0),
            onPlayClick = {},
            onModeSelect = {},
            onJourneyClick = {},
            onSettingsClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Home - Dark")
@Composable
private fun HomeScreenPreviewDark() {
    ArrowEscapeTheme(darkTheme = true) {
        HomeScreen(
            prefs = previewPrefs(GameMode.CLASSIC, level = 42, best = 50, done = 49),
            onPlayClick = {},
            onModeSelect = {},
            onJourneyClick = {},
            onSettingsClick = {}
        )
    }
}
