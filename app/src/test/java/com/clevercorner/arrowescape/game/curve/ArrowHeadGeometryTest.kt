package com.clevercorner.arrowescape.game.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowHeadGeometryTest {

    private val sw = 10f

    @Test
    fun `triangle sits on the body end and points along the heading`() {
        val tri = ArrowHeadGeometry.triangle(Point2D(100f, 50f), Point2D(1f, 0f), sw)

        // Tip is out in front of the anchor, on the same line.
        assertTrue(tri.tip.x > 100f)
        assertEquals(50f, tri.tip.y, 0.001f)

        // Base corners are level with each other and straddle the body line.
        assertEquals(tri.backLeft.x, tri.backRight.x, 0.001f)
        assertEquals(50f, (tri.backLeft.y + tri.backRight.y) / 2f, 0.001f)

        // Base starts at (or just inside) the body end - no gap, no overshoot.
        assertTrue(tri.backLeft.x <= 100f)
        assertTrue(tri.backLeft.x >= 100f - sw)
    }

    @Test
    fun `head is wider than the body so it reads as a head`() {
        val tri = ArrowHeadGeometry.triangle(Point2D.ZERO, Point2D(0f, 1f), sw)
        assertTrue(tri.backLeft.distanceTo(tri.backRight) > sw * 2)
    }

    @Test
    fun `triangle rotates cleanly for any heading`() {
        val diag = Point2D(1f, 1f).normalize()
        val tri = ArrowHeadGeometry.triangle(Point2D.ZERO, diag, sw)
        val axis = (tri.tip - (tri.backLeft + tri.backRight) / 2f).normalize()
        assertEquals(diag.x, axis.x, 0.001f)
        assertEquals(diag.y, axis.y, 0.001f)
    }

    @Test
    fun `heading follows the real direction of travel on a bend`() {
        // Runs right, then turns sharply up. The head must point UP.
        val body = listOf(
            Point2D(0f, 100f), Point2D(10f, 100f), Point2D(20f, 100f),
            Point2D(30f, 100f), Point2D(30f, 90f), Point2D(30f, 80f),
            Point2D(30f, 70f)
        )
        val heading = ArrowHeadGeometry.headingOf(body, sw, fallback = Point2D(1f, 0f))
        assertEquals(0f, heading.x, 0.001f)
        assertEquals(-1f, heading.y, 0.001f)
    }

    @Test
    fun `heading ignores sub-pixel jitter in the last sample`() {
        val body = listOf(Point2D(0f, 0f), Point2D(10f, 0f), Point2D(20f, 0f), Point2D(30f, 0.4f))
        val heading = ArrowHeadGeometry.headingOf(body, sw, fallback = Point2D(0f, 1f))
        assertTrue("heading should stay essentially horizontal", heading.x > 0.99f)
    }

    @Test
    fun `degenerate body falls back to the supplied heading`() {
        val fallback = Point2D(0f, -1f)
        assertEquals(fallback, ArrowHeadGeometry.headingOf(emptyList(), sw, fallback))
        assertEquals(fallback, ArrowHeadGeometry.headingOf(listOf(Point2D(5f, 5f)), sw, fallback))
    }
}
