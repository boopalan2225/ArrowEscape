package com.clevercorner.arrowescape.game.engine

import com.clevercorner.arrowescape.game.model.Cell
import kotlin.math.abs
import kotlin.math.hypot

/**
 * What a board asks of the player, beyond how crowded it is.
 *
 * The existing numbers - `tightness`, `forcedRatio`, `longestChain` - all
 * measure *constraint*, and they agree with each other because they are three
 * views of the same thing. Worse, the blend built from them peaks on a board
 * where every single move is forced, which is a board with no thinking in it
 * at all. It is a dial pointing at "tedious", confidently labelled "hard".
 *
 * These measure perception instead, and perception is where this game's
 * difficulty actually lives. That is not a stylistic preference, it follows
 * from the rules: clearing an arrow only ever frees cells, so the legal-move
 * set grows monotonically and **no move can ever be wrong**. A player cannot
 * out-think such a board, only out-read it. So the honest questions are how
 * hard it is to find the next move and how often the board lies about which
 * moves are available.
 *
 * [decisionCount] is the exception, and is deliberately *not* weighted
 * heavily yet: a free choice is only interesting once choices can be wrong,
 * which needs the mechanics in Stage 11. It is measured now so that the
 * number already exists when it starts to mean something.
 */
data class PuzzleMetrics(
    /** Steps where the player had more than one legal move. */
    val decisionCount: Int,
    /** Those steps as a fraction of all steps. */
    val decisionRatio: Float,
    /**
     * Mean distance the eye travels between consecutive solution moves, as a
     * fraction of the board diagonal.
     *
     * A solution that unzips one corner reads itself; one that hops corner to
     * corner has to be hunted for, move after move. Cheap to compute and one
     * of the things players most reliably report as "hard".
     */
    val visualSearchCost: Float,
    /**
     * How often the board offers a move that is not really there.
     *
     * An arrow that is blocked by its immediate neighbour is honestly
     * blocked - the player sees it. An arrow with a long clear run that is
     * stopped somewhere far away *looks* free, and finding out it is not
     * costs a tap and a little trust. This is most of what makes a dense
     * board feel hard rather than merely full.
     */
    val falseAffordanceRate: Float
) {
    companion object {
        val NONE = PuzzleMetrics(0, 0f, 0f, 0f)

        /**
         * A blocked arrow counts as a false affordance once its run is at
         * least this many cells, i.e. once the obstruction is far enough away
         * not to be obvious at a glance.
         */
        const val FAR_RUN_CELLS = 3
    }
}

/** Accumulates [PuzzleMetrics] across a solve. Mutable on purpose; one per solve. */
internal class MetricsRecorder(private val rows: Int, private val cols: Int) {

    private var decisions = 0
    private var steps = 0
    private var searchDistanceSum = 0f
    private var hops = 0
    private var falseAffordanceSum = 0f
    private var previous: Cell? = null

    private val diagonal = hypot(rows.toFloat(), cols.toFloat()).coerceAtLeast(1f)

    fun step(legalMoveCount: Int, chosenHead: Cell, falseAffordances: Int, remaining: Int) {
        steps++
        if (legalMoveCount > 1) decisions++

        previous?.let { from ->
            searchDistanceSum += chebyshevish(from, chosenHead) / diagonal
            hops++
        }
        previous = chosenHead

        if (remaining > 0) falseAffordanceSum += falseAffordances.toFloat() / remaining
    }

    fun build(): PuzzleMetrics {
        val stepDivisor = steps.coerceAtLeast(1)
        return PuzzleMetrics(
            decisionCount = decisions,
            decisionRatio = decisions.toFloat() / stepDivisor,
            visualSearchCost = if (hops == 0) 0f else (searchDistanceSum / hops).coerceIn(0f, 1f),
            falseAffordanceRate = (falseAffordanceSum / stepDivisor).coerceIn(0f, 1f)
        )
    }

    /**
     * Straight-line distance between two cells.
     *
     * Euclidean rather than grid-step distance because this models the
     * player's *eye* crossing the board, and an eye does not walk the lattice.
     */
    private fun chebyshevish(a: Cell, b: Cell): Float =
        hypot(abs(a.row - b.row).toFloat(), abs(a.col - b.col).toFloat())
}
