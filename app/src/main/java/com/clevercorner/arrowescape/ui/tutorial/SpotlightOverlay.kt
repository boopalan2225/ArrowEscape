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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.ui.components.FitText
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import kotlinx.coroutines.delay

private val SCRIM = Color(0xDD0B0B0F)
private const val PADDING_PX = 10f
private const val CORNER_PX = 22f

/**
 * Breathing room between the highlighted control and the card explaining it.
 */
private val CARD_GAP = 20.dp

/** The card never touches the edge of the safe area. */
private val EDGE_MARGIN = 8.dp

/**
 * The shortest a card is allowed to be squeezed to. Below this, a scrolled
 * sliver of text stops being a tutorial step and starts being a sign that
 * something has gone wrong with the device's window, so the floor is a
 * deliberate admission that we have run out of room rather than a value
 * tuned to look nice.
 */
private val MIN_CARD_HEIGHT = 120.dp

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
 * @param onFinish the tour was seen through to the end.
 * @param onSkip the player asked to stop. A different outcome from
 *   [onFinish] and emphatically not a synonym: "Skip" means *the tutorial*,
 *   not *this page of it*, and a host that treats them alike will march a
 *   player who just opted out into the next tour anyway.
 */
@Composable
fun SpotlightOverlay(
    state: SpotlightState,
    steps: List<TutorialStep>,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
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
        } else {
            // An interactive step asks for a move, so it shows one being
            // made. The hand is drawn over the live control and never
            // intercepts anything - the tap it mimes still has to be the
            // player's own.
            TapHintFinger(
                modifier = Modifier
                    .offset(x = left, y = top)
                    .size(width = right - left, height = bottom - top),
                ink = MaterialTheme.colorScheme.onSurface,
                fill = MaterialTheme.colorScheme.surface,
                ripple = ArrowEscapeTheme.gameColors.arrowHint
            )
        }

        // The card goes in whichever gap is bigger, touching the edge of
        // the hole it describes. How much of the card is allowed to show
        // is a measurement of what's left after that touch, not a guess -
        // the previous version reserved a fixed number of dp per kind of
        // card, and a fixed number is a bet about font scale, screen size
        // and how many lines the body text wraps to. That bet loses on
        // somebody's device, always: on a tall phone the demonstration
        // steps were pushed so far down that the card's own buttons fell
        // off the bottom of the screen.
        val bars = WindowInsets.systemBars.asPaddingValues()
        val safeHeight = maxHeight - bars.calculateTopPadding() - bars.calculateBottomPadding()
        val spaceAbove = top
        val spaceBelow = maxHeight - bottom
        val below = spaceBelow >= spaceAbove

        // The gap is fixed at "touching the hole", full stop - it is never
        // pulled back to make room. Letting a tall card shrink the gap
        // instead of its own height was the actual bug: a two-pane
        // landscape window leaves little room under a short board slot,
        // and the old code solved that by dragging the card's edge back
        // over the puzzle it was supposed to be describing. A card is
        // already built to scroll past its own heightIn ceiling - that is
        // the thing that should give, not the card's position.
        val gap = (CARD_GAP + if (below) {
            bottom - bars.calculateTopPadding()
        } else {
            maxHeight - top - bars.calculateBottomPadding()
        }).coerceAtLeast(0.dp)

        // Whatever is left after the card touches the hole, with a floor
        // so a pathologically short window still shows a legible sliver
        // rather than nothing at all.
        val roomForCard = (safeHeight - gap - EDGE_MARGIN).coerceAtLeast(MIN_CARD_HEIGHT)

        TourCard(
            step = step,
            index = index,
            total = steps.size,
            isLast = isLast,
            onBack = { index-- },
            onNext = advance,
            onSkip = onSkip,
            modifier = Modifier
                .align(if (below) Alignment.TopCenter else Alignment.BottomCenter)
                // Status and navigation bars are part of the overlay's own
                // area; without this the card can tuck under the clock.
                .systemBarsPadding()
                .padding(horizontal = 20.dp)
                // The ceiling is the room actually left beside the hole,
                // not the whole screen - a card given the whole screen as
                // its budget has no reason to shrink before it starts
                // overlapping the thing it is pointing at.
                .heightIn(max = roomForCard)
                // Offset, not padding. Padding would shrink the constraints
                // the card is measured under, so a card pushed down would
                // measure shorter, which would permit pushing it further
                // down - a feedback loop that settles on a squashed card.
                // An offset moves the card without lying to it about how
                // much room it has.
                .offset { IntOffset(0, (if (below) gap else -gap).roundToPx()) }
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
        // The button row is a SIBLING of the scroll area, not a child of
        // it - a squeezed card is the whole reason this card scrolls at
        // all, and a player who can't see Next is stuck exactly when the
        // tour is supposed to be getting them unstuck. Only the words and
        // the demo - the part that can genuinely run long - give up space;
        // the way out stays put.
        Column {
            // `weight(1f, fill = false)` rather than a plain `weight(1f)`:
            // on a roomy window this should wrap its own content and sit
            // snug above the buttons, not stretch to claim empty space it
            // doesn't need. It only becomes the thing that shrinks - and
            // scrolls - once the card as a whole runs out of room.
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.tour_step_counter, index + 1, total),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = ArrowEscapeTheme.gameColors.textSecondary
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(step.titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(step.bodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArrowEscapeTheme.gameColors.textSecondary
                )

                // The rule, performed. Sits under the words rather than
                // replacing them: the animation answers "what does that look
                // like", and only the sentence answers "what is it called".
                step.demo?.let { demo ->
                    Spacer(Modifier.height(14.dp))
                    TutorialDemoStage(demo)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, top = 6.dp, end = 18.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Says "tutorial", not "step", because that is what it does:
                // one tap and neither this tour nor the one waiting on the
                // next screen will bother the player again.
                //
                // It carries the weight, and that is the whole fix for a
                // squashed button row. A Row measures its inflexible
                // children first and hands a weighted one the leftovers, so
                // whichever child holds the weight is the child that gives
                // way when space runs short. With a plain spacer in the
                // middle, the one that gave way was the last in the row -
                // the primary button - and at a 1.45x font scale "Next"
                // was squeezed into a column of single letters. Putting the
                // weight on the opt-out states the priority properly: the
                // action the player most likely wants keeps its full size,
                // and the one they probably do not shrinks to fit.
                // The weight sits on the Box rather than on the button so
                // the label still hugs the left edge: a button given the
                // weight directly would centre its text in all that spare
                // room. The flexible slot is what matters for measurement,
                // not which node is flexible.
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = onSkip) {
                        FitText(text = stringResource(R.string.tour_skip_all))
                    }
                }

                if (index > 0) {
                    TextButton(onClick = onBack) {
                        FitText(text = stringResource(R.string.back))
                    }
                }

                // An interactive step is finished by playing, not by tapping
                // Next - but the way out is always there for anyone stuck.
                Button(onClick = onNext, shape = MaterialTheme.shapes.large) {
                    FitText(
                        text = stringResource(
                            when {
                                isLast -> R.string.tour_got_it
                                step.interactive -> R.string.tour_skip_move
                                else -> R.string.tour_next
                            }
                        ),
                        style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
