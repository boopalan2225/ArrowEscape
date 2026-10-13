package com.example.arrowescape.game.curve

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
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
        val flowType: FlowType = FlowType.ORGANIC,
        val colorPalette: List<Long> = listOf(0xFF4A90D9)
    )

    enum class FlowType {
        ORGANIC,      // Natural swirling patterns
        RADIAL,       // From center outward
        HORIZONTAL,   // Left to right waves
        VERTICAL,     // Top to bottom waves
        SPIRAL,       // Spiral from center
        CONTOUR       // Follows the shape outline, like feather/fur flow
    }

    /** Turning rate cap, in radians per step. Keeps streamlines smooth. */
    private const val MAX_TURN_PER_STEP = 0.22f

    /**
     * Generate curved arrows that fill the given shape.
     */
    fun generateArrows(
        shape: ShapePath,
        config: GenerationConfig = GenerationConfig(),
        random: Random = Random.Default
    ): List<CurvedArrow> {
        if (shape.toOutlineSegments().isEmpty()) return emptyList()

        val field = FlowField(shape, config)

        // Packing radii are driven by stroke width, not seed spacing. Tying
        // them to spacing made every arrow reserve a corridor wider than the
        // ink it drew, which is why shapes came out nearly empty.
        val seedClearance = config.arrowSpacing * 0.85f
        val occupyRadius = config.strokeWidth * 0.9f
        val collideRadius = config.strokeWidth * 0.7f

        val (boundsMin, boundsMax) = shape.bounds
        val grid = OccupancyGrid(
            originX = boundsMin.x - occupyRadius * 2,
            originY = boundsMin.y - occupyRadius * 2,
            width = (boundsMax.x - boundsMin.x) + occupyRadius * 4,
            height = (boundsMax.y - boundsMin.y) + occupyRadius * 4,
            cellSize = (config.strokeWidth * 0.5f).coerceAtLeast(2f)
        )
        val arrows = mutableListOf<CurvedArrow>()

        var arrowId = 0
        for (seed in generateSeedPoints(shape, config, random)) {
            if (grid.isOccupied(seed, seedClearance)) continue

            val waypoints = traceFlowLine(seed, shape, field, config)
            if (waypoints.size < 2) continue
            if (polylineLength(waypoints) < config.arrowMinLength) continue

            val trimmed = trimToLength(waypoints, config.arrowMaxLength)
            val candidate = CurvedArrow.throughPoints(
                id = arrowId,
                points = trimmed,
                color = config.colorPalette[arrowId % config.colorPalette.size],
                tension = 0.5f
            ).copy(strokeWidth = config.strokeWidth)

            // Reject if the smoothed curve drifted into a neighbour.
            if (grid.pathCollides(candidate.polyline, collideRadius)) continue

            grid.occupyPath(candidate.polyline, occupyRadius)
            arrows.add(candidate)
            arrowId++
        }

        return arrows
    }

    /**
     * Seed points on a jittered hex lattice. Hex packing beats a square grid
     * because it leaves no axis-aligned corridors, which is exactly what made
     * the old output look like stacked zig-zags.
     */
    private fun generateSeedPoints(
        shape: ShapePath,
        config: GenerationConfig,
        random: Random
    ): List<Point2D> {
        val spacing = config.arrowSpacing
        val rowHeight = spacing * 0.866f // sqrt(3)/2
        val (boundsMin, boundsMax) = shape.bounds
        val points = mutableListOf<Point2D>()

        var row = 0
        var y = boundsMin.y
        while (y <= boundsMax.y) {
            val xOffset = if (row % 2 == 0) 0f else spacing * 0.5f
            var x = boundsMin.x + xOffset
            while (x <= boundsMax.x) {
                val jitterX = (random.nextFloat() - 0.5f) * spacing * 0.35f
                val jitterY = (random.nextFloat() - 0.5f) * rowHeight * 0.35f
                val p = Point2D(x + jitterX, y + jitterY)
                if (shape.contains(p)) points.add(p)
                x += spacing
            }
            y += rowHeight
            row++
        }

        // Seed from the outside in so the silhouette edge reads crisply.
        return points.sortedBy { shape.distanceToEdge(it) }
    }

    /**
     * Trace a flow line with angular inertia, so the path curves instead of
     * jittering. Bendiness scales how far the heading may drift per step.
     */
    private fun traceFlowLine(
        start: Point2D,
        shape: ShapePath,
        field: FlowField,
        config: GenerationConfig
    ): List<Point2D> {
        val stepSize = config.arrowSpacing * 0.6f
        val maxSteps = (config.arrowMaxLength / stepSize).toInt() + 4
        val maxTurn = MAX_TURN_PER_STEP * (0.35f + config.bendiness)

        val points = mutableListOf(start)
        var current = start
        var heading = field.angleAt(start)

        for (step in 0 until maxSteps) {
            val desired = field.angleAt(current)
            heading += normalizeAngle(desired - heading).coerceIn(-maxTurn, maxTurn)

            val next = current + Point2D(cos(heading), sin(heading)) * stepSize
            if (!shape.contains(next)) {
                shape.clipToEdge(current, next)?.let { edge ->
                    if (edge.distanceTo(points.last()) > stepSize * 0.4f) points.add(edge)
                }
                break
            }
            points.add(next)
            current = next
        }

        return points
    }

    private fun polylineLength(points: List<Point2D>): Float =
        (1 until points.size).fold(0f) { acc, i -> acc + points[i - 1].distanceTo(points[i]) }

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
                FlowType.CONTOUR -> shape.edgeTangentAt(p)
            }
            return base + config.swirl * organicAngle(p) * 0.5f
        }

        /** Two octaves of smooth trig noise. Cheap, stable, no gradient jumps. */
        private fun organicAngle(p: Point2D): Float {
            val n1 = sin(p.x * 0.013f) * cos(p.y * 0.011f)
            val n2 = sin((p.x + p.y) * 0.021f) * 0.5f
            return (n1 + n2) * PI.toFloat()
        }
    }
}
