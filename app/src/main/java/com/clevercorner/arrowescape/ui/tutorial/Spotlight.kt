package com.clevercorner.arrowescape.ui.tutorial

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.clevercorner.arrowescape.R

/** The real controls a coach mark can point at. */
enum class SpotlightTarget { MODES, PROGRESS, PLAY, JOURNEY, SETTINGS, BOARD, LIVES, HINT }

/**
 * One stop on the tour: which control to light up, what to say about it, and
 * whether the player is meant to *use* it before moving on.
 */
data class TutorialStep(
    val target: SpotlightTarget,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
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
            titleRes = R.string.tour_home_modes_title,
            bodyRes = R.string.tour_home_modes_body
        ),
        TutorialStep(
            target = SpotlightTarget.PROGRESS,
            titleRes = R.string.tour_home_progress_title,
            bodyRes = R.string.tour_home_progress_body
        ),
        TutorialStep(
            target = SpotlightTarget.PLAY,
            titleRes = R.string.tour_home_play_title,
            bodyRes = R.string.tour_home_play_body
        ),
        TutorialStep(
            target = SpotlightTarget.JOURNEY,
            titleRes = R.string.tour_home_journey_title,
            bodyRes = R.string.tour_home_journey_body
        ),
        TutorialStep(
            target = SpotlightTarget.SETTINGS,
            titleRes = R.string.tour_home_settings_title,
            bodyRes = R.string.tour_home_settings_body
        )
    )
}

/** The in-game tour: two guided moves, then the things around the board. */
object GameTour {
    val steps = listOf(
        TutorialStep(
            target = SpotlightTarget.BOARD,
            titleRes = R.string.tour_game_board1_title,
            bodyRes = R.string.tour_game_board1_body,
            interactive = true
        ),
        TutorialStep(
            target = SpotlightTarget.BOARD,
            titleRes = R.string.tour_game_board2_title,
            bodyRes = R.string.tour_game_board2_body,
            interactive = true
        ),
        TutorialStep(
            target = SpotlightTarget.LIVES,
            titleRes = R.string.tour_game_lives_title,
            bodyRes = R.string.tour_game_lives_body
        ),
        TutorialStep(
            target = SpotlightTarget.HINT,
            titleRes = R.string.tour_game_hint_title,
            bodyRes = R.string.tour_game_hint_body
        )
    )
}
