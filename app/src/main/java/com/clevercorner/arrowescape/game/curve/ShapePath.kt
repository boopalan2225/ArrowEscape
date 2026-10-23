package com.clevercorner.arrowescape.game.curve

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * SVG-like path commands for defining shape outlines.
 */
sealed class PathCommand {
    data class MoveTo(val point: Point2D) : PathCommand()
    data class LineTo(val point: Point2D) : PathCommand()
    data class CurveTo(val control1: Point2D, val control2: Point2D, val end: Point2D) : PathCommand()
    data class QuadTo(val control: Point2D, val end: Point2D) : PathCommand()
    data object Close : PathCommand()
}

/**
 * A shape defined by a path outline, used to generate curved arrows.
 */
data class ShapePath(
    val name: String,
    val commands: List<PathCommand>,
    val width: Float,
    val height: Float,
    val fillColor: Long = 0xFF4A90D9
) {
    /**
     * Convert to a list of bezier segments representing the outline.
     */
    fun toOutlineSegments(): List<BezierSegment> {
        val segments = mutableListOf<BezierSegment>()
        var currentPos = Point2D.ZERO
        var startPos = Point2D.ZERO
        
        for (cmd in commands) {
            when (cmd) {
                is PathCommand.MoveTo -> {
                    // Starting a new subpath implicitly closes the previous
                    // one; leaving it open produced broken contour rings.
                    if (currentPos.distanceTo(startPos) > 0.5f && segments.isNotEmpty()) {
                        segments.add(lineSegment(currentPos, startPos))
                    }
                    currentPos = cmd.point
                    startPos = cmd.point
                }
                is PathCommand.LineTo -> {
                    // Convert line to degenerate bezier
                    val p0 = currentPos
                    val p3 = cmd.point
                    val p1 = p0.lerp(p3, 0.33f)
                    val p2 = p0.lerp(p3, 0.67f)
                    segments.add(BezierSegment(p0, p1, p2, p3))
                    currentPos = cmd.point
                }
                is PathCommand.CurveTo -> {
                    segments.add(BezierSegment(currentPos, cmd.control1, cmd.control2, cmd.end))
                    currentPos = cmd.end
                }
                is PathCommand.QuadTo -> {
                    // Convert quadratic to cubic
                    val p0 = currentPos
                    val p3 = cmd.end
                    val p1 = p0.lerp(cmd.control, 2f/3f)
                    val p2 = p3.lerp(cmd.control, 2f/3f)
                    segments.add(BezierSegment(p0, p1, p2, p3))
                    currentPos = cmd.end
                }
                is PathCommand.Close -> {
                    if (currentPos.distanceTo(startPos) > 0.5f) {
                        segments.add(lineSegment(currentPos, startPos))
                    }
                    currentPos = startPos
                }
            }
        }
        
        // Close the final subpath so its contour ring is complete.
        if (currentPos.distanceTo(startPos) > 0.5f && segments.isNotEmpty()) {
            segments.add(lineSegment(currentPos, startPos))
        }

        return segments
    }
    
    /**
     * Scale the shape to fit within given bounds.
     */
    fun scaleTo(targetWidth: Float, targetHeight: Float): ShapePath {
        val scaleX = targetWidth / width
        val scaleY = targetHeight / height
        
        fun scalePoint(p: Point2D) = Point2D(p.x * scaleX, p.y * scaleY)
        
        val scaledCommands = commands.map { cmd ->
            when (cmd) {
                is PathCommand.MoveTo -> PathCommand.MoveTo(scalePoint(cmd.point))
                is PathCommand.LineTo -> PathCommand.LineTo(scalePoint(cmd.point))
                is PathCommand.CurveTo -> PathCommand.CurveTo(
                    scalePoint(cmd.control1),
                    scalePoint(cmd.control2),
                    scalePoint(cmd.end)
                )
                is PathCommand.QuadTo -> PathCommand.QuadTo(
                    scalePoint(cmd.control),
                    scalePoint(cmd.end)
                )
                is PathCommand.Close -> PathCommand.Close
            }
        }
        
        return copy(commands = scaledCommands, width = targetWidth, height = targetHeight)
    }
    
    /**
     * Offset (translate) the shape by the given amount.
     */
    fun offsetBy(dx: Float, dy: Float): ShapePath {
        fun offsetPoint(p: Point2D) = Point2D(p.x + dx, p.y + dy)
        
        val offsetCommands = commands.map { cmd ->
            when (cmd) {
                is PathCommand.MoveTo -> PathCommand.MoveTo(offsetPoint(cmd.point))
                is PathCommand.LineTo -> PathCommand.LineTo(offsetPoint(cmd.point))
                is PathCommand.CurveTo -> PathCommand.CurveTo(
                    offsetPoint(cmd.control1),
                    offsetPoint(cmd.control2),
                    offsetPoint(cmd.end)
                )
                is PathCommand.QuadTo -> PathCommand.QuadTo(
                    offsetPoint(cmd.control),
                    offsetPoint(cmd.end)
                )
                is PathCommand.Close -> PathCommand.Close
            }
        }
        
        return copy(commands = offsetCommands)
    }
    
    /**
     * Cached flattened outline, split into closed subpaths.
     *
     * Treating every point as one ring was wrong for multi-part shapes: it
     * connected the owl's left eye to its right eye, so containment and edge
     * distance were nonsense wherever a shape had more than one contour.
     */
    val contours: List<List<Point2D>> by lazy {
        val rings = mutableListOf<List<Point2D>>()
        var current = mutableListOf<Point2D>()

        for (segment in toOutlineSegments()) {
            // A gap between the previous end and this start means a new subpath.
            if (current.isNotEmpty() && current.last().distanceTo(segment.p0) > 0.5f) {
                if (current.size >= 3) rings.add(current)
                current = mutableListOf()
            }
            val steps = (segment.approximateLength() / 4f).toInt().coerceIn(6, 48)
            for (i in 0..steps) current.add(segment.pointAt(i.toFloat() / steps))
        }
        if (current.size >= 3) rings.add(current)
        rings
    }

    private val outlinePoints: List<Point2D> by lazy { contours.flatten() }

    /**
     * Check if a point is inside the shape, using even-odd across all
     * contours. Even-odd means an inner ring punches a hole, which is exactly
     * the behaviour interior detail regions need.
     */
    fun contains(point: Point2D): Boolean {
        var inside = false

        for (ring in contours) {
            var j = ring.size - 1
            for (i in ring.indices) {
                val pi = ring[i]
                val pj = ring[j]
                if ((pi.y > point.y) != (pj.y > point.y)) {
                    val x = pi.x + (point.y - pi.y) / (pj.y - pi.y) * (pj.x - pi.x)
                    if (x > point.x) inside = !inside
                }
                j = i
            }
        }

        return inside
    }

    /**
     * Actual bounding box of the outline. Shapes get translated onto the board,
     * so `width`/`height` alone say nothing about *where* the shape lives.
     */
    val bounds: Pair<Point2D, Point2D> by lazy {
        val pts = outlinePoints
        if (pts.isEmpty()) Point2D.ZERO to Point2D(width, height)
        else Point2D(pts.minOf { it.x }, pts.minOf { it.y }) to
            Point2D(pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    /** Centre of the outline's bounding box. */
    val center: Point2D get() = bounds.first.lerp(bounds.second, 0.5f)

    /**
     * Nearest outline point to [point], across every contour.
     */
    private fun nearestOutline(point: Point2D): Pair<List<Point2D>, Int>? {
        var bestRing: List<Point2D>? = null
        var bestIndex = 0
        var bestDistance = Float.MAX_VALUE

        for (ring in contours) {
            for (i in ring.indices) {
                val d = ring[i].distanceTo(point)
                if (d < bestDistance) {
                    bestDistance = d
                    bestRing = ring
                    bestIndex = i
                }
            }
        }

        return bestRing?.let { it to bestIndex }
    }

    /**
     * Approximate distance from [point] to the nearest outline point.
     */
    fun distanceToEdge(point: Point2D): Float =
        outlinePoints.minOfOrNull { it.distanceTo(point) } ?: Float.MAX_VALUE

    /**
     * Direction pointing from [point] back into the shape's interior.
     * Used to reflect streamlines off the boundary.
     */
    fun inwardAngleAt(point: Point2D): Float {
        val (ring, index) = nearestOutline(point) ?: return 0f
        val edge = ring[index]

        val next = ring[(index + 1) % ring.size]
        val prev = ring[(index - 1 + ring.size) % ring.size]
        val tangent = (next - prev).normalize()
        val normal = tangent.perpendicular()

        // Pick whichever normal direction actually lands inside.
        val probe = (edge - point).normalize().let {
            if (it == Point2D.ZERO) normal else it
        }
        val candidate = edge + normal * 4f
        val inward = if (contains(candidate)) normal else normal * -1f

        return kotlin.math.atan2(
            inward.y + probe.y * 0.2f,
            inward.x + probe.x * 0.2f
        )
    }

    /**
     * Heading of the outline where it runs closest to [point]. Drives
     * CONTOUR flow, which is what gives fur/feather shapes their look.
     */
    fun edgeTangentAt(point: Point2D): Float {
        val (ring, index) = nearestOutline(point) ?: return 0f
        val next = ring[(index + 1) % ring.size]
        val prev = ring[(index - 1 + ring.size) % ring.size]
        return prev.angleTo(next)
    }

    /**
     * Binary-search the boundary crossing on the segment [inside] -> [outside].
     */
    fun clipToEdge(inside: Point2D, outside: Point2D): Point2D? {
        if (!contains(inside)) return null
        var lo = inside
        var hi = outside
        repeat(12) {
            val mid = lo.lerp(hi, 0.5f)
            if (contains(mid)) lo = mid else hi = mid
        }
        return lo
    }
    
    companion object {
        /** A straight line expressed as a degenerate cubic. */
        private fun lineSegment(from: Point2D, to: Point2D) = BezierSegment(
            from, from.lerp(to, 0.33f), from.lerp(to, 0.67f), to
        )

        /**
         * Create a circle shape.
         */
        fun circle(centerX: Float, centerY: Float, radius: Float): ShapePath {
            val k = 0.5523f // Magic number for circular bezier approximation
            val commands = listOf(
                PathCommand.MoveTo(Point2D(centerX + radius, centerY)),
                PathCommand.CurveTo(
                    Point2D(centerX + radius, centerY + radius * k),
                    Point2D(centerX + radius * k, centerY + radius),
                    Point2D(centerX, centerY + radius)
                ),
                PathCommand.CurveTo(
                    Point2D(centerX - radius * k, centerY + radius),
                    Point2D(centerX - radius, centerY + radius * k),
                    Point2D(centerX - radius, centerY)
                ),
                PathCommand.CurveTo(
                    Point2D(centerX - radius, centerY - radius * k),
                    Point2D(centerX - radius * k, centerY - radius),
                    Point2D(centerX, centerY - radius)
                ),
                PathCommand.CurveTo(
                    Point2D(centerX + radius * k, centerY - radius),
                    Point2D(centerX + radius, centerY - radius * k),
                    Point2D(centerX + radius, centerY)
                ),
                PathCommand.Close
            )
            return ShapePath("Circle", commands, radius * 2, radius * 2)
        }
        
        /**
         * Create a heart shape.
         */
        fun heart(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                PathCommand.MoveTo(Point2D(w * 0.5f, h * 0.25f)),
                PathCommand.CurveTo(
                    Point2D(w * 0.5f, h * 0.1f),
                    Point2D(w * 0.25f, 0f),
                    Point2D(0f, h * 0.25f)
                ),
                PathCommand.CurveTo(
                    Point2D(0f, h * 0.5f),
                    Point2D(w * 0.25f, h * 0.75f),
                    Point2D(w * 0.5f, h)
                ),
                PathCommand.CurveTo(
                    Point2D(w * 0.75f, h * 0.75f),
                    Point2D(w, h * 0.5f),
                    Point2D(w, h * 0.25f)
                ),
                PathCommand.CurveTo(
                    Point2D(w * 0.75f, 0f),
                    Point2D(w * 0.5f, h * 0.1f),
                    Point2D(w * 0.5f, h * 0.25f)
                ),
                PathCommand.Close
            )
            return ShapePath("Heart", commands, width, height)
        }
        
        /**
         * Create a star shape.
         */
        fun star(centerX: Float, centerY: Float, outerRadius: Float, innerRadius: Float, points: Int = 5): ShapePath {
            val commands = mutableListOf<PathCommand>()
            val angleStep = PI.toFloat() / points
            
            for (i in 0 until points * 2) {
                val angle = -PI.toFloat() / 2 + i * angleStep
                val radius = if (i % 2 == 0) outerRadius else innerRadius
                val point = Point2D(
                    centerX + cos(angle) * radius,
                    centerY + sin(angle) * radius
                )
                
                if (i == 0) {
                    commands.add(PathCommand.MoveTo(point))
                } else {
                    commands.add(PathCommand.LineTo(point))
                }
            }
            commands.add(PathCommand.Close)
            
            return ShapePath("Star", commands, outerRadius * 2, outerRadius * 2)
        }
        
        /**
         * Create a cat silhouette (simplified).
         */
        fun cat(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                // Start at left ear tip
                PathCommand.MoveTo(Point2D(w * 0.15f, h * 0.05f)),
                // Left ear outer edge
                PathCommand.CurveTo(
                    Point2D(w * 0.05f, h * 0.1f),
                    Point2D(w * 0.05f, h * 0.2f),
                    Point2D(w * 0.1f, h * 0.25f)
                ),
                // Left side of head
                PathCommand.CurveTo(
                    Point2D(w * 0.05f, h * 0.35f),
                    Point2D(w * 0.08f, h * 0.45f),
                    Point2D(w * 0.15f, h * 0.5f)
                ),
                // Left body
                PathCommand.CurveTo(
                    Point2D(w * 0.1f, h * 0.6f),
                    Point2D(w * 0.08f, h * 0.75f),
                    Point2D(w * 0.15f, h * 0.85f)
                ),
                // Bottom left / tail start
                PathCommand.CurveTo(
                    Point2D(w * 0.2f, h * 0.95f),
                    Point2D(w * 0.35f, h * 0.98f),
                    Point2D(w * 0.5f, h * 0.95f)
                ),
                // Bottom right
                PathCommand.CurveTo(
                    Point2D(w * 0.65f, h * 0.98f),
                    Point2D(w * 0.8f, h * 0.95f),
                    Point2D(w * 0.85f, h * 0.85f)
                ),
                // Right body
                PathCommand.CurveTo(
                    Point2D(w * 0.92f, h * 0.75f),
                    Point2D(w * 0.9f, h * 0.6f),
                    Point2D(w * 0.85f, h * 0.5f)
                ),
                // Right side of head
                PathCommand.CurveTo(
                    Point2D(w * 0.92f, h * 0.45f),
                    Point2D(w * 0.95f, h * 0.35f),
                    Point2D(w * 0.9f, h * 0.25f)
                ),
                // Right ear
                PathCommand.CurveTo(
                    Point2D(w * 0.95f, h * 0.2f),
                    Point2D(w * 0.95f, h * 0.1f),
                    Point2D(w * 0.85f, h * 0.05f)
                ),
                // Top of head between ears
                PathCommand.CurveTo(
                    Point2D(w * 0.7f, h * 0.08f),
                    Point2D(w * 0.55f, h * 0.1f),
                    Point2D(w * 0.5f, h * 0.12f)
                ),
                PathCommand.CurveTo(
                    Point2D(w * 0.45f, h * 0.1f),
                    Point2D(w * 0.3f, h * 0.08f),
                    Point2D(w * 0.15f, h * 0.05f)
                ),
                PathCommand.Close
            )
            return ShapePath("Cat", commands, width, height)
        }
        
        /**
         * Create an owl silhouette.
         */
        fun owl(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                // Start at top of head
                PathCommand.MoveTo(Point2D(w * 0.5f, h * 0.02f)),
                // Left ear tuft
                PathCommand.CurveTo(
                    Point2D(w * 0.35f, h * 0.0f),
                    Point2D(w * 0.15f, h * 0.05f),
                    Point2D(w * 0.1f, h * 0.15f)
                ),
                // Left side of head
                PathCommand.CurveTo(
                    Point2D(w * 0.05f, h * 0.25f),
                    Point2D(w * 0.08f, h * 0.35f),
                    Point2D(w * 0.15f, h * 0.4f)
                ),
                // Left body (wider)
                PathCommand.CurveTo(
                    Point2D(w * 0.05f, h * 0.5f),
                    Point2D(w * 0.02f, h * 0.65f),
                    Point2D(w * 0.1f, h * 0.8f)
                ),
                // Bottom left / feet
                PathCommand.CurveTo(
                    Point2D(w * 0.15f, h * 0.9f),
                    Point2D(w * 0.25f, h * 0.98f),
                    Point2D(w * 0.35f, h * 0.95f)
                ),
                // Between feet
                PathCommand.CurveTo(
                    Point2D(w * 0.45f, h * 0.92f),
                    Point2D(w * 0.55f, h * 0.92f),
                    Point2D(w * 0.65f, h * 0.95f)
                ),
                // Bottom right
                PathCommand.CurveTo(
                    Point2D(w * 0.75f, h * 0.98f),
                    Point2D(w * 0.85f, h * 0.9f),
                    Point2D(w * 0.9f, h * 0.8f)
                ),
                // Right body
                PathCommand.CurveTo(
                    Point2D(w * 0.98f, h * 0.65f),
                    Point2D(w * 0.95f, h * 0.5f),
                    Point2D(w * 0.85f, h * 0.4f)
                ),
                // Right side of head
                PathCommand.CurveTo(
                    Point2D(w * 0.92f, h * 0.35f),
                    Point2D(w * 0.95f, h * 0.25f),
                    Point2D(w * 0.9f, h * 0.15f)
                ),
                // Right ear tuft
                PathCommand.CurveTo(
                    Point2D(w * 0.85f, h * 0.05f),
                    Point2D(w * 0.65f, h * 0.0f),
                    Point2D(w * 0.5f, h * 0.02f)
                ),
                PathCommand.Close
            )
            return ShapePath("Owl", commands, width, height)
        }
        
        /**
         * Create a simple fish shape.
         */
        fun fish(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                // Start at mouth
                PathCommand.MoveTo(Point2D(0f, h * 0.5f)),
                // Top of body
                PathCommand.CurveTo(
                    Point2D(w * 0.2f, h * 0.1f),
                    Point2D(w * 0.5f, h * 0.05f),
                    Point2D(w * 0.7f, h * 0.2f)
                ),
                // Top tail
                PathCommand.CurveTo(
                    Point2D(w * 0.85f, h * 0.1f),
                    Point2D(w * 0.95f, h * 0.0f),
                    Point2D(w, h * 0.15f)
                ),
                // Tail fork
                PathCommand.LineTo(Point2D(w * 0.85f, h * 0.5f)),
                // Bottom tail
                PathCommand.LineTo(Point2D(w, h * 0.85f)),
                PathCommand.CurveTo(
                    Point2D(w * 0.95f, h),
                    Point2D(w * 0.85f, h * 0.9f),
                    Point2D(w * 0.7f, h * 0.8f)
                ),
                // Bottom of body
                PathCommand.CurveTo(
                    Point2D(w * 0.5f, h * 0.95f),
                    Point2D(w * 0.2f, h * 0.9f),
                    Point2D(0f, h * 0.5f)
                ),
                PathCommand.Close
            )
            return ShapePath("Fish", commands, width, height)
        }
        
        /**
         * Create a butterfly silhouette.
         */
        fun butterfly(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                // Start at top center
                PathCommand.MoveTo(Point2D(w * 0.5f, h * 0.05f)),
                // Left antenna
                PathCommand.CurveTo(
                    Point2D(w * 0.4f, h * 0.0f),
                    Point2D(w * 0.35f, h * 0.05f),
                    Point2D(w * 0.4f, h * 0.12f)
                ),
                // Left top wing outer
                PathCommand.CurveTo(
                    Point2D(w * 0.15f, h * 0.05f),
                    Point2D(w * 0.0f, h * 0.2f),
                    Point2D(w * 0.05f, h * 0.4f)
                ),
                // Left top wing inner
                PathCommand.CurveTo(
                    Point2D(w * 0.15f, h * 0.45f),
                    Point2D(w * 0.35f, h * 0.4f),
                    Point2D(w * 0.45f, h * 0.45f)
                ),
                // Body segment
                PathCommand.LineTo(Point2D(w * 0.45f, h * 0.55f)),
                // Left bottom wing inner
                PathCommand.CurveTo(
                    Point2D(w * 0.35f, h * 0.6f),
                    Point2D(w * 0.15f, h * 0.55f),
                    Point2D(w * 0.05f, h * 0.6f)
                ),
                // Left bottom wing outer
                PathCommand.CurveTo(
                    Point2D(w * 0.0f, h * 0.8f),
                    Point2D(w * 0.15f, h * 0.95f),
                    Point2D(w * 0.4f, h * 0.88f)
                ),
                // Bottom body
                PathCommand.CurveTo(
                    Point2D(w * 0.45f, h * 0.95f),
                    Point2D(w * 0.55f, h * 0.95f),
                    Point2D(w * 0.6f, h * 0.88f)
                ),
                // Right bottom wing outer
                PathCommand.CurveTo(
                    Point2D(w * 0.85f, h * 0.95f),
                    Point2D(w * 1.0f, h * 0.8f),
                    Point2D(w * 0.95f, h * 0.6f)
                ),
                // Right bottom wing inner
                PathCommand.CurveTo(
                    Point2D(w * 0.85f, h * 0.55f),
                    Point2D(w * 0.65f, h * 0.6f),
                    Point2D(w * 0.55f, h * 0.55f)
                ),
                // Body segment
                PathCommand.LineTo(Point2D(w * 0.55f, h * 0.45f)),
                // Right top wing inner
                PathCommand.CurveTo(
                    Point2D(w * 0.65f, h * 0.4f),
                    Point2D(w * 0.85f, h * 0.45f),
                    Point2D(w * 0.95f, h * 0.4f)
                ),
                // Right top wing outer
                PathCommand.CurveTo(
                    Point2D(w * 1.0f, h * 0.2f),
                    Point2D(w * 0.85f, h * 0.05f),
                    Point2D(w * 0.6f, h * 0.12f)
                ),
                // Right antenna
                PathCommand.CurveTo(
                    Point2D(w * 0.65f, h * 0.05f),
                    Point2D(w * 0.6f, h * 0.0f),
                    Point2D(w * 0.5f, h * 0.05f)
                ),
                PathCommand.Close
            )
            return ShapePath("Butterfly", commands, width, height)
        }
        
        /**
         * Create a frog silhouette.
         */
        fun frog(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                // Start at left eye bulge
                PathCommand.MoveTo(Point2D(w * 0.2f, h * 0.15f)),
                // Left eye top
                PathCommand.CurveTo(
                    Point2D(w * 0.15f, h * 0.05f),
                    Point2D(w * 0.25f, h * 0.0f),
                    Point2D(w * 0.35f, h * 0.08f)
                ),
                // Between eyes
                PathCommand.CurveTo(
                    Point2D(w * 0.45f, h * 0.12f),
                    Point2D(w * 0.55f, h * 0.12f),
                    Point2D(w * 0.65f, h * 0.08f)
                ),
                // Right eye top
                PathCommand.CurveTo(
                    Point2D(w * 0.75f, h * 0.0f),
                    Point2D(w * 0.85f, h * 0.05f),
                    Point2D(w * 0.8f, h * 0.15f)
                ),
                // Right side of head
                PathCommand.CurveTo(
                    Point2D(w * 0.95f, h * 0.25f),
                    Point2D(w * 0.98f, h * 0.4f),
                    Point2D(w * 0.9f, h * 0.5f)
                ),
                // Right front leg
                PathCommand.CurveTo(
                    Point2D(w * 0.98f, h * 0.55f),
                    Point2D(w * 0.95f, h * 0.65f),
                    Point2D(w * 0.85f, h * 0.6f)
                ),
                // Right body
                PathCommand.CurveTo(
                    Point2D(w * 0.9f, h * 0.75f),
                    Point2D(w * 0.85f, h * 0.85f),
                    Point2D(w * 0.75f, h * 0.88f)
                ),
                // Right back leg
                PathCommand.CurveTo(
                    Point2D(w * 0.85f, h * 0.95f),
                    Point2D(w * 0.8f, h * 1.0f),
                    Point2D(w * 0.7f, h * 0.95f)
                ),
                // Bottom
                PathCommand.CurveTo(
                    Point2D(w * 0.6f, h * 0.92f),
                    Point2D(w * 0.4f, h * 0.92f),
                    Point2D(w * 0.3f, h * 0.95f)
                ),
                // Left back leg
                PathCommand.CurveTo(
                    Point2D(w * 0.2f, h * 1.0f),
                    Point2D(w * 0.15f, h * 0.95f),
                    Point2D(w * 0.25f, h * 0.88f)
                ),
                // Left body
                PathCommand.CurveTo(
                    Point2D(w * 0.15f, h * 0.85f),
                    Point2D(w * 0.1f, h * 0.75f),
                    Point2D(w * 0.15f, h * 0.6f)
                ),
                // Left front leg
                PathCommand.CurveTo(
                    Point2D(w * 0.05f, h * 0.65f),
                    Point2D(w * 0.02f, h * 0.55f),
                    Point2D(w * 0.1f, h * 0.5f)
                ),
                // Left side of head
                PathCommand.CurveTo(
                    Point2D(w * 0.02f, h * 0.4f),
                    Point2D(w * 0.05f, h * 0.25f),
                    Point2D(w * 0.2f, h * 0.15f)
                ),
                PathCommand.Close
            )
            return ShapePath("Frog", commands, width, height)
        }
        
        /**
         * Create a deer head silhouette.
         */
        fun deer(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                // Start at left antler tip
                PathCommand.MoveTo(Point2D(w * 0.05f, h * 0.0f)),
                // Left antler
                PathCommand.CurveTo(
                    Point2D(w * 0.1f, h * 0.08f),
                    Point2D(w * 0.15f, h * 0.12f),
                    Point2D(w * 0.2f, h * 0.15f)
                ),
                // Left antler branch
                PathCommand.CurveTo(
                    Point2D(w * 0.15f, h * 0.1f),
                    Point2D(w * 0.18f, h * 0.05f),
                    Point2D(w * 0.22f, h * 0.02f)
                ),
                PathCommand.CurveTo(
                    Point2D(w * 0.25f, h * 0.08f),
                    Point2D(w * 0.28f, h * 0.15f),
                    Point2D(w * 0.3f, h * 0.2f)
                ),
                // Left ear
                PathCommand.CurveTo(
                    Point2D(w * 0.2f, h * 0.25f),
                    Point2D(w * 0.15f, h * 0.28f),
                    Point2D(w * 0.2f, h * 0.35f)
                ),
                // Left side of head
                PathCommand.CurveTo(
                    Point2D(w * 0.15f, h * 0.45f),
                    Point2D(w * 0.18f, h * 0.55f),
                    Point2D(w * 0.25f, h * 0.65f)
                ),
                // Nose/muzzle left
                PathCommand.CurveTo(
                    Point2D(w * 0.3f, h * 0.8f),
                    Point2D(w * 0.35f, h * 0.9f),
                    Point2D(w * 0.45f, h * 0.95f)
                ),
                // Nose bottom
                PathCommand.CurveTo(
                    Point2D(w * 0.48f, h * 1.0f),
                    Point2D(w * 0.52f, h * 1.0f),
                    Point2D(w * 0.55f, h * 0.95f)
                ),
                // Nose/muzzle right
                PathCommand.CurveTo(
                    Point2D(w * 0.65f, h * 0.9f),
                    Point2D(w * 0.7f, h * 0.8f),
                    Point2D(w * 0.75f, h * 0.65f)
                ),
                // Right side of head
                PathCommand.CurveTo(
                    Point2D(w * 0.82f, h * 0.55f),
                    Point2D(w * 0.85f, h * 0.45f),
                    Point2D(w * 0.8f, h * 0.35f)
                ),
                // Right ear
                PathCommand.CurveTo(
                    Point2D(w * 0.85f, h * 0.28f),
                    Point2D(w * 0.8f, h * 0.25f),
                    Point2D(w * 0.7f, h * 0.2f)
                ),
                // Right antler base
                PathCommand.CurveTo(
                    Point2D(w * 0.72f, h * 0.15f),
                    Point2D(w * 0.75f, h * 0.08f),
                    Point2D(w * 0.78f, h * 0.02f)
                ),
                PathCommand.CurveTo(
                    Point2D(w * 0.82f, h * 0.05f),
                    Point2D(w * 0.85f, h * 0.1f),
                    Point2D(w * 0.8f, h * 0.15f)
                ),
                // Right antler
                PathCommand.CurveTo(
                    Point2D(w * 0.85f, h * 0.12f),
                    Point2D(w * 0.9f, h * 0.08f),
                    Point2D(w * 0.95f, h * 0.0f)
                ),
                // Connect back to start
                PathCommand.CurveTo(
                    Point2D(w * 0.7f, h * 0.05f),
                    Point2D(w * 0.3f, h * 0.05f),
                    Point2D(w * 0.05f, h * 0.0f)
                ),
                PathCommand.Close
            )
            return ShapePath("Deer", commands, width, height)
        }
        
        /**
         * Create a snail silhouette with spiral shell.
         */
        fun snail(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                // Start at front of body (right side)
                PathCommand.MoveTo(Point2D(w * 0.95f, h * 0.7f)),
                // Front body curve
                PathCommand.CurveTo(
                    Point2D(w * 0.98f, h * 0.75f),
                    Point2D(w * 0.98f, h * 0.85f),
                    Point2D(w * 0.9f, h * 0.9f)
                ),
                // Bottom of body
                PathCommand.CurveTo(
                    Point2D(w * 0.7f, h * 0.98f),
                    Point2D(w * 0.3f, h * 0.98f),
                    Point2D(w * 0.15f, h * 0.85f)
                ),
                // Tail
                PathCommand.CurveTo(
                    Point2D(w * 0.05f, h * 0.8f),
                    Point2D(w * 0.02f, h * 0.7f),
                    Point2D(w * 0.08f, h * 0.65f)
                ),
                // Back of shell
                PathCommand.CurveTo(
                    Point2D(w * 0.0f, h * 0.5f),
                    Point2D(w * 0.05f, h * 0.2f),
                    Point2D(w * 0.25f, h * 0.1f)
                ),
                // Top of shell
                PathCommand.CurveTo(
                    Point2D(w * 0.4f, h * 0.02f),
                    Point2D(w * 0.6f, h * 0.02f),
                    Point2D(w * 0.75f, h * 0.15f)
                ),
                // Front of shell
                PathCommand.CurveTo(
                    Point2D(w * 0.88f, h * 0.28f),
                    Point2D(w * 0.92f, h * 0.45f),
                    Point2D(w * 0.85f, h * 0.58f)
                ),
                // Neck area
                PathCommand.CurveTo(
                    Point2D(w * 0.8f, h * 0.62f),
                    Point2D(w * 0.82f, h * 0.65f),
                    Point2D(w * 0.88f, h * 0.62f)
                ),
                // Right antenna
                PathCommand.CurveTo(
                    Point2D(w * 0.92f, h * 0.55f),
                    Point2D(w * 0.95f, h * 0.5f),
                    Point2D(w * 0.98f, h * 0.52f)
                ),
                PathCommand.CurveTo(
                    Point2D(w * 0.96f, h * 0.58f),
                    Point2D(w * 0.93f, h * 0.62f),
                    Point2D(w * 0.92f, h * 0.65f)
                ),
                // Head
                PathCommand.CurveTo(
                    Point2D(w * 0.94f, h * 0.68f),
                    Point2D(w * 0.95f, h * 0.7f),
                    Point2D(w * 0.95f, h * 0.7f)
                ),
                PathCommand.Close
            )
            return ShapePath("Snail", commands, width, height)
        }
        
        /**
         * Create a crescent moon shape.
         */
        fun moon(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                PathCommand.MoveTo(Point2D(w * 0.8f, h * 0.1f)),
                // Outer curve (full moon edge)
                PathCommand.CurveTo(
                    Point2D(w * 0.95f, h * 0.25f),
                    Point2D(w * 1.0f, h * 0.45f),
                    Point2D(w * 0.95f, h * 0.7f)
                ),
                PathCommand.CurveTo(
                    Point2D(w * 0.85f, h * 0.9f),
                    Point2D(w * 0.6f, h * 1.0f),
                    Point2D(w * 0.35f, h * 0.95f)
                ),
                // Inner curve (shadow edge)
                PathCommand.CurveTo(
                    Point2D(w * 0.5f, h * 0.8f),
                    Point2D(w * 0.55f, h * 0.55f),
                    Point2D(w * 0.5f, h * 0.35f)
                ),
                PathCommand.CurveTo(
                    Point2D(w * 0.45f, h * 0.15f),
                    Point2D(w * 0.55f, h * 0.05f),
                    Point2D(w * 0.8f, h * 0.1f)
                ),
                PathCommand.Close
            )
            return ShapePath("Moon", commands, width, height)
        }
        
        /**
         * Create a simple leaf shape.
         */
        fun leaf(width: Float, height: Float): ShapePath {
            val w = width
            val h = height
            val commands = listOf(
                // Start at stem
                PathCommand.MoveTo(Point2D(w * 0.5f, h)),
                // Left edge
                PathCommand.CurveTo(
                    Point2D(w * 0.1f, h * 0.8f),
                    Point2D(w * 0.0f, h * 0.4f),
                    Point2D(w * 0.3f, h * 0.1f)
                ),
                // Tip
                PathCommand.CurveTo(
                    Point2D(w * 0.45f, h * 0.0f),
                    Point2D(w * 0.55f, h * 0.0f),
                    Point2D(w * 0.7f, h * 0.1f)
                ),
                // Right edge
                PathCommand.CurveTo(
                    Point2D(w * 1.0f, h * 0.4f),
                    Point2D(w * 0.9f, h * 0.8f),
                    Point2D(w * 0.5f, h)
                ),
                PathCommand.Close
            )
            return ShapePath("Leaf", commands, width, height)
        }
    }
}
