package com.clevercorner.arrowescape.game.level

import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Cell
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Numbers a handful of arrows so the board demands a plan, not just a lane.
 *
 * ## Why this mechanic, after two that did not work
 *
 * Stage 11 set out to add difficulty that is about *thinking* rather than
 * about the board being hard to read. Two earlier candidates failed, and both
 * failures pointed here:
 *
 *  - **Move budgets are a no-op in this ruleset.** Every move clears exactly
 *    one party and the party count is fixed, so the number of taps is
 *    identical for every possible clear order. Par would be a constant.
 *  - **Layer locks fired on 1 level in 175.** They were *derived* from the
 *    artwork's region structure, and that structure mostly refused to
 *    cooperate: a region's arrows escape in all directions, so regions
 *    mutually block and cannot be separated by a lock.
 *
 * The lesson is that a constraint derived from structure that may not exist
 * will usually not exist. A numbered sequence is *imposed* instead, so it
 * fires on every board it is asked for, and it works in both modes.
 *
 * ## Why it cannot produce an unsolvable board
 *
 * Numbers add "wait for lower numbers" edges to the dependency graph, and
 * added edges can create a cycle, which here means a dead board. The
 * construction rules that out rather than testing for it: keys are handed out
 * **in the order the solver actually cleared the board**. In a valid clear
 * order every blocker appears before the arrow it blocks, so an arrow's
 * numbered predecessors are all arrows that were already leaving earlier
 * anyway. The new edges therefore point the same way as the existing ones,
 * and a DAG plus edges that agree with it is still a DAG.
 *
 * The generator re-solves afterwards regardless, because "the construction is
 * sound" is how unsolvable levels get shipped.
 *
 * ## Why it is fair
 *
 * Every numbered arrow wears its number. A constraint the player cannot see
 * is indistinguishable from a bug - and worse, an invisible lock would
 * register as a *false affordance*, the heaviest term in the difficulty
 * score, so the board would measure as harder while merely being unfair.
 */
object SequencePlanner {

    /** Fewer than this is a decoration rather than a plan. */
    private const val MIN_KEYS = 3

    /**
     * Spread candidates over this much of the clear order. Numbering only
     * the first few arrows would be satisfied by playing normally; running
     * to the very end leaves a long tail with nothing to think about.
     */
    private const val SPAN_START = 0.10f
    private const val SPAN_END = 0.92f

    /**
     * Numbers [count] arrows along [order].
     *
     * Returns the arrows unchanged when the board is too small to carry a
     * sequence worth having.
     */
    fun apply(
        arrows: List<Arrow>,
        order: List<Int>,
        count: Int,
        random: Random
    ): List<Arrow> {
        if (count < MIN_KEYS || order.size < count * 2) return arrows

        val chosen = pickSpread(arrows, order, count, random)
        if (chosen.size < MIN_KEYS) return arrows

        val keyOf = chosen.withIndex().associate { (index, id) -> id to index + 1 }
        return arrows.map { arrow ->
            keyOf[arrow.id]?.let { arrow.copy(sequenceKey = it) } ?: arrow
        }
    }

    /**
     * Picks ids spread through the clear order, preferring candidates that
     * are far from the previous pick on the board.
     *
     * Spacing them through the order is what makes the sequence bite: two
     * consecutive numbers would be cleared in that order by accident.
     * Spacing them across the *board* is what makes it interesting, because
     * the honest difficulty metrics say the real cost to a player is the
     * distance their eye has to travel between one move and the next.
     */
    private fun pickSpread(
        arrows: List<Arrow>,
        order: List<Int>,
        count: Int,
        random: Random
    ): List<Int> {
        val positionOf = arrows.associate { it.id to it.head }
        val span = SPAN_END - SPAN_START
        val chosen = mutableListOf<Int>()
        var previous: Cell? = null

        for (slot in 0 until count) {
            val centre = SPAN_START + span * slot / (count - 1).coerceAtLeast(1)
            val window = windowAround(centre, order.size, count)

            val candidates = window.map { order[it] }.filter { it !in chosen }
            if (candidates.isEmpty()) continue

            val best = previous?.let { from ->
                candidates.maxByOrNull { id ->
                    positionOf[id]?.let { to -> distance(from, to) } ?: 0f
                }
            } ?: candidates[random.nextInt(candidates.size)]

            chosen.add(best)
            previous = positionOf[best]
        }
        return chosen
    }

    /** Indices to consider for one slot, clamped to the order's bounds. */
    private fun windowAround(centre: Float, size: Int, count: Int): IntRange {
        val half = (size / (count * 3)).coerceAtLeast(1)
        val mid = (centre * (size - 1)).toInt()
        return (mid - half).coerceAtLeast(0)..(mid + half).coerceAtMost(size - 1)
    }

    private fun distance(a: Cell, b: Cell): Float =
        hypot((a.row - b.row).toFloat(), (a.col - b.col).toFloat())
}
