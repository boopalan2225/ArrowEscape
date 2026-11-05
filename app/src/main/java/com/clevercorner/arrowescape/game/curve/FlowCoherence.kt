package com.clevercorner.arrowescape.game.curve

import kotlin.math.hypot

/**
 * How much neighbouring arrows agree about which way the drawing flows.
 *
 * [CurveGenerator] traces its strokes along a continuous flow field, so the
 * arrows it lays down start out beautifully parallel - that local agreement
 * is most of what makes reference artwork read as fur, feathers or antlers
 * rather than as a heap of lines. The orientation hill-climb then reverses
 * roughly half of them in pursuit of tightness.
 *
 * Each individual flip is *geometrically* free: the drawn curve does not
 * move, only the end the head sits on. But it is not **visually** free, since
 * direction is the one thing the eye reads off an arrow. The search was
 * throwing away the flow field's whole contribution and nothing was scoring
 * the loss, so nothing stopped it.
 *
 * This puts a number on that loss. The climb can then trade coherence against
 * tightness deliberately instead of spending it for free.
 *
 * Built once per board: both headings of every arrow and the neighbour pairs
 * are fixed by geometry, and flipping changes only *which* heading applies.
 * Scoring a candidate is therefore a walk over two flat arrays.
 */
class FlowCoherence(arrows: List<CurvedArrow>) {

    /**
     * Headings for every arrow in both orientations, interleaved as
     * `[slot * 2 + flipped]`.
     *
     * Reversing an arrow does **not** simply negate its heading: the head
     * moves to the other end of the curve and takes *that* end's tangent,
     * which on anything bendier than a straight line points somewhere else
     * entirely. Both are measured from real geometry for that reason.
     */
    private val headX = FloatArray(arrows.size * 2)
    private val headY = FloatArray(arrows.size * 2)

    /** Neighbour pairs, split across two arrays to keep scoring allocation-free. */
    private val pairA: IntArray
    private val pairB: IntArray

    init {
        arrows.forEachIndexed { slot, arrow ->
            val forward = arrow.direction
            val backward = arrow.reversed().direction
            headX[slot * 2] = forward.x
            headY[slot * 2] = forward.y
            headX[slot * 2 + 1] = backward.x
            headY[slot * 2 + 1] = backward.y
        }

        val (a, b) = nearestPairs(arrows)
        pairA = a
        pairB = b
    }

    /**
     * Agreement across the board in 0..1, where 1 is every neighbouring pair
     * pointing the same way.
     *
     * The raw measure is the cosine between two headings, which lives in
     * -1..1; rescaling to 0..1 lets it sit alongside tightness in the climb's
     * objective without one quietly dominating by range alone.
     */
    fun of(flips: BooleanArray): Float {
        if (pairA.isEmpty()) return 1f

        var total = 0f
        for (p in pairA.indices) {
            val a = pairA[p]
            val b = pairB[p]
            val i = a * 2 + if (flips[a]) 1 else 0
            val j = b * 2 + if (flips[b]) 1 else 0
            total += headX[i] * headX[j] + headY[i] * headY[j]
        }
        return (total / pairA.size + 1f) / 2f
    }

    private companion object {

        /**
         * Neighbours each arrow is compared against.
         *
         * Coherence is a *local* property - a stroke on the owl's wing has no
         * opinion about one on its foot - so this stays small. Widening it
         * does not make the measure more correct, only blurrier.
         */
        const val NEIGHBOURS = 5

        /**
         * The [NEIGHBOURS] closest arrows to each arrow, by midpoint, as a
         * deduplicated pair list.
         *
         * Brute force on purpose. A few hundred arrows is ~100k distance
         * checks *once* per board, next to a climb that runs thousands of
         * full solve simulations - a spatial index here would be structure
         * bought with no payoff.
         */
        fun nearestPairs(arrows: List<CurvedArrow>): Pair<IntArray, IntArray> {
            val count = arrows.size
            if (count < 2) return IntArray(0) to IntArray(0)

            val midX = FloatArray(count)
            val midY = FloatArray(count)
            arrows.forEachIndexed { slot, arrow ->
                val mid = arrow.pointAt(0.5f)
                midX[slot] = mid.x
                midY[slot] = mid.y
            }

            val take = minOf(NEIGHBOURS, count - 1)
            // A pair is one relationship, not two. Encoding low/high into a
            // single key is what stops a mutual neighbour counting twice and
            // quietly outvoting everyone else.
            val seen = LinkedHashSet<Long>(count * take)
            for (i in 0 until count) {
                val byDistance = (0 until count)
                    .filter { it != i }
                    .sortedBy { hypot(midX[it] - midX[i], midY[it] - midY[i]) }
                for (n in 0 until take) {
                    val j = byDistance[n]
                    val low = minOf(i, j).toLong()
                    val high = maxOf(i, j).toLong()
                    seen.add((low shl 32) or high)
                }
            }

            val a = IntArray(seen.size)
            val b = IntArray(seen.size)
            seen.forEachIndexed { index, key ->
                a[index] = (key shr 32).toInt()
                b[index] = (key and 0xFFFFFFFFL).toInt()
            }
            return a to b
        }
    }
}
