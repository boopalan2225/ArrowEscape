package com.arrowescape.game.curve

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 2D point with float coordinates for smooth curves.
 */
data class Point2D(val x: Float, val y: Float) {
    operator fun plus(other: Point2D) = Point2D(x + other.x, y + other.y)
    operator fun minus(other: Point2D) = Point2D(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Point2D(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Point2D(x / scalar, y / scalar)
    
    fun distanceTo(other: Point2D): Float = hypot(x - other.x, y - other.y)
    
    fun angleTo(other: Point2D): Float = atan2(other.y - y, other.x - x)
    
    fun lerp(other: Point2D, t: Float): Point2D = 
        Point2D(x + (other.x - x) * t, y + (other.y - y) * t)
    
    fun rotate(angle: Float, pivot: Point2D = Point2D(0f, 0f)): Point2D {
        val dx = x - pivot.x
        val dy = y - pivot.y
        val cosA = cos(angle)
        val sinA = sin(angle)
        return Point2D(
            pivot.x + dx * cosA - dy * sinA,
            pivot.y + dx * sinA + dy * cosA
        )
    }
    
    fun normalize(): Point2D {
        val len = hypot(x, y)
        return if (len > 0.0001f) Point2D(x / len, y / len) else Point2D(0f, 0f)
    }
    
    fun perpendicular(): Point2D = Point2D(-y, x)
    
    companion object {
        val ZERO = Point2D(0f, 0f)
        
        fun fromPolar(angle: Float, radius: Float): Point2D =
            Point2D(cos(angle) * radius, sin(angle) * radius)
    }
}

/**
 * A cubic bezier curve segment defined by 4 control points.
 * P0 = start, P1 = control1, P2 = control2, P3 = end
 */
data class BezierSegment(
    val p0: Point2D,
    val p1: Point2D,
    val p2: Point2D,
    val p3: Point2D
) {
    /**
     * Evaluate the bezier curve at parameter t (0..1).
     */
    fun pointAt(t: Float): Point2D {
        val mt = 1f - t
        val mt2 = mt * mt
        val mt3 = mt2 * mt
        val t2 = t * t
        val t3 = t2 * t
        
        return Point2D(
            mt3 * p0.x + 3f * mt2 * t * p1.x + 3f * mt * t2 * p2.x + t3 * p3.x,
            mt3 * p0.y + 3f * mt2 * t * p1.y + 3f * mt * t2 * p2.y + t3 * p3.y
        )
    }
    
    /**
     * Get the tangent (direction) at parameter t.
     */
    fun tangentAt(t: Float): Point2D {
        val mt = 1f - t
        val mt2 = mt * mt
        val t2 = t * t
        
        val dx = 3f * mt2 * (p1.x - p0.x) + 6f * mt * t * (p2.x - p1.x) + 3f * t2 * (p3.x - p2.x)
        val dy = 3f * mt2 * (p1.y - p0.y) + 6f * mt * t * (p2.y - p1.y) + 3f * t2 * (p3.y - p2.y)
        
        return Point2D(dx, dy).normalize()
    }
    
    /**
     * Approximate arc length of this segment.
     */
    fun approximateLength(steps: Int = 20): Float {
        var length = 0f
        var prev = p0
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val curr = pointAt(t)
            length += prev.distanceTo(curr)
            prev = curr
        }
        return length
    }
    
    /**
     * Split this segment at parameter t into two segments.
     */
    fun splitAt(t: Float): Pair<BezierSegment, BezierSegment> {
        val p01 = p0.lerp(p1, t)
        val p12 = p1.lerp(p2, t)
        val p23 = p2.lerp(p3, t)
        val p012 = p01.lerp(p12, t)
        val p123 = p12.lerp(p23, t)
        val p0123 = p012.lerp(p123, t)
        
        return Pair(
            BezierSegment(p0, p01, p012, p0123),
            BezierSegment(p0123, p123, p23, p3)
        )
    }
    
    companion object {
        /**
         * Create a smooth curve through points using Catmull-Rom spline.
         */
        fun fromCatmullRom(
            p0: Point2D, p1: Point2D, p2: Point2D, p3: Point2D,
            tension: Float = 0.5f
        ): BezierSegment {
            val d1 = (p2 - p0) * tension / 3f
            val d2 = (p3 - p1) * tension / 3f
            return BezierSegment(p1, p1 + d1, p2 - d2, p2)
        }
        
        /**
         * Create a simple curved segment between two points with a bend.
         */
        fun curved(start: Point2D, end: Point2D, bendStrength: Float = 0.3f): BezierSegment {
            val mid = start.lerp(end, 0.5f)
            val perpLen = start.distanceTo(end) * bendStrength
            val angle = start.angleTo(end) + (Math.PI / 2).toFloat()
            val offset = Point2D.fromPolar(angle, perpLen)
            
            return BezierSegment(
                start,
                start.lerp(mid + offset, 0.5f),
                end.lerp(mid + offset, 0.5f),
                end
            )
        }
    }
}

/**
 * A curved arrow consisting of multiple bezier segments.
 */
data class CurvedArrow(
    val id: Int,
    val segments: List<BezierSegment>,
    val color: Long = 0xFF4A90D9, // Default blue
    val strokeWidth: Float = 8f,
    val isActive: Boolean = true
) {
    /**
     * Total approximate length of the arrow.
     */
    val length: Float by lazy {
        segments.sumOf { it.approximateLength().toDouble() }.toFloat()
    }
    
    /**
     * The start point (tail) of the arrow.
     */
    val startPoint: Point2D get() = segments.first().p0
    
    /**
     * The end point (head/tip) of the arrow.
     */
    val endPoint: Point2D get() = segments.last().p3
    
    /**
     * Direction the arrow is pointing and will escape along.
     *
     * Deliberately the same heading the arrowhead is *drawn* with, not the
     * raw bezier tangent at the last point: on a curled tip the two differ by
     * tens of degrees, which made the head point one way while the arrow
     * escaped another. One definition, so the picture is the rule.
     */
    val direction: Point2D by lazy {
        if (segments.isEmpty()) {
            Point2D.ZERO
        } else {
            ArrowHeadGeometry.headingOf(polyline, strokeWidth, segments.last().tangentAt(1f))
        }
    }
    
    /**
     * Get a point along the entire arrow path at parameter t (0..1).
     */
    fun pointAt(t: Float): Point2D {
        if (segments.isEmpty()) return Point2D.ZERO
        if (t <= 0f) return startPoint
        if (t >= 1f) return endPoint
        
        val totalLen = length
        val targetLen = totalLen * t
        var accumulated = 0f
        
        for (segment in segments) {
            val segLen = segment.approximateLength()
            if (accumulated + segLen >= targetLen) {
                val localT = (targetLen - accumulated) / segLen
                return segment.pointAt(localT)
            }
            accumulated += segLen
        }
        
        return endPoint
    }
    
    /**
     * Get tangent along the arrow path at parameter t.
     */
    fun tangentAt(t: Float): Point2D {
        if (segments.isEmpty()) return Point2D(1f, 0f)
        if (t <= 0f) return segments.first().tangentAt(0f)
        if (t >= 1f) return segments.last().tangentAt(1f)
        
        val totalLen = length
        val targetLen = totalLen * t
        var accumulated = 0f
        
        for (segment in segments) {
            val segLen = segment.approximateLength()
            if (accumulated + segLen >= targetLen) {
                val localT = (targetLen - accumulated) / segLen
                return segment.tangentAt(localT)
            }
            accumulated += segLen
        }
        
        return direction
    }
    
    /**
     * Flattened polyline approximation of the whole arrow, cached.
     * Single source of truth for hit-testing and collision - re-evaluating
     * beziers on every query was the old performance sinkhole.
     */
    val polyline: List<Point2D> by lazy { samplePolyline(COLLISION_SAMPLE_STEP) }

    /**
     * Sample the arrow into roughly evenly spaced points, about [step] apart.
     */
    fun samplePolyline(step: Float): List<Point2D> {
        if (segments.isEmpty()) return emptyList()
        val count = (length / step).toInt().coerceIn(2, 160)
        return (0..count).map { pointAt(it.toFloat() / count) }
    }

    /**
     * Check if a point is close to this arrow path.
     */
    fun isNear(point: Point2D, threshold: Float): Boolean =
        polyline.any { it.distanceTo(point) <= threshold }

    /**
     * Point at [distance] measured along the path from the tail.
     *
     * Distances past the tip continue straight along [exitDirection], and
     * distances before the tail extend backwards from the start. This lets the
     * escape animation slide the whole body along its own curve and then off
     * the board without any special-casing at the ends.
     */
    fun pointAtDistance(distance: Float, exitDirection: Point2D): Point2D {
        if (segments.isEmpty()) return Point2D.ZERO

        val total = length
        return when {
            distance <= 0f -> {
                val back = segments.first().tangentAt(0f)
                startPoint + back * distance
            }
            distance >= total -> endPoint + exitDirection * (distance - total)
            else -> pointAt(distance / total)
        }
    }

    /**
     * Flip the arrowhead to the opposite end, keeping identical geometry.
     */
    fun reversed(): CurvedArrow = copy(
        segments = segments.reversed().map { BezierSegment(it.p3, it.p2, it.p1, it.p0) }
    )
    
    /**
     * Get the bounding box of this arrow.
     */
    fun getBounds(): Pair<Point2D, Point2D> {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        
        for (p in polyline) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        
        return Pair(Point2D(minX, minY), Point2D(maxX, maxY))
    }
    
    /**
     * Create a copy with updated active state.
     */
    fun withActive(active: Boolean) = copy(isActive = active)
    
    companion object {
        /** Spacing used when flattening a curve for collision queries. */
        const val COLLISION_SAMPLE_STEP = 6f

        /**
         * Create a simple curved arrow between two points.
         */
        fun simple(
            id: Int,
            start: Point2D,
            end: Point2D,
            bendStrength: Float = 0.3f,
            color: Long = 0xFF4A90D9
        ): CurvedArrow {
            return CurvedArrow(
                id = id,
                segments = listOf(BezierSegment.curved(start, end, bendStrength)),
                color = color
            )
        }
        
        /**
         * Create a multi-segment curved arrow through waypoints.
         */
        fun throughPoints(
            id: Int,
            points: List<Point2D>,
            color: Long = 0xFF4A90D9,
            tension: Float = 0.5f
        ): CurvedArrow {
            if (points.size < 2) {
                return CurvedArrow(id, emptyList(), color)
            }
            if (points.size == 2) {
                return simple(id, points[0], points[1], 0.2f, color)
            }
            
            val segments = mutableListOf<BezierSegment>()
            
            // Extend endpoints for Catmull-Rom
            val extended = listOf(
                points[0] * 2f - points[1]
            ) + points + listOf(
                points.last() * 2f - points[points.size - 2]
            )
            
            for (i in 1 until extended.size - 2) {
                segments.add(
                    BezierSegment.fromCatmullRom(
                        extended[i - 1],
                        extended[i],
                        extended[i + 1],
                        extended[i + 2],
                        tension
                    )
                )
            }
            
            return CurvedArrow(id, segments, color)
        }
    }
}
