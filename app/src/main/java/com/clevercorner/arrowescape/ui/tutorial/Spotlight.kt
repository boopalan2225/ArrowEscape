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
import com.clevercorner.arrowescape.data.GameMode

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
    val interactive: Boolean = false,
    /**
     * A worked example played out on a miniature board inside the card.
     *
     * Reserved for the rules that a sentence cannot carry - "matching rings
     * leave together" is a fact a player nods at and forgets, where a hand
     * tapping one ring and watching both go is a rule they have seen work.
     */
    val demo: TutorialDemo? = null
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

/**
 * The in-game tour: two guided moves, the mechanics that the board legend
 * lists, then the things around the board.
 *
 * Mode-aware, because the legend is. Turn pads and locked pairs exist only
 * on the classic grid, and a lesson about furniture a player will never
 * meet is worse than no lesson - it teaches them that the tutorial is not
 * about the game in front of them.
 */
object GameTour {

    /** Learn the verb first. Nothing else makes sense before the tap does. */
    private val opening = listOf(
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
        )
    )

    /**
     * The two lessons that are really about the HUD, each carrying the
     * demonstration that belongs with it: hearts are what a blocked tap
     * costs, and a hint is a glow on the board rather than a button that
     * solves things.
     */
    private val closing = listOf(
        TutorialStep(
            target = SpotlightTarget.LIVES,
            titleRes = R.string.tour_game_lives_title,
            bodyRes = R.string.tour_game_lives_body,
            demo = TutorialDemo.BLOCKED
        ),
        TutorialStep(
            target = SpotlightTarget.HINT,
            titleRes = R.string.tour_game_hint_title,
            bodyRes = R.string.tour_game_hint_body,
            demo = TutorialDemo.HINT_GLOW
        )
    )

    fun steps(mode: GameMode): List<TutorialStep> = opening + mechanics(mode) + closing

    /**
     * One step per rule the board legend bothers to list, in the order a
     * player will trip over them. The spotlight stays on the board for all
     * of these: the card is doing the teaching, and the hole keeps the
     * player's eye on the thing being taught about.
     */
    private fun mechanics(mode: GameMode): List<TutorialStep> = buildList {
        if (mode == GameMode.CLASSIC) {
            add(lesson(R.string.tour_game_turnpad_title, R.string.tour_game_turnpad_body, TutorialDemo.TURN_PAD))
            add(lesson(R.string.tour_game_lock_title, R.string.tour_game_lock_body, TutorialDemo.LOCK_PAIR))
        }
        add(lesson(R.string.tour_game_order_title, R.string.tour_game_order_body, TutorialDemo.SEQUENCE))
        add(lesson(R.string.tour_game_route_title, R.string.tour_game_route_body, TutorialDemo.ROUTE_PREVIEW))
    }

    private fun lesson(@StringRes title: Int, @StringRes body: Int, demo: TutorialDemo) =
        TutorialStep(
            target = SpotlightTarget.BOARD,
            titleRes = title,
            bodyRes = body,
            demo = demo
        )
}
