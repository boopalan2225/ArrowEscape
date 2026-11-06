package com.clevercorner.arrowescape.ui.tutorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.game.engine.CollisionChecker
import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.ui.components.BoardFurniture
import com.clevercorner.arrowescape.ui.components.BoardFurniture.drawDeflector
import com.clevercorner.arrowescape.ui.components.BoardFurniture.drawLockBadge
import com.clevercorner.arrowescape.ui.components.BoardFurniture.drawSequenceBadge
import com.clevercorner.arrowescape.ui.components.drawDotGrid
import com.clevercorner.arrowescape.ui.components.drawRoutePreview
import com.clevercorner.arrowescape.ui.components.drawSnakeArrow
import com.clevercorner.arrowescape.ui.components.slitherPoints
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import com.clevercorner.arrowescape.ui.theme.GameColors
import kotlin.math.sin

/**
 * A four-by-four board that plays one rule on a loop, with a hand doing the
 * touching.
 *
 * Every pixel here comes from the renderers the real board uses -
 * [drawSnakeArrow], [drawDeflector], [drawSequenceBadge], [drawRoutePreview].
 * That is not thrift, it is the whole point: a player who learns the symbol
 * here recognises exactly the same symbol in the game, and nobody has to
 * keep two sets of artwork in step.
 *
 * The animation lives entirely in the draw phase. The clock is read inside
 * the draw lambda, so sixty frames a second cost sixty redraws and zero
 * recompositions of the card around it.
 */
@Composable
fun TutorialDemoStage(demo: TutorialDemo, modifier: Modifier = Modifier) {
    val scene = remember(demo) { demo.scene() }
    val steps = remember(scene) { scene.storyboard() }
    if (steps.isEmpty()) return

    val cycle = remember(steps) { steps.sumOf { lengthOf(it.gesture).toInt() } + TAIL }
    val clock = rememberLoopClock(cycle, resetKey = demo)
    val colors = ArrowEscapeTheme.gameColors
    val caption = stringResource(demo.captionRes)
    val windowHeight = LocalConfiguration.current.screenHeightDp.dp

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Sized from the room it was actually given, in both directions.
        // Width keeps the board square-ish inside a narrow card; the share
        // of window height is what stops a demonstration eating a short
        // landscape screen, where the buttons underneath matter more than
        // a big picture does.
        val stageHeight = minOf(maxWidth * 0.52f, windowHeight * 0.26f)
            .coerceIn(MIN_STAGE_HEIGHT, MAX_STAGE_HEIGHT)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(stageHeight)
                .clip(RoundedCornerShape(18.dp))
                // The hand is a picture, and a picture of a hand tells a
                // screen reader nothing. The legend's own sentence does.
                .semantics { contentDescription = caption }
        ) {
        val now = clock.longValue.toFloat()
        val at = locate(now, steps)
        val cell = minOf(size.width / scene.board.cols, size.height / scene.board.rows) * 0.86f
        val origin = Offset(
            (size.width - cell * scene.board.cols) / 2f,
            (size.height - cell * scene.board.rows) / 2f
        )
        val centreOf: (Cell) -> Offset = { c ->
            origin + Offset(c.col * cell + cell / 2f, c.row * cell + cell / 2f)
        }

        drawRoundRect(
            color = colors.boardBackground,
            cornerRadius = CornerRadius(cell * 0.3f, cell * 0.3f)
        )
        drawDotGrid(
            rows = scene.board.rows,
            cols = scene.board.cols,
            cell = cell,
            offX = origin.x,
            offY = origin.y,
            dotColor = colors.textSecondary.copy(alpha = 0.25f)
        )
        scene.board.deflectors.forEach { drawDeflector(it, centreOf(it.cell), cell, colors) }

        at.step.arrows.forEach { arrow ->
            drawDemoArrow(arrow, at, scene, cell, centreOf, colors)
        }

            drawTouchFeedback(at, cell, centreOf, colors)
        }
    }
}

/** Where the loop has got to: which step, how far into it, and where the
 * hand set off from. */
private class Playhead(
    val step: DemoStep,
    val local: Float,
    val from: Cell?,
    val last: Boolean
)

private fun lengthOf(gesture: DemoGesture): Float =
    if (gesture == DemoGesture.LONG_PRESS) LONG_LENGTH else TAP_LENGTH

/**
 * Finds the live step for [now], and where the hand is travelling from.
 *
 * The hand walks from the last thing it touched rather than teleporting -
 * the journey is what makes two taps read as one person demonstrating
 * something, instead of two unrelated events.
 */
private fun locate(now: Float, steps: List<DemoStep>): Playhead {
    var start = 0f
    steps.forEachIndexed { i, step ->
        val length = lengthOf(step.gesture)
        // The last step also owns the tail: letting its local time run past
        // the step's length is harmless, because every phase clamps.
        if (now < start + length || i == steps.lastIndex) {
            return Playhead(
                step = step,
                local = (now - start).coerceAtLeast(0f),
                from = steps.getOrNull(i - 1)?.target?.head,
                last = i == steps.lastIndex
            )
        }
        start += length
    }
    return Playhead(steps.first(), now, null, steps.size == 1)
}

private fun DrawScope.drawDemoArrow(
    arrow: Arrow,
    at: Playhead,
    scene: DemoScene,
    cell: Float,
    centreOf: (Cell) -> Offset,
    colors: GameColors
) {
    val step = at.step
    val route = step.routes?.get(arrow.id)

    // A refused tap shakes the arrow the player touched - and only that one,
    // because the shake is the game saying "not this". The progress through
    // that shake is also what reddens it: tying the two together is what
    // stops an arrow sitting there red before the hand has even landed.
    val shakeAt = if (step.refused && arrow.id == step.target.id) {
        phase(at.local, ACTION_AT, SHAKE_END)
    } else 0f
    val shaking = shakeAt > 0f && shakeAt < 1f
    val shake = sin(shakeAt * 32f) * cell * 0.11f * (1f - shakeAt)

    val lock = arrow.colorGroup?.let { BoardFurniture.lockColors(it) }
    val hinted = arrow.id == scene.hintId
    val (c1, c2) = when {
        shaking -> colors.arrowDown to colors.arrowDown
        hinted -> colors.arrowHint to colors.arrowHint
        lock != null -> lock
        else -> colors.arrowPrimary to colors.arrowSecondary
    }

    val base = arrow.cells.map(centreOf)
    // At zero progress the slither lands exactly on the resting points, so
    // an arrow on its way out needs no special case before the tap.
    val points = route?.let {
        slitherPoints(
            base = base,
            routePoints = it.map(centreOf),
            progress = ease(phase(at.local, ACTION_AT, ESCAPE_END)),
            extend = cell * scene.board.cols
        )
    } ?: base.map { it.copy(x = it.x + shake) }

    if (hinted) {
        // Breathing, like the real board's hint - a static halo looks like
        // a selection, and selection is a different idea entirely.
        val glow = 0.18f + 0.14f * (1f + sin(at.local / 260f)) / 2f
        points.forEach { drawCircle(colors.arrowHint.copy(alpha = glow), cell * 0.52f, it) }
    }

    drawSnakeArrow(points, arrow.direction, cell, c1, c2)
    if (lock != null) drawLockBadge(points.first(), cell, lock.first)
    arrow.sequenceKey?.let { key ->
        drawSequenceBadge(
            center = points.last(),
            cell = cell,
            key = key,
            // Asked of the engine, exactly as the board asks it, so the
            // badge dims for precisely the reason the tap gets refused.
            ready = !CollisionChecker.isSequenceBlocked(arrow, step.arrows),
            color = colors.arrowHint
        )
    }
}

/** The hand, its ripple, and whatever a long press is revealing. */
private fun DrawScope.drawTouchFeedback(
    at: Playhead,
    cell: Float,
    centreOf: (Cell) -> Offset,
    colors: GameColors
) {
    val step = at.step
    val target = centreOf(step.target.head)
    val entry = Offset(size.width * 0.92f, size.height * 1.3f)
    val origin = at.from?.let(centreOf) ?: entry
    val travel = ease(phase(at.local, 0f, TRAVEL_END))
    val tip = origin + (target - origin) * travel

    val held = step.gesture == DemoGesture.LONG_PRESS
    val release = if (held) HOLD_END else ACTION_AT
    val press = phase(at.local, TRAVEL_END, ACTION_AT) - phase(at.local, release, release + 140f)

    if (held) {
        val holding = phase(at.local, ACTION_AT, ACTION_AT + 420f) - phase(at.local, HOLD_END, HOLD_END + 140f)
        with(FingerPointer) { drawHoldRing(target, holding, colors.arrowHint, cell) }

        if (holding > 0.9f) {
            step.traces.forEach { (id, trace) ->
                val owner = step.arrows.firstOrNull { it.id == id } ?: return@forEach
                drawRoutePreview(
                    from = centreOf(owner.head),
                    routePoints = trace.path.map(centreOf),
                    blockedAt = trace.stoppedAt?.let(centreOf),
                    cell = cell,
                    dashPhase = at.local / 420f,
                    clearColor = colors.arrowHint,
                    blockedColor = colors.arrowDown
                )
            }
        }
    }

    val hand = cell * FingerPointer.CELL_SCALE
    with(FingerPointer) {
        if (!held) {
            drawTapRipple(
                at = target,
                progress = phase(at.local, ACTION_AT, ACTION_AT + 620f),
                color = if (step.refused) colors.arrowDown else colors.arrowHint,
                size = hand
            )
        }
        drawFinger(
            at = tip,
            press = press,
            size = hand,
            ink = Color(0xFF26262B),
            fill = Color(0xFFF7F3EC),
            // Only the final step fades: between steps the hand walks on to
            // the next arrow, and a fade there would read as a blink rather
            // than as one continuous demonstration.
            alpha = if (!at.last) 1f else {
                1f - phase(at.local, lengthOf(at.step.gesture) - 260f, lengthOf(at.step.gesture))
            }
        )
    }
}

/**
 * A board big enough to read on a tablet and small enough to leave room for
 * the buttons on a short phone in landscape.
 */
private val MIN_STAGE_HEIGHT = 104.dp
private val MAX_STAGE_HEIGHT = 220.dp

// Beat timings, in milliseconds from the start of a step. The hand arrives,
// presses, and only then does the board react - the order matters, because
// a board that moves before the finger lands teaches nothing about cause.
private const val TRAVEL_END = 560f
private const val ACTION_AT = 720f
private const val ESCAPE_END = 1520f
private const val SHAKE_END = 1160f
private const val HOLD_END = 2280f
private const val TAP_LENGTH = 2300f
private const val LONG_LENGTH = 2900f

/** A beat of stillness before the loop starts over. */
private const val TAIL = 700
