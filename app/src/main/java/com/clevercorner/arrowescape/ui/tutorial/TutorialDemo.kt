package com.clevercorner.arrowescape.ui.tutorial

import androidx.annotation.StringRes
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.game.engine.CollisionChecker
import com.clevercorner.arrowescape.game.engine.ExitSimulator
import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.Deflector
import com.clevercorner.arrowescape.game.model.Direction

/**
 * The worked examples the tutorial plays out: a tiny board, a scripted
 * finger, and whatever the rules say happens next.
 *
 * The important word is *rules*. A scene holds real [Arrow] and [Board]
 * objects and asks the real [CollisionChecker] what each tap does, so a
 * demonstration cannot drift away from the game the way a hand-drawn
 * animation would. Change an escape rule and these lessons change with it -
 * or fail loudly, which is the next best thing.
 */

/** What the hand does. A tap commits a move; a long press only looks. */
enum class DemoGesture { TAP, LONG_PRESS }

/** One scripted touch on the demo board. */
data class DemoBeat(val arrowId: Int, val gesture: DemoGesture = DemoGesture.TAP)

/**
 * A board small enough to fit on a card, plus the taps to perform on it.
 *
 * [hintId] marks an arrow that wears the hint glow throughout - the one
 * lesson with nothing to simulate, since a glow is a statement rather than
 * a move.
 */
data class DemoScene(
    val board: Board,
    val arrows: List<Arrow>,
    val script: List<DemoBeat>,
    val hintId: Int? = null
)

/**
 * The mechanics a player has to be shown rather than told.
 *
 * [captionRes] reuses the board legend's own wording: the dialog and the
 * tutorial are describing the same symbol, and two descriptions of one
 * thing is one description too many. It doubles as the animation's
 * screen-reader label, which is the only way this lesson reaches a player
 * who cannot see the hand at all.
 */
enum class TutorialDemo(@StringRes val captionRes: Int) {
    BLOCKED(R.string.legend_blocked),
    TURN_PAD(R.string.legend_turn_pad),
    LOCK_PAIR(R.string.legend_lock_pair),
    SEQUENCE(R.string.legend_sequence),
    ROUTE_PREVIEW(R.string.legend_route),
    HINT_GLOW(R.string.legend_hint);

    fun scene(): DemoScene = when (this) {
        BLOCKED -> blockedScene(script = listOf(DemoBeat(0), DemoBeat(1)))

        TURN_PAD -> DemoScene(
            // The pad sits in the lane, two cells along, pointing down: the
            // arrow has to visibly turn a corner for the lesson to land.
            board = DEMO_BOARD.copy(deflectors = listOf(Deflector(Cell(1, 2), Direction.DOWN))),
            arrows = listOf(arrowAt(0, 1, 0, Direction.RIGHT)),
            script = listOf(DemoBeat(0))
        )

        LOCK_PAIR -> DemoScene(
            board = DEMO_BOARD,
            arrows = listOf(
                arrowAt(0, 1, 0, Direction.LEFT, lock = 0),
                arrowAt(1, 2, 3, Direction.RIGHT, lock = 0),
                // An unlocked bystander, so "both of them left" reads as the
                // pairing and not as "everything leaves at once".
                arrowAt(2, 0, 2, Direction.UP)
            ),
            script = listOf(DemoBeat(0))
        )

        SEQUENCE -> DemoScene(
            board = DEMO_BOARD,
            arrows = listOf(
                arrowAt(0, 1, 1, Direction.RIGHT, order = 1),
                arrowAt(1, 2, 1, Direction.RIGHT, order = 2)
            ),
            // Refused, then allowed, then the same arrow again now that its
            // turn has come round. Showing the refusal first is the point:
            // a clear lane that does nothing is the moment players quit.
            script = listOf(DemoBeat(1), DemoBeat(0), DemoBeat(1))
        )

        ROUTE_PREVIEW -> blockedScene(
            script = listOf(
                DemoBeat(0, DemoGesture.LONG_PRESS),
                DemoBeat(1, DemoGesture.LONG_PRESS)
            )
        )

        HINT_GLOW -> blockedScene(script = listOf(DemoBeat(1)), hintId = 1)
    }
}

/** Four by four: big enough for a blocked lane and a turn, small on a card. */
private val DEMO_BOARD = Board(rows = 4, cols = 4)

/**
 * One arrow jammed behind another - the shape of nearly every real board,
 * and the setup three separate lessons need.
 */
private fun blockedScene(script: List<DemoBeat>, hintId: Int? = null) = DemoScene(
    board = DEMO_BOARD,
    arrows = listOf(
        arrowAt(0, 1, 0, Direction.RIGHT),
        arrowAt(1, 1, 2, Direction.UP)
    ),
    script = script,
    hintId = hintId
)

private fun arrowAt(
    id: Int,
    row: Int,
    col: Int,
    direction: Direction,
    lock: Int? = null,
    order: Int? = null
) = Arrow(
    id = id,
    cells = listOf(Cell(row, col)),
    direction = direction,
    colorGroup = lock,
    sequenceKey = order
)

/**
 * One frozen moment of a scene: the board as it stands, the arrow about to
 * be touched, and the verdict.
 *
 * [routes] is non-null only when the touch actually moves something, so the
 * renderer never has to re-derive a decision the rules already made.
 */
internal data class DemoStep(
    val arrows: List<Arrow>,
    val target: Arrow,
    val gesture: DemoGesture,
    val traces: Map<Int, ExitSimulator.Trace>,
    val routes: Map<Int, List<Cell>>?
) {
    val refused: Boolean get() = gesture == DemoGesture.TAP && routes == null
}

/**
 * Plays the script through the real rules once, up front, so that drawing a
 * frame is pure lookup.
 *
 * Doing this per frame would mean running the collision checker sixty times
 * a second to re-learn an answer that has not changed since the scene was
 * written.
 */
internal fun DemoScene.storyboard(): List<DemoStep> {
    var live = arrows
    return script.mapNotNull { beat ->
        val target = live.firstOrNull { it.id == beat.arrowId } ?: return@mapNotNull null
        val routes = CollisionChecker.escapeFor(target, live, board)
            ?.routes
            // A long press is a question, not a move. Dropping the routes
            // here is what keeps the preview lesson from secretly playing
            // the escape lesson as well.
            ?.takeIf { beat.gesture == DemoGesture.TAP }

        DemoStep(
            arrows = live,
            target = target,
            gesture = beat.gesture,
            traces = CollisionChecker.traceFor(target, live, board),
            routes = routes
        ).also { if (routes != null) live = live.filterNot { it.id in routes.keys } }
    }
}
