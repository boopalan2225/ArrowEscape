package com.clevercorner.arrowescape.game.curve

/**
 * Outline edges bucketed by horizontal band, for even-odd containment.
 *
 * "Is this point inside the shape?" is answered by counting the outline
 * edges that cross the ray going right from it. Only edges that straddle the
 * point's y can possibly cross, which on an authored silhouette is a dozen
 * out of two thousand - so testing all of them was almost entirely wasted
 * work, and it was the hottest thing left in level generation once the
 * nearest-point scan was indexed.
 *
 * Exact, and easily so: parity is a count, and a count does not care what
 * order it is taken in. Every edge that could cross is in the band, no edge
 * is in a band twice, and the per-edge test is unchanged - so the answer is
 * the answer the full scan gave, every time.
 */
internal class ContourCrossings(contours: List<List<Point2D>>) {

    /** Edge endpoints, flattened: edge `e` runs from (x0,y0) to (x1,y1). */
    private val x0: FloatArray
    private val y0: FloatArray
    private val x1: FloatArray
    private val y1: FloatArray

    private val bands: Int
    private val bandHeight: Float
    private val originY: Float

    /** Edge ids grouped by band: `bandStart[b] until bandStart[b + 1]`. */
    private val bandStart: IntArray
    private val bandEdges: IntArray

    init {
        val total = contours.sumOf { if (it.size >= 2) it.size else 0 }
        x0 = FloatArray(total); y0 = FloatArray(total)
        x1 = FloatArray(total); y1 = FloatArray(total)

        var next = 0
        for (ring in contours) {
            if (ring.size < 2) continue
            var previous = ring.size - 1
            for (current in ring.indices) {
                x0[next] = ring[current].x; y0[next] = ring[current].y
                x1[next] = ring[previous].x; y1[next] = ring[previous].y
                previous = current
                next++
            }
        }

        if (total == 0) {
            bands = 1; bandHeight = 1f; originY = 0f
            bandStart = IntArray(2); bandEdges = IntArray(0)
        } else {
            originY = y0.min()
            val span = (y0.max() - originY).coerceAtLeast(1f)
            bands = (total / EDGES_PER_BAND).coerceIn(1, MAX_BANDS)
            bandHeight = span / bands

            // Counting sort, same as OutlineIndex: size the buckets, fill them.
            val counts = IntArray(bands + 1)
            forEachEdgeBand { _, band -> counts[band + 1]++ }
            for (b in 1 until counts.size) counts[b] += counts[b - 1]
            bandStart = counts

            val cursor = bandStart.copyOf()
            bandEdges = IntArray(cursor.last())
            forEachEdgeBand { edge, band -> bandEdges[cursor[band]++] = edge }
        }
    }

    /** Even-odd containment: true if the ray going right crosses an odd number of edges. */
    fun contains(x: Float, y: Float): Boolean {
        val band = bandOf(y)
        if (band < 0 || band >= bands) return false

        var inside = false
        for (slot in bandStart[band] until bandStart[band + 1]) {
            val edge = bandEdges[slot]
            val yi = y0[edge]
            val yj = y1[edge]
            if ((yi > y) != (yj > y)) {
                val crossing = x0[edge] + (y - yi) / (yj - yi) * (x1[edge] - x0[edge])
                if (crossing > x) inside = !inside
            }
        }
        return inside
    }

    /** Every (edge, band) pair an edge belongs to - it may span several. */
    private inline fun forEachEdgeBand(visit: (edge: Int, band: Int) -> Unit) {
        for (edge in x0.indices) {
            val lowest = bandOf(minOf(y0[edge], y1[edge])).coerceIn(0, bands - 1)
            val highest = bandOf(maxOf(y0[edge], y1[edge])).coerceIn(0, bands - 1)
            for (band in lowest..highest) visit(edge, band)
        }
    }

    private fun bandOf(y: Float): Int = ((y - originY) / bandHeight).toInt()

    private companion object {
        /** Tuning only - correctness holds for any band count. */
        const val EDGES_PER_BAND = 12
        const val MAX_BANDS = 512
    }
}
