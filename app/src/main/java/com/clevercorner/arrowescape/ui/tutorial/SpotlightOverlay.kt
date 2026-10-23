package com.clevercorner.arrowescape.ui.tutorial

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import kotlinx.coroutines.delay

private val SCRIM = Color(0xDD0B0B0F)
private const val PADDING_PX = 10f
private const val CORNER_PX = 22f

/**
 * Dims the screen and cuts a hole around one real control at a time, with a
 * card explaining it and Back / Next to move the tour along.
 *
 * The hole is a genuine hole: the scrim is built from four blocker panels
 * *around* the highlight rather than one sheet on top of it, because Compose
 * delivers a tap to the topmost node that is hit and never passes it down to
 * the sibling underneath. On an interactive step there is simply nothing
 * covering the control, so the game receives the tap and plays the move.
 *
 * @param progress a number the host bumps whenever the player makes progress
 *   (arrows remaining, for instance). An interactive step waits for it to
 *   change rather than guessing with a timer.
 */
@Composable
fun SpotlightOverlay(
    state: SpotlightState,
    steps: List<TutorialStep>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Int = 0
) {
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    val isLast = index == steps.lastIndex

    // Until the control reports its position there is nothing to point at.
    val target = state.boundsOf(step.target) ?: return

    val hole by animateRectAsState(
        targetValue = target.inflate(PADDING_PX),
        animationSpec = tween(420, easing = EaseInOutCubic),
        label = "spotlightHole"
    )
    val fadeIn by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(320),
        label = "spotlightFade"
    )
    val pulse by rememberInfiniteTransition(label = "spotlightRing").animateFloat(
        initialValue = 0f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = EaseInOutCubic),
            RepeatMode.Reverse
        ),
        label = "ring"
    )

    val advance: () -> Unit = { if (isLast) onFinish() else index++ }

    // An interactive step ends when the move actually lands, not on a guess.
    val progressAtStart = remember(index) { progress }
    LaunchedEffect(index, progress) {
        if (step.interactive && progress != progressAtStart) {
            delay(650)
            advance()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().alpha(fadeIn)) {
        val density = LocalDensity.current
        val left = with(density) { hole.left.toDp() }
        val top = with(density) { hole.top.toDp() }
        val right = with(density) { hole.right.toDp() }
        val bottom = with(density) { hole.bottom.toDp() }

        // Painting only - a Canvas has no pointer input, so it blocks nothing.
        Canvas(Modifier.fillMaxSize()) {
            val cutout = Path().apply {
                addRect(Rect(Offset.Zero, Size(size.width, size.height)))
                addRoundRect(RoundRect(hole, CornerRadius(CORNER_PX, CORNER_PX)))
                fillType = PathFillType.EvenOdd
            }
            drawPath(cutout, SCRIM)

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(hole.left - pulse, hole.top - pulse),
                size = Size(hole.width + pulse * 2, hole.height + pulse * 2),
                cornerRadius = CornerRadius(CORNER_PX + pulse, CORNER_PX + pulse),
                style = Stroke(width = 3f)
            )
        }

        // Four panels fence the highlight in: taps outside move the tour on,
        // taps inside reach whatever is being pointed at.
        Blocker(0.dp, 0.dp, maxWidth, top, advance)
        Blocker(0.dp, bottom, maxWidth, maxHeight - bottom, advance)
        Blocker(0.dp, top, left, bottom - top, advance)
        Blocker(right, top, maxWidth - right, bottom - top, advance)

        // On a "look at this" step the control stays untouchable, so nobody
        // starts a level by reading about the Play button.
        if (!step.interactive) {
            Blocker(left, top, right - left, bottom - top, advance)
        }

        val below = bottom < maxHeight / 2
        val gap = if (below) bottom + 20.dp else maxHeight - top + 20.dp

        TourCard(
            step = step,
            index = index,
            total = steps.size,
            isLast = isLast,
            onBack = { index-- },
            onNext = advance,
            onSkip = onFinish,
            modifier = Modifier
                .align(if (below) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(top = if (below) gap else 0.dp, bottom = if (below) 0.dp else gap)
        )
    }
}

/** An invisible panel that eats taps and nudges the tour along. */
@Composable
private fun Blocker(x: Dp, y: Dp, width: Dp, height: Dp, onTap: () -> Unit) {
    if (width <= 0.dp || height <= 0.dp) return

    Box(
        Modifier
            .offset(x = x, y = y)
            .size(width = width, height = height)
            .pointerInput(onTap) { detectTapGestures { onTap() } }
    )
}

@Composable
private fun TourCard(
    step: TutorialStep,
    index: Int,
    total: Int,
    isLast: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
        modifier = modifier.widthIn(max = 420.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "STEP ${index + 1} OF $total",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
                color = ArrowEscapeTheme.gameColors.textSecondary
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = step.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium,
                color = ArrowEscapeTheme.gameColors.textSecondary
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onSkip) { Text("Skip") }

                Spacer(Modifier.weight(1f))

                if (index > 0) {
                    TextButton(onClick = onBack) { Text("Back") }
                }

                // An interactive step is finished by playing, not by tapping
                // Next - but the way out is always there for anyone stuck.
                Button(onClick = onNext, shape = MaterialTheme.shapes.large) {
                    Text(
                        text = when {
                            isLast -> "Got it"
                            step.interactive -> "Skip move"
                            else -> "Next"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
