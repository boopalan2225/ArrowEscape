package com.example.arrowescape.game.curve

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates curved arrows that fill a shape with organic, flowing patterns.
 * 
 * The algorithm:
 * 1. Sample the shape outline to get contour points
 * 2. Generate flow field (direction at each point)
 * 3. Trace streamlines through the flow field
 * 4. Convert streamlines to curved arrows
 * 5. Ensure arrows don't overlap and can escape
 */
object CurveGenerator {

    data class GenerationConfig(
        val arrowMinLength: Float = 30f,
        val arrowMaxLength: Float = 150f,
        val arrowSpacing: Float = 12f,
        val strokeWidth: Float = 6f,
        val bendiness: Float = 0.4f,  // 0 = straight, 1 = very curvy
        val flowType: FlowType = FlowType.ORGANIC,
        val colorPalette: List<Long> = listOf(0xFF4A90D9)
    )

    enum class FlowType {
        ORGANIC,      // Natural swirling patterns
        RADIAL,       // From center outward
        HORIZONTAL,   // Left to right waves
        VERTICAL,     // Top to bottom waves
        SPIRAL        // Spiral from center
    }

    /**
     * Generate curved arrows that fill the given shape.
     */
    fun generateArrows(
        shape: ShapePath,
        config: GenerationConfig = GenerationConfig(),
        random: Random = Random.Default
    ): List<CurvedArrow> {
        val arrows = mutableListOf<CurvedArrow>()
        val occupiedRegions = mutableListOf<Pair<Point2D, Point2D>>() // Bounding boxes
        
        // Get shape bounds
        val outline = shape.toOutlineSegments()
        if (outline.isEmpty()) return emptyList()
        
        // Generate seed points inside the shape
        val seedPoints = generateSeedPoints(shape, config.arrowSpacing, random)
        
        var arrowId = 0
        
        for (seed in seedPoints.shuffled(random)) {
            // Skip if too close to existing arrows
            if (isNearOccupied(seed, occupiedRegions, config.arrowSpacing * 0.5f)) {
                continue
            }
            
            // Generate a curved path from this seed
            val waypoints = traceFlowLine(
                seed, shape, config, random
            )
            
            if (waypoints.size < 2) continue
            
            // Calculate path length
            var pathLength = 0f
            for (i in 1 until waypoints.size) {
                pathLength += waypoints[i - 1].distanceTo(waypoints[i])
            }
            
            if (pathLength < config.arrowMinLength) continue
            
            // Trim to max length if needed
            val trimmedWaypoints = trimToLength(waypoints, config.arrowMaxLength)
            
            // Pick a color from palette
            val color = config.colorPalette[arrowId % config.colorPalette.size]
            
            // Create the curved arrow
            val arrow = CurvedArrow.throughPoints(
                id = arrowId++,
                points = trimmedWaypoints,
                color = color,
                tension = 0.5f + random.nextFloat() * 0.3f
            )
            
            arrows.add(arrow)
            
            // Mark region as occupied
            val bounds = arrow.getBounds()
            occupiedRegions.add(bounds)
        }
        
        return arrows
    }

    /**
     * Generate seed points distributed inside the shape.
     */
    private fun generateSeedPoints(
        shape: ShapePath,
        spacing: Float,
        random: Random
    ): List<Point2D> {
        val points = mutableListOf<Point2D>()
        
        // Grid-based seeding with jitter
        val cols = (shape.width / spacing).toInt() + 1
        val rows = (shape.height / spacing).toInt() + 1
        
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = col * spacing + random.nextFloat() * spacing * 0.5f
                val y = row * spacing + random.nextFloat() * spacing * 0.5f
                val point = Point2D(x, y)
                
                if (shape.contains(point)) {
                    points.add(point)
                }
            }
        }
        
        return points
    }

    /**
     * Trace a flow line starting from the given point.
     */
    private fun traceFlowLine(
        start: Point2D,
        shape: ShapePath,
        config: GenerationConfig,
        random: Random
    ): List<Point2D> {
        val points = mutableListOf(start)
        var current = start
        val stepSize = config.arrowSpacing * 0.5f
        val maxSteps = (config.arrowMaxLength / stepSize).toInt() + 10
        
        // Trace forward
        for (step in 0 until maxSteps) {
            val direction = getFlowDirection(current, shape, config, random)
            val next = current + direction * stepSize
            
            // Stop if we leave the shape
            if (!shape.contains(next)) {
                // Try to find edge point
                val edgePoint = findEdgePoint(current, direction, shape, stepSize)
                if (edgePoint != null && edgePoint.distanceTo(points.last()) > stepSize * 0.5f) {
                    points.add(edgePoint)
                }
                break
            }
            
            // Add slight curve variation
            val bendOffset = direction.perpendicular() * (random.nextFloat() - 0.5f) * config.bendiness * stepSize
            val bentPoint = next + bendOffset
            
            if (shape.contains(bentPoint)) {
                points.add(bentPoint)
                current = bentPoint
            } else {
                points.add(next)
                current = next
            }
        }
        
        return points
    }

    /**
     * Get flow direction at a point based on flow type.
     */
    private fun getFlowDirection(
        point: Point2D,
        shape: ShapePath,
        config: GenerationConfig,
        random: Random
    ): Point2D {
        val cx = shape.width / 2
        val cy = shape.height / 2
        
        return when (config.flowType) {
            FlowType.ORGANIC -> {
                // Perlin-like noise for organic flow
                val noiseAngle = (sin(point.x * 0.05f) * cos(point.y * 0.05f) + 
                                  sin(point.x * 0.03f + point.y * 0.02f)) * PI.toFloat()
                Point2D(cos(noiseAngle), sin(noiseAngle))
            }
            FlowType.RADIAL -> {
                // Outward from center
                val dx = point.x - cx
                val dy = point.y - cy
                Point2D(dx, dy).normalize()
            }
            FlowType.HORIZONTAL -> {
                // Left to right with waves
                val waveOffset = sin(point.y * 0.1f) * 0.3f
                Point2D(1f, waveOffset).normalize()
            }
            FlowType.VERTICAL -> {
                // Top to bottom with waves
                val waveOffset = sin(point.x * 0.1f) * 0.3f
                Point2D(waveOffset, 1f).normalize()
            }
            FlowType.SPIRAL -> {
                // Spiral from center
                val dx = point.x - cx
                val dy = point.y - cy
                val angle = atan2(dy, dx) + PI.toFloat() / 4
                Point2D(cos(angle), sin(angle))
            }
        }
    }

    /**
     * Find the point where a ray exits the shape.
     */
    private fun findEdgePoint(
        start: Point2D,
        direction: Point2D,
        shape: ShapePath,
        maxDist: Float
    ): Point2D? {
        val steps = 10
        val stepSize = maxDist / steps
        
        var lastInside = start
        for (i in 1..steps) {
            val test = start + direction * (stepSize * i)
            if (!shape.contains(test)) {
                // Binary search for exact edge
                var lo = 0f
                var hi = stepSize
                for (j in 0 until 5) {
                    val mid = (lo + hi) / 2
                    if (shape.contains(lastInside + direction * mid)) {
                        lo = mid
                    } else {
                        hi = mid
                    }
                }
                return lastInside + direction * lo
            }
            lastInside = test
        }
        
        return null
    }

    /**
     * Trim waypoints to maximum length.
     */
    private fun trimToLength(points: List<Point2D>, maxLength: Float): List<Point2D> {
        if (points.size < 2) return points
        
        val result = mutableListOf(points[0])
        var accumulatedLength = 0f
        
        for (i in 1 until points.size) {
            val segmentLength = points[i - 1].distanceTo(points[i])
            if (accumulatedLength + segmentLength > maxLength) {
                // Interpolate final point
                val remaining = maxLength - accumulatedLength
                val t = remaining / segmentLength
                result.add(points[i - 1].lerp(points[i], t))
                break
            }
            result.add(points[i])
            accumulatedLength += segmentLength
        }
        
        return result
    }

    /**
     * Check if a point is near any occupied region.
     */
    private fun isNearOccupied(
        point: Point2D,
        regions: List<Pair<Point2D, Point2D>>,
        threshold: Float
    ): Boolean {
        for ((min, max) in regions) {
            val expandedMin = Point2D(min.x - threshold, min.y - threshold)
            val expandedMax = Point2D(max.x + threshold, max.y + threshold)
            
            if (point.x >= expandedMin.x && point.x <= expandedMax.x &&
                point.y >= expandedMin.y && point.y <= expandedMax.y) {
                return true
            }
        }
        return false
    }

    /**
     * Verify that all arrows can escape (have a clear path to exit).
     */
    fun validateEscapePaths(
        arrows: List<CurvedArrow>,
        shape: ShapePath
    ): List<CurvedArrow> {
        // For now, all arrows pointing outward can escape
        // More sophisticated validation can be added later
        return arrows.filter { arrow ->
            val tip = arrow.endPoint
            val direction = arrow.direction
            
            // Check if there's a clear path from tip to edge
            var testPoint = tip
            for (i in 0 until 50) {
                testPoint = testPoint + direction * 10f
                if (!shape.contains(testPoint)) {
                    return@filter true // Can escape
                }
            }
            false // Blocked
        }
    }
}
