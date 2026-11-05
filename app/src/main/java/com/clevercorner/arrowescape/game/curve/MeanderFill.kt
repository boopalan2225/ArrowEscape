package com.clevercorner.arrowescape.game.curve

import kotlin.random.Random

/**
 * Fills a region with long serpentine strokes that switch back on themselves -
 * the dominant look in every piece of the reference art, and the one thing
 * [CurveGenerator]'s streamline fill cannot produce by construction.
 *
 * A streamline is turn-rate capped (`MAX_TURN_PER_STEP`) so that it stays
 * smooth. That cap is exactly what forbids a meander: reversing direction is a
 * turn of pi, which at 0.22 rad/step needs fourteen steps, and fourteen steps
 * of drifting is long enough to leave the region entirely. So the reference's
 * signature move - run to the edge, hairpin, run back alongside yourself - is
 * unreachable no matter how the field is tuned. It needs a different
 * generator, not a different parameter.
 *
 * The approach is a self-avoiding walk on a coarse lattice:
 *
 *  1. Lay a 4-connected lattice over the region at a pitch of one stroke plus
 *     one gap. The pitch *is* the spacing between neighbouring lanes, which is
 *     why the result reads as deliberate ruling rather than as packing.
 *  2. Walk it, preferring the neighbour with the fewest onward moves
 *     (Warnsdorff's rule). Greedily taking the most-constrained cell first is
 *     what produces one long snake instead of a sprawl that strands isolated
 *     cells behind it.
 *  3. Round the lattice's right angles off with Chaikin smoothing, so a
 *     hairpin reads as a hairpin and not as a staircase.
 *
 * 4-connected on purpose: allowing diagonals produces soft zig-zags, while
 * restricting to the axes produces the square-shouldered switchback the
 * references actually use.
 *
 * Pure geometry, no Android dependency. Admission to the board still goes
 * through [CurveGenerator.placeWithClearEscape] like every other fill mode, so
 * meanders cannot smuggle an unsolvable board past the dependency graph.
 */
object MeanderFill {

    /** Lattice moves. Axes only - see the note on 4-connectivity above. */
    private val STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /** Chaikin passes used to round the lattice corners. Two is plenty. */
    private const val SMOOTHING_PASSES = 2

    /** A chain shorter than this is a stub, not a meander. */
    private const val MIN_CHAIN_CELLS = 4

    /**
     * Fill [shape] with meandering arrows.
     *
     * Returns empty when the region is too small to hold a lattice worth
     * walking - an owl's eye is three cells across and a "meander" in it is
     * just a dash. Callers are expected to fall back to the streamline fill
     * in that case rather than leaving a hole.
     */
    fun generateArrows(
        shape: ShapePath,
        config: CurveGenerator.GenerationConfig,
        random: Random,
        exclusions: List<ShapePath>,
        startId: Int,
        canvas: PackingCanvas
    ): List<CurvedArrow> {
        val (boundsMin, boundsMax) = shape.bounds
        val span = maxOf(boundsMax.x - boundsMin.x, boundsMax.y - boundsMin.y)

        // One lane of stroke plus one lane of gap. This single number is what
        // makes the output look ruled rather than crammed.
        val pitch = config.strokeWidth + config.arrowSpacing
        if (span < pitch * MIN_CHAIN_CELLS) return emptyList()

        val grid = canvas.grid
        val collideRadius = config.strokeWidth * 1.15f
        val occupyRadius = config.strokeWidth * 0.75f

        val cfg = config.copy(
            arrowMaxLength = minOf(config.arrowMaxLength, span * 1.2f),
            arrowMinLength = maxOf(
                minOf(config.arrowMinLength, span * 0.18f),
                ArrowHeadGeometry.minBodyLength(config.strokeWidth)
            )
        )

        val arrows = mutableListOf<CurvedArrow>()
        var arrowId = startId

        for (chain in chains(shape, pitch, exclusions, grid, config.strokeWidth, random)) {
            // A meander is usually far longer than one arrow may be, so it is
            // chopped into arrow-sized runs with a gap between them - the same
            // treatment a traced contour gets, and for the same reason: an
            // unbroken pipe reads as a pipe, not as a line of arrows.
            for (run in CurveGenerator.splitIntoRuns(chain, cfg, exclusions)) {
                val points = CurveGenerator.truncateAtCollision(run, grid, collideRadius)
                val arrow = CurveGenerator.placeWithClearEscape(
                    points = points,
                    cfg = cfg,
                    id = arrowId,
                    tone = CurveGenerator.toneAt(points, boundsMin, boundsMax, cfg.colorPalette.size),
                    canvas = canvas,
                    random = random
                ) ?: continue

                grid.occupyPath(arrow.polyline, occupyRadius)
                arrows.add(arrow)
                arrowId++
            }
        }

        return arrows
    }

    /**
     * Walk the lattice, returning one smoothed polyline per self-avoiding
     * chain. Repeats until no startable cell is left, so a region split in two
     * by something already drawn still fills both halves.
     */
    private fun chains(
        shape: ShapePath,
        pitch: Float,
        exclusions: List<ShapePath>,
        grid: OccupancyGrid,
        strokeWidth: Float,
        random: Random
    ): List<List<Point2D>> {
        val lattice = Lattice(shape, pitch, exclusions, grid, strokeWidth)
        val chains = mutableListOf<List<Point2D>>()

        while (true) {
            val start = lattice.mostConstrainedFreeCell(random) ?: break
            val cells = lattice.walkFrom(start, random)
            if (cells.size < MIN_CHAIN_CELLS) continue
            chains.add(smoothed(cells.map(lattice::centreOf)))
        }

        return chains
    }

    /**
     * Chaikin corner-cutting. Endpoints are kept so a chain still starts and
     * ends where the walk decided it should; only the corners move.
     */
    private fun smoothed(points: List<Point2D>): List<Point2D> {
        var result = points
        repeat(SMOOTHING_PASSES) {
            if (result.size < 3) return result
            val next = mutableListOf(result.first())
            for (i in 0 until result.size - 1) {
                val p = result[i]
                val q = result[i + 1]
                next.add(Point2D(p.x * 0.75f + q.x * 0.25f, p.y * 0.75f + q.y * 0.25f))
                next.add(Point2D(p.x * 0.25f + q.x * 0.75f, p.y * 0.25f + q.y * 0.75f))
            }
            next.add(result.last())
            result = next
        }
        return result
    }

    /**
     * The coarse lattice the walk happens on. A cell is free when its centre
     * is inside the region, outside every exclusion, and not already inked -
     * so a meander flows around detail that was drawn before it, exactly as
     * the streamline fill does.
     */
    private class Lattice(
        shape: ShapePath,
        private val pitch: Float,
        exclusions: List<ShapePath>,
        grid: OccupancyGrid,
        strokeWidth: Float
    ) {
        private val originX: Float
        private val originY: Float
        private val cols: Int
        private val rows: Int
        private val free: BooleanArray

        init {
            val (boundsMin, boundsMax) = shape.bounds
            originX = boundsMin.x
            originY = boundsMin.y
            cols = maxOf(1, ((boundsMax.x - boundsMin.x) / pitch).toInt())
            rows = maxOf(1, ((boundsMax.y - boundsMin.y) / pitch).toInt())
            free = BooleanArray(cols * rows)

            // Clearance of half a stroke: the lattice centre is where ink will
            // actually sit, so a cell whose stroke would overlap something
            // existing is not free however empty its centre point looks.
            val clearance = strokeWidth * 0.75f
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val centre = centreOf(col, row)
                    free[row * cols + col] = shape.contains(centre) &&
                        exclusions.none { it.contains(centre) } &&
                        !grid.isOccupied(centre, clearance)
                }
            }
        }

        fun centreOf(cell: Int): Point2D = centreOf(cell % cols, cell / cols)

        private fun centreOf(col: Int, row: Int) =
            Point2D(originX + (col + 0.5f) * pitch, originY + (row + 0.5f) * pitch)

        private fun neighbours(cell: Int): List<Int> {
            val col = cell % cols
            val row = cell / cols
            return STEPS.mapNotNull { (dc, dr) ->
                val c = col + dc
                val r = row + dr
                if (c in 0 until cols && r in 0 until rows) r * cols + c else null
            }
        }

        private fun freeDegree(cell: Int): Int = neighbours(cell).count { free[it] }

        /**
         * Start where the walk has the least room, which is the cell hardest
         * to reach later. Starting in the open middle instead is what leaves
         * unreachable pockets around the rim.
         */
        fun mostConstrainedFreeCell(random: Random): Int? {
            var best = Int.MAX_VALUE
            val candidates = mutableListOf<Int>()
            for (cell in free.indices) {
                if (!free[cell]) continue
                val degree = freeDegree(cell)
                if (degree < best) {
                    best = degree
                    candidates.clear()
                }
                if (degree == best) candidates.add(cell)
            }
            return candidates.randomOrNull(random)
        }

        /**
         * Warnsdorff's rule: always step to the neighbour with the fewest
         * onward moves. Taking the roomiest option instead feels natural and
         * is precisely wrong - it saves the cramped cells for last, by which
         * point they are unreachable and the fill is full of holes.
         */
        fun walkFrom(start: Int, random: Random): List<Int> {
            val path = mutableListOf(start)
            free[start] = false
            var current = start

            while (true) {
                val options = neighbours(current).filter { free[it] }
                if (options.isEmpty()) break
                val fewest = options.minOf { freeDegree(it) }
                val next = options.filter { freeDegree(it) == fewest }.random(random)
                free[next] = false
                path.add(next)
                current = next
            }

            return path
        }
    }
}
