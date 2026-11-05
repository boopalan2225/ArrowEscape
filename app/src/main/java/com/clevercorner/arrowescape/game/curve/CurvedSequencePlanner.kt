package com.clevercorner.arrowescape.game.curve

/**
 * Numbers a handful of curved arrows so the board demands a plan, not just a
 * lane. The curved-mode counterpart of `SequencePlanner` in classic mode -
 * see that file for the full case for this mechanic over move budgets and
 * layer locks, both of which were tried first and rejected or found inert.
 *
 * ## Why this is safe
 *
 * Numbers add "wait for every lower number" edges to the blocking graph (see
 * [withOrderingRules]), and added edges can create a cycle, which here means
 * a dead board. The construction rules that out rather than testing for it:
 * keys are handed out **in the order the solver actually cleared the board**.
 * In a valid clear order every blocker appears before the arrow it blocks, so
 * an arrow's numbered predecessors were already leaving earlier anyway - the
 * new edges point the same way the existing ones do, and a DAG plus edges
 * that agree with it is still a DAG.
 *
 * [apply] still re-solves the result regardless, because "the construction is
 * sound" is how unsolvable levels get shipped.
 */
object CurvedSequencePlanner {

    /** Fewer numbers than this is a decoration rather than a plan. */
    private const val MIN_SEQUENCE = 3

    /** Where along the clear order candidates are spread; see [apply]. */
    private const val SPAN_START = 0.10f
    private const val SPAN_END = 0.92f

    /**
     * Numbers [count] arrows spread along [order], then checks the board
     * still solves.
     *
     * Candidates are spaced across the *board* as well as across the order:
     * two numbers that sit side by side in the clear order would be cleared
     * in that order by accident, and the honest difficulty metrics say the
     * real cost to a player is the distance their eye travels between one
     * move and the next, so spreading the picks is what makes the sequence
     * bite rather than decorate.
     */
    fun apply(
        arrows: List<CurvedArrow>,
        order: List<Int>,
        count: Int,
        board: CurvedGameEngine.Board
    ): List<CurvedArrow> {
        if (count < MIN_SEQUENCE || order.size < count * 2) return arrows

        val byId = arrows.associateBy { it.id }
        val chosen = mutableListOf<Int>()
        var previous: Point2D? = null

        for (slot in 0 until count) {
            val centre = SPAN_START + (SPAN_END - SPAN_START) * slot / (count - 1).coerceAtLeast(1)
            val mid = (centre * (order.size - 1)).toInt()
            val half = (order.size / (count * 3)).coerceAtLeast(1)
            val window = (mid - half).coerceAtLeast(0)..(mid + half).coerceAtMost(order.size - 1)

            val candidates = window.map { order[it] }.filter { it !in chosen && byId[it] != null }
            if (candidates.isEmpty()) continue

            val best = previous?.let { from ->
                candidates.maxByOrNull { id -> byId[id]!!.startPoint.distanceTo(from) }
            } ?: candidates.first()

            chosen.add(best)
            previous = byId[best]?.startPoint
        }
        if (chosen.size < MIN_SEQUENCE) return arrows

        val keyOf = chosen.withIndex().associate { (index, id) -> id to index + 1 }
        val keyed = arrows.map { arrow ->
            keyOf[arrow.id]?.let { arrow.copy(sequenceKey = it) } ?: arrow
        }
        return if (CurvedGameEngine.analyze(keyed, board).solved) keyed else arrows
    }
}
