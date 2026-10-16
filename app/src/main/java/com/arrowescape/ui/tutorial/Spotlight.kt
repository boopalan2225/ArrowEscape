package com.arrowescape.ui.tutorial

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** The real controls a coach mark can point at. */
enum class SpotlightTarget { MODES, PROGRESS, PLAY, JOURNEY, SETTINGS, BOARD, LIVES, HINT }

/**
 * One stop on the tour: which control to light up, what to say about it, and
 * whether the player is meant to *use* it before moving on.
 */
data class TutorialStep(
    val target: SpotlightTarget,
    val title: String,
    val body: String,
    /** Interactive steps let taps through to the control underneath. */
    val interactive: Boolean = false
)

/** Where each control ended up on screen, measured by the controls themselves. */
@Stable
class SpotlightState {
    private val bounds = mutableStateMapOf<SpotlightTarget, Rect>()

    fun report(target: SpotlightTarget, rect: Rect) {
        bounds[target] = rect
    }

    fun boundsOf(target: SpotlightTarget): Rect? = bounds[target]
}

/**
 * Lets any control register itself without knowing a tour exists. When no
 * tour is running the reports simply go to a state nobody reads.
 */
val LocalSpotlight = staticCompositionLocalOf { SpotlightState() }

fun Modifier.spotlightTarget(target: SpotlightTarget): Modifier = composed {
    val state = LocalSpotlight.current
    onGloballyPositioned { coordinates ->
        state.report(target, coordinates.boundsInRoot())
    }
}

/** The home screen tour, in the order a new player meets the controls. */
object HomeTour {
    val steps = listOf(
        TutorialStep(
            target = SpotlightTarget.MODES,
            title = "Pick your game",
            body = "Classic lays arrows on a grid; Curved flows them around a drawn " +
                "figure. Each mode keeps its own progress."
        ),
        TutorialStep(
            target = SpotlightTarget.PROGRESS,
            title = "Your place in the story",
            body = "The level you are on, how far through the journey you are, and " +
                "your best run so far."
        ),
        TutorialStep(
            target = SpotlightTarget.PLAY,
            title = "Start here",
            body = "Tap an arrow and it escapes in the direction it points - but only " +
                "if its path is clear. Three lives per level."
        ),
        TutorialStep(
            target = SpotlightTarget.JOURNEY,
            title = "The Journey map",
            body = "Chapters of ten levels, your stars on each, and a peek at what is " +
                "still locked."
        ),
        TutorialStep(
            target = SpotlightTarget.SETTINGS,
            title = "Make it yours",
            body = "Five appearances, each with its own look and sound, plus sound and " +
                "haptics switches and this walkthrough any time you want it."
        )
    )
}

/** The in-game tour: two guided moves, then the things around the board. */
object GameTour {
    val steps = listOf(
        TutorialStep(
            target = SpotlightTarget.BOARD,
            title = "Your first move",
            body = "Find an arrow with a clear run to the edge it points at, and tap " +
                "it. Go on - the board is live.",
            interactive = true
        ),
        TutorialStep(
            target = SpotlightTarget.BOARD,
            title = "Now one more",
            body = "Every arrow that leaves opens a path for another. Tap a second " +
                "one and watch the board loosen up.",
            interactive = true
        ),
        TutorialStep(
            target = SpotlightTarget.LIVES,
            title = "Arrows left, and lives",
            body = "Tap a blocked arrow and it shakes instead of leaving - and costs " +
                "one of these three hearts."
        ),
        TutorialStep(
            target = SpotlightTarget.HINT,
            title = "Stuck? Take a hint",
            body = "Two hints a level point out an arrow that can leave right now. " +
                "Run out and you can watch an ad for more."
        )
    )
}
