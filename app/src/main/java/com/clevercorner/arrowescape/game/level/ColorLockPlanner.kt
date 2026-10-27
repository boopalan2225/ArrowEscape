package com.clevercorner.arrowescape.game.level

import com.clevercorner.arrowescape.game.model.Arrow
import kotlin.random.Random

/**
 * Welds arrows into colour-locked pairs - two snakes that leave together or
 * not at all, ignoring each other on the way out.
 *
 * Pairs are the cheapest difficulty in the game: the picture does not change
 * one pixel, but the player now has to see two openings at once.
 *
 * Picking them is safe by construction rather than by trial. Given a known
 * solution order, arrows that are *consecutive* in it are welded: the first
 * can leave at that moment by definition, and the second can leave the moment
 * after, with only the first in its way - which is exactly the allowance a
 * locked pair gets. Every other move in the solution is untouched, so the
 * board stays solvable.
 */
object ColorLockPlanner {

    fun weld(
        arrows: List<Arrow>,
        solutionOrder: List<Int>,
        pairs: Int,
        random: Random
    ): List<Arrow> {
        if (pairs <= 0 || solutionOrder.size < 2) return arrows

        val slots = pickSlots(solutionOrder.size, pairs, random)
        if (slots.isEmpty()) return arrows

        val groupOf = HashMap<Int, Int>()
        slots.forEachIndexed { group, slot ->
            groupOf[solutionOrder[slot]] = group
            groupOf[solutionOrder[slot + 1]] = group
        }

        return arrows.map { arrow ->
            groupOf[arrow.id]?.let { arrow.copy(colorGroup = it) } ?: arrow
        }
    }

    /**
     * Start indices of non-overlapping consecutive pairs, spread evenly over
     * the solution so the locks are not all clustered in the opening moves.
     */
    private fun pickSlots(orderSize: Int, pairs: Int, random: Random): List<Int> {
        val capacity = orderSize / 2
        val wanted = pairs.coerceAtMost(capacity)
        if (wanted <= 0) return emptyList()

        val stride = orderSize / wanted
        if (stride < 2) return emptyList()

        return (0 until wanted).map { index ->
            val windowStart = index * stride
            val jitter = random.nextInt(stride - 1)
            (windowStart + jitter).coerceAtMost(orderSize - 2)
        }.distinct()
    }
}
