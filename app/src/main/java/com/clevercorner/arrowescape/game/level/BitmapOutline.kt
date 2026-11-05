package com.clevercorner.arrowescape.game.level

import com.clevercorner.arrowescape.game.curve.Point2D

/**
 * Traces the boundary of an ASCII bitmap into closed polygon loops.
 *
 * Pure pixel geometry, no art: a `'#'` cell contributes an edge for every
 * side that touches a non-`'#'` neighbour (or the grid edge); a side shared by
 * two filled cells is interior and contributes nothing. What survives is
 * exactly the bitmap's outline, including an inner loop wherever the bitmap
 * has a hole - `circle_ring`, `square_hollow` and the rest fall out of this
 * for free, with no per-shape special-casing, because [ShapePath.contains]
 * already fills by even-odd across every loop it is given.
 *
 * This is the vector-ification of [LevelDesigns]' 59 hand-drawn templates: it
 * turns a bitmap into geometry algorithmically, so [TemplateMask] can
 * rasterise it at any grid size the way [DesignMask] already does for the
 * illustrated animals - no redrawing required.
 */
internal object BitmapOutline {

    private data class Edge(val from: Point2D, val to: Point2D)

    /** Closed loops in grid-corner coordinates: cell (r, c) spans x in [c, c+1], y in [r, r+1]. */
    fun trace(template: List<String>): List<List<Point2D>> {
        val rows = template.size
        if (rows == 0) return emptyList()
        val cols = template.maxOf { it.length }

        fun filled(r: Int, c: Int): Boolean =
            r in 0 until rows && c in 0 until cols && template[r].getOrNull(c) == '#'

        // Walk clockwise around every filled cell, keeping only the sides
        // that face a non-filled neighbour. A side between two filled cells
        // is claimed by neither, so it is never added - which is exactly
        // what deletes the grid lines from the interior of a solid block.
        val edges = mutableListOf<Edge>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!filled(r, c)) continue
                val x0 = c.toFloat()
                val x1 = (c + 1).toFloat()
                val y0 = r.toFloat()
                val y1 = (r + 1).toFloat()

                if (!filled(r - 1, c)) edges += Edge(Point2D(x0, y0), Point2D(x1, y0)) // top
                if (!filled(r, c + 1)) edges += Edge(Point2D(x1, y0), Point2D(x1, y1)) // right
                if (!filled(r + 1, c)) edges += Edge(Point2D(x1, y1), Point2D(x0, y1)) // bottom
                if (!filled(r, c - 1)) edges += Edge(Point2D(x0, y1), Point2D(x0, y0)) // left
            }
        }

        return linkIntoLoops(edges)
    }

    /**
     * Each boundary point has exactly one outgoing and one incoming edge (a
     * pixel boundary never branches except at a single-pixel diagonal pinch,
     * which [LevelDesigns]' hand-drawn templates do not produce), so greedily
     * following `from -> to` chains until a loop closes is enough to recover
     * every contour - outer silhouettes and inner holes alike.
     */
    private fun linkIntoLoops(edges: List<Edge>): List<List<Point2D>> {
        if (edges.isEmpty()) return emptyList()

        val byStart = HashMap<Point2D, ArrayDeque<Edge>>()
        for (edge in edges) byStart.getOrPut(edge.from) { ArrayDeque() }.add(edge)

        val loops = mutableListOf<List<Point2D>>()
        for (seed in edges) {
            val queue = byStart[seed.from] ?: continue
            if (!queue.remove(seed)) continue // already consumed by an earlier loop

            val loop = mutableListOf(seed.from)
            var current = seed.to
            // Hard cap rather than "until back at start": a malformed template
            // (stray punctuation) must not spin the tracer forever.
            var guard = edges.size + 1
            while (current != seed.from && guard-- > 0) {
                loop.add(current)
                val next = byStart[current]?.removeFirstOrNull() ?: break
                current = next.to
            }
            if (loop.size >= 3) loops.add(loop)
        }
        return loops
    }
}
