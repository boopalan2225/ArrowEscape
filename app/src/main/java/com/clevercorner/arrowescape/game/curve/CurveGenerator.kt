package com.clevercorner.arrowescape.game.curve

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates curved arrows that fill a shape with organic, flowing patterns.
 *
 * The algorithm:
 * 1. Build a *continuous* flow field over the shape (no per-step randomness).
 * 2. Seed points on a jittered hex lattice, visited nearest-the-edge first.
 * 3. Trace streamlines with angular inertia so curves stay smooth.
 * 4. Rasterise each accepted streamline into an occupancy grid so the next
 *    arrow can pack tightly against it.
 */
object CurveGenerator {

    data class GenerationConfig(
        val arrowMinLength: Float = 30f,
        val arrowMaxLength: Float = 150f,
        val arrowSpacing: Float = 12f,
        val strokeWidth: Float = 6f,
        val bendiness: Float = 0.4f,      // 0 = straight, 1 = very curvy
        val swirl: Float = 0f,            // rotates the field, creates vortices
        val curl: Float = 0f,             // adds tight coils / switchbacks
        /**
         * Most arrows a new arrow may have to wait on. The puzzle-difficulty
         * dial for the packer: 0 only admits arrows with a fully clear exit
         * (sparse, gentle); higher values admit arrows tucked into pockets
         * behind others (dense, with real dependency chains).
         */
        val maxDependencies: Int = 0,
        val flowType: FlowType = FlowType.ORGANIC,
        val colorPalette: List<Long> = listOf(0xFF4A90D9)
    )

    enum class FlowType {
        ORGANIC,      // Natural swirling patterns
        RADIAL,       // From center outward
        HORIZONTAL,   // Left to right waves
        VERTICAL,     // Top to bottom waves
        SPIRAL,       // Spiral from center
        RING,         // Pure circles around the centre (eyes, coils)
        CONTOUR,      // Follows the shape outline, like feather/fur flow

        /**
         * Long switchback serpentines - see [MeanderFill].
         *
         * The odd one out: every other entry here names an angle field that
         * [FlowField] evaluates per point, while this one names a different
         * generator entirely, because a turn-rate-capped streamline cannot
         * hairpin by construction. It lives in this enum anyway so that a
         * region picks its fill the same way it picks anything else - a
         * `@meander` suffix on an SVG layer id, no second mechanism.
         */
        MEANDER
    }

    /** Turning rate cap, in radians per step. Keeps streamlines smooth. */
    private const val MAX_TURN_PER_STEP = 0.22f

    /** How many times a streamline may reflect off the boundary. */
    private const val MAX_BOUNCES = 6

    /** Minimum-length multipliers for the successive gap-filling passes. */
    private val PASS_MIN_LENGTH_FACTORS = listOf(1f, 0.5f, 0.25f)

    /** How often a stroke with no clear escape is shortened and retried. */
    private const val MAX_SHORTENINGS = 4
    private const val SHORTEN_FACTOR = 0.72f

    /**
     * Generate curved arrows that fill the given shape.
     *
     * Arrows are placed inside-out, and each is only accepted if the board's
     * dependency graph stays acyclic (see [DependencyTracker]). Solvability is
     * therefore built in rather than repaired afterwards, while arrows tucked
     * into pockets - which a strict clear-exit rule used to reject - can still
     * be placed.
     *
     * [exclusions] are regions already filled by other layers; seeds inside
     * them are skipped so interior detail (an owl's belly, a panda's grass)
     * is not overdrawn by the layer beneath it.
     */
    fun generateArrows(
        shape: ShapePath,
        config: GenerationConfig = GenerationConfig(),
        random: Random = Random.Default,
        exclusions: List<ShapePath> = emptyList(),
        startId: Int = 0,
        canvas: PackingCanvas
    ): List<CurvedArrow> {
        if (shape.toOutlineSegments().isEmpty()) return emptyList()

        // Scale arrow length to the region. A 400px stroke makes sense in a
        // deer's body but is absurd inside an owl's eye, where it just
        // ricochets until it has blocked the entire region.
        val (boundsMin, boundsMax) = shape.bounds
        val span = maxOf(boundsMax.x - boundsMin.x, boundsMax.y - boundsMin.y)
        val cfg = config.copy(
            arrowMaxLength = minOf(config.arrowMaxLength, span * 1.2f),
            arrowMinLength = minOf(config.arrowMinLength, span * 0.18f)
        )

        val field = FlowField(shape, cfg)
        val grid = canvas.grid

        // Every packing radius is driven by stroke width, because that is
        // the only thing actually occupying space.
        val seedClearance = cfg.strokeWidth * 1.5f
        val occupyRadius = cfg.strokeWidth * 0.75f
        val collideRadius = cfg.strokeWidth * 1.15f

        val arrows = mutableListOf<CurvedArrow>()
        var arrowId = startId

        val seeds = generateSeedPoints(shape, cfg)

        // Several passes over the same seeds. The first lays down the long,
        // flowing strokes; later passes accept progressively shorter ones to
        // fill the gaps those leave. Every arrow is admitted only if it keeps
        // the dependency graph acyclic, so extra passes cannot break
        // solvability, they only add ink.
        // Later passes accept shorter arrows to fill gaps, but never shorter
        // than one that can carry a readable head: a stub whose head is most
        // of its length conveys no direction, which is the whole point of an
        // arrow. This floor is what keeps tight corners legible.
        for (minLengthFactor in PASS_MIN_LENGTH_FACTORS) {
            val passCfg = cfg.copy(
                arrowMinLength = maxOf(
                    cfg.arrowMinLength * minLengthFactor,
                    ArrowHeadGeometry.minBodyLength(cfg.strokeWidth)
                )
            )

            for (seed in seeds) {
                if (exclusions.any { it.contains(seed) }) continue
                if (grid.isOccupied(seed, seedClearance)) continue

                val traced = traceFlowLine(seed, shape, field, passCfg, exclusions)

                // Truncate where the path first touches something already
                // drawn. Rejecting the whole arrow on any collision was
                // catastrophic for density: long wandering strokes nearly
                // always clip a neighbour somewhere.
                val points = truncateAtCollision(traced, grid, collideRadius)

                val arrow = placeWithClearEscape(
                    points = points,
                    cfg = passCfg,
                    id = arrowId,
                    tone = toneAt(points, boundsMin, boundsMax, passCfg.colorPalette.size),
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
     * Strokes that *follow* a region's contour instead of filling it.
     *
     * This is what [RegionKind.OUTLINE] and [RegionKind.FEATURE] are made of:
     * a line round the silhouette, or the small deliberate arcs that are
     * themselves the detail. Filling cannot produce either - a packed area
     * reads as a mass, and a drawing needs edges too.
     *
     * Admission goes through the same [placeWithClearEscape] as every other
     * arrow, so a contour pass cannot smuggle an unsolvable board past the
     * dependency graph. Some strokes will be refused - a closed ring is a
     * natural dependency cycle - and the gaps that leaves are honest.
     */
    fun generateContourArrows(
        shape: ShapePath,
        config: GenerationConfig = GenerationConfig(),
        random: Random = Random.Default,
        exclusions: List<ShapePath> = emptyList(),
        startId: Int = 0,
        canvas: PackingCanvas
    ): List<CurvedArrow> {
        val contours = shape.contours
        if (contours.isEmpty()) return emptyList()

        val (boundsMin, boundsMax) = shape.bounds
        val span = maxOf(boundsMax.x - boundsMin.x, boundsMax.y - boundsMin.y)
        val cfg = config.copy(
            arrowMaxLength = minOf(config.arrowMaxLength, span * 1.2f),
            arrowMinLength = minOf(
                config.arrowMinLength,
                maxOf(span * 0.18f, ArrowHeadGeometry.minBodyLength(config.strokeWidth))
            )
        )

        val grid = canvas.grid
        val collideRadius = cfg.strokeWidth * 1.15f
        val occupyRadius = cfg.strokeWidth * 0.75f

        val arrows = mutableListOf<CurvedArrow>()
        var arrowId = startId

        for (ring in contours) {
            for (run in splitIntoRuns(ring, cfg, exclusions)) {
                val points = truncateAtCollision(run, grid, collideRadius)
                val arrow = placeWithClearEscape(
                    points = points,
                    cfg = cfg,
                    id = arrowId,
                    tone = toneAt(points, boundsMin, boundsMax, cfg.colorPalette.size),
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
     * Chop a contour into arrow-sized runs, leaving a gap between them.
     *
     * The gap matters: without it a ring becomes one unbroken pipe and reads
     * as a border, not as a line of arrows. Points inside an [exclusions]
     * region end the current run, so a contour passing behind something
     * already drawn breaks around it rather than through it.
     */
    internal fun splitIntoRuns(
        ring: List<Point2D>,
        cfg: GenerationConfig,
        exclusions: List<ShapePath>
    ): List<List<Point2D>> {
        val runs = mutableListOf<List<Point2D>>()
        var current = mutableListOf<Point2D>()
        var length = 0f
        var skipping = 0f
        // Tracked independently of [current], which empties on every flush -
        // measuring the gap against it would make the gap never close.
        var previous: Point2D? = null

        fun flush() {
            if (length >= cfg.arrowMinLength && current.size >= 2) runs.add(current)
            current = mutableListOf()
            length = 0f
        }

        for (point in ring) {
            val step = previous?.distanceTo(point) ?: 0f
            previous = point

            if (exclusions.any { it.contains(point) }) {
                flush()
                skipping = 0f
                continue
            }
            if (skipping > 0f) {
                skipping -= step
                continue
            }

            if (current.isNotEmpty()) length += step
            current.add(point)
            if (length >= cfg.arrowMaxLength) {
                flush()
                skipping = cfg.arrowSpacing
            }
        }
        flush()
        return runs
    }

    /**
     * Build an arrow from [points] and admit it to the board in whichever
     * orientation keeps the dependency graph acyclic. If neither works,
     * shorten the stroke and try again - a shorter stroke ends somewhere else
     * and often points somewhere open.
     */
    internal fun placeWithClearEscape(
        points: List<Point2D>,
        cfg: GenerationConfig,
        id: Int,
        tone: Int,
        canvas: PackingCanvas,
        random: Random
    ): CurvedArrow? {
        var current = points

        repeat(MAX_SHORTENINGS + 1) { attempt ->
            // Two different questions, and a stroke has to answer both: is
            // there enough of it, and did it go anywhere? A tight coil has
            // ample arc length and occupies a smudge - legible as ink,
            // illegible as an arrow.
            if (current.size < 2 || polylineLength(current) < cfg.arrowMinLength) return null
            if (boundingDiagonal(current) < ArrowHeadGeometry.minVisualExtent(cfg.strokeWidth)) return null

            val forward = CurvedArrow.throughPoints(
                id = id,
                points = trimToLength(current, cfg.arrowMaxLength),
                color = cfg.colorPalette[tone.coerceIn(0, cfg.colorPalette.lastIndex)],
                tension = 0.32f
            ).copy(strokeWidth = cfg.strokeWidth)

            val admitted = canvas.dependencies.tryAdd(
                candidates = listOf(forward, forward.reversed()),
                maxDependencies = cfg.maxDependencies,
                random = random
            )
            if (admitted != null) return admitted

            // Alternate which end is trimmed so both heads get a new chance.
            val cut = (current.size * SHORTEN_FACTOR).toInt()
            current = if (attempt % 2 == 0) current.take(cut) else current.takeLast(cut)
        }

        return null
    }

    /**
     * Cut a traced path at the first point that collides with existing ink.
     */
    internal fun truncateAtCollision(
        points: List<Point2D>,
        grid: OccupancyGrid,
        radius: Float
    ): List<Point2D> {
        val result = mutableListOf<Point2D>()
        for (p in points) {
            if (grid.isOccupied(p, radius)) break
            result.add(p)
        }
        return result
    }

    /**
     * Seed points on a jittered hex lattice. Hex packing beats a square grid
     * because it leaves no axis-aligned corridors, which is exactly what made
     * the old output look like stacked zig-zags.
     */
    private fun generateSeedPoints(
        shape: ShapePath,
        config: GenerationConfig
    ): List<Point2D> {
        // Seed densely relative to the ink, not to the nominal spacing: the
        // lattice only proposes candidates, the occupancy grid decides what
        // actually fits.
        val spacing = minOf(config.arrowSpacing, config.strokeWidth * 2f)
        val rowHeight = spacing * 0.866f // sqrt(3)/2
        val (boundsMin, boundsMax) = shape.bounds
        val points = mutableListOf<Point2D>()

        var row = 0
        var y = boundsMin.y
        while (y <= boundsMax.y) {
            val xOffset = if (row % 2 == 0) 0f else spacing * 0.5f
            var x = boundsMin.x + xOffset
            while (x <= boundsMax.x) {
                val p = Point2D(x, y)
                if (shape.contains(p)) points.add(p)
                x += spacing
            }
            y += rowHeight
            row++
        }

        // Inside-out. Arrows placed first are the ones cleared last, so the
        // core of the shape must go down first and the rim last - that way
        // outer arrows can always point away from what is already there.
        val centre = shape.center
        return points.sortedBy { it.distanceTo(centre) }
    }

    /**
     * Trace a flow line that *stays inside* the shape, reflecting off the
     * boundary instead of stopping at it.
     *
     * Terminating on first boundary contact was why silhouettes read as
     * sparse scribbles: every streamline died the moment it drifted outward,
     * so nothing ever reached into ears, feet or antlers. Reflecting lets a
     * single arrow wander the whole region, which is exactly the long,
     * meandering look of the reference art.
     */
    private fun traceFlowLine(
        start: Point2D,
        shape: ShapePath,
        field: FlowField,
        config: GenerationConfig,
        exclusions: List<ShapePath>
    ): List<Point2D> {
        val stepSize = config.arrowSpacing * 0.55f
        val maxSteps = (config.arrowMaxLength / stepSize).toInt() + 8
        val maxTurn = MAX_TURN_PER_STEP * (0.35f + config.bendiness)

        // A point is valid only if it is inside the region AND outside every
        // layer already drawn - otherwise detail regions get overdrawn.
        fun valid(p: Point2D) = shape.contains(p) && exclusions.none { it.contains(p) }

        val selfClearance = config.strokeWidth * 1.6f
        val selfClearanceSteps = (selfClearance * 2f / stepSize).toInt() + 2

        val points = mutableListOf(start)
        var current = start
        var heading = field.angleAt(start)
        var bounces = 0

        for (step in 0 until maxSteps) {
            val desired = field.angleAt(current)
            heading += normalizeAngle(desired - heading).coerceIn(-maxTurn, maxTurn)

            var next = current + Point2D(cos(heading), sin(heading)) * stepSize

            if (!valid(next)) {
                if (bounces >= MAX_BOUNCES) break
                bounces++

                // Steer back toward the interior and retry from the same spot.
                heading = shape.inwardAngleAt(current)
                next = current + Point2D(cos(heading), sin(heading)) * stepSize

                // Still stuck in a narrow spur - stop cleanly.
                if (!valid(next)) break
            }

            // Stop before crossing our own earlier path. In narrow spurs a
            // stroke bounces off one wall, then the other, and retraces
            // itself - which piled up as a solid blob of ink. The most recent
            // few points are skipped, since those are just our own tail.
            val older = points.size - selfClearanceSteps
            if (older > 0 && (0 until older).any { points[it].distanceTo(next) < selfClearance }) break

            points.add(next)
            current = next
        }

        return points
    }

    /**
     * Which tone of a region's colour a stroke is painted in, from *where it
     * is* rather than from the order it happened to be placed in.
     *
     * Tones used to be dealt out by arrow id. The intent was texture - a big
     * region in one flat colour reads as a blob - but ids follow packing
     * order, which is only loosely spatial, so neighbouring strokes landed on
     * unrelated tones. That is not texture, it is static.
     *
     * A ramp across the region's bounds gives the same variety arranged the
     * way light actually falls, so the eye reads it as a shaded surface. The
     * palette runs light to dark (see [ArrowTones]), so lighting it from the
     * top-left is simply the ramp in its natural order.
     */
    internal fun toneAt(
        points: List<Point2D>,
        boundsMin: Point2D,
        boundsMax: Point2D,
        tones: Int
    ): Int {
        if (tones <= 1 || points.isEmpty()) return 0
        val mid = points[points.size / 2]
        val width = (boundsMax.x - boundsMin.x).coerceAtLeast(1f)
        val height = (boundsMax.y - boundsMin.y).coerceAtLeast(1f)
        val across = ((mid.x - boundsMin.x) / width + (mid.y - boundsMin.y) / height) / 2f
        return (across * tones).toInt().coerceIn(0, tones - 1)
    }

    private fun polylineLength(points: List<Point2D>): Float =
        (1 until points.size).fold(0f) { acc, i -> acc + points[i - 1].distanceTo(points[i]) }

    /**
     * Diagonal of the polyline's bounding box - how much board it actually
     * covers, as opposed to how much stroke it spent getting there.
     */
    private fun boundingDiagonal(points: List<Point2D>): Float {
        if (points.size < 2) return 0f
        var minX = points[0].x
        var maxX = minX
        var minY = points[0].y
        var maxY = minY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return hypot(maxX - minX, maxY - minY)
    }

    /** Wrap an angle delta into (-PI, PI]. */
    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        val twoPi = (2 * PI).toFloat()
        while (a > PI) a -= twoPi
        while (a < -PI) a += twoPi
        return a
    }

    /**
     * Trim waypoints to maximum length.
     */
    private fun trimToLength(points: List<Point2D>, maxLength: Float): List<Point2D> {
        if (points.size < 2) return points

        val result = mutableListOf(points[0])
        var accumulated = 0f

        for (i in 1 until points.size) {
            val segmentLength = points[i - 1].distanceTo(points[i])
            if (accumulated + segmentLength > maxLength) {
                val t = (maxLength - accumulated) / segmentLength
                result.add(points[i - 1].lerp(points[i], t))
                break
            }
            result.add(points[i])
            accumulated += segmentLength
        }

        return result
    }

    /**
     * A smooth, deterministic direction field over the shape. Sampling the same
     * point always yields the same angle, which is what makes neighbouring
     * streamlines run parallel instead of crossing into spaghetti.
     */
    private class FlowField(
        private val shape: ShapePath,
        private val config: GenerationConfig
    ) {
        private val center = shape.center
        private val cx = center.x
        private val cy = center.y

        fun angleAt(p: Point2D): Float {
            val base = when (config.flowType) {
                FlowType.ORGANIC -> organicAngle(p)
                FlowType.RADIAL -> atan2(p.y - cy, p.x - cx)
                FlowType.HORIZONTAL -> sin(p.y * 0.02f) * 0.45f
                FlowType.VERTICAL -> (PI / 2).toFloat() + sin(p.x * 0.02f) * 0.45f
                FlowType.SPIRAL -> atan2(p.y - cy, p.x - cx) + (PI / 3).toFloat()
                FlowType.RING -> atan2(p.y - cy, p.x - cx) + (PI / 2).toFloat()
                FlowType.CONTOUR -> shape.edgeTangentAt(p)
            // Only reachable if a meander region was routed through the
            // streamline tracer - a small-region fallback. Following the
            // contour is the least surprising thing to do there.
            FlowType.MEANDER -> shape.edgeTangentAt(p)
            }

            // Curl adds tight, high-frequency rotation on top of the base
            // field. This is what produces the coils and switchbacks that
            // give the reference art its hand-drawn density.
            val curlTerm = if (config.curl > 0f) {
                (sin(p.x * 0.055f) + cos(p.y * 0.065f)) * config.curl
            } else {
                0f
            }

            return base + config.swirl * organicAngle(p) * 0.5f + curlTerm
        }

        /** Two octaves of smooth trig noise. Cheap, stable, no gradient jumps. */
        private fun organicAngle(p: Point2D): Float {
            val n1 = sin(p.x * 0.013f) * cos(p.y * 0.011f)
            val n2 = sin((p.x + p.y) * 0.021f) * 0.5f
            return (n1 + n2) * PI.toFloat()
        }
    }
}
