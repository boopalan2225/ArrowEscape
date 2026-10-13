package com.example.arrowescape.ui.components

import com.example.arrowescape.game.curve.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardViewportTest {

    private val view = Point2D(400f, 600f)

    private fun assertPoint(expected: Point2D, actual: Point2D, eps: Float = 0.01f) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }

    @Test
    fun `screen and canvas mapping round-trip`() {
        val vp = BoardViewport(zoom = 2.5f, pan = Point2D(-120f, -80f))
        val canvas = Point2D(137f, 211f)
        assertPoint(canvas, vp.toCanvas(vp.toScreen(canvas)))
    }

    @Test
    fun `pinch keeps the point under the fingers fixed`() {
        val focus = Point2D(300f, 200f)
        val before = BoardViewport.RESET
        val canvasUnderFingers = before.toCanvas(focus)

        val after = before.transformedBy(focus, Point2D.ZERO, 2f, view)

        assertEquals(2f, after.zoom, 0.001f)
        assertPoint(focus, after.toScreen(canvasUnderFingers))
    }

    @Test
    fun `zoom is clamped to the allowed range`() {
        val tooFar = BoardViewport.RESET.transformedBy(Point2D(10f, 10f), Point2D.ZERO, 100f, view)
        val tooSmall = BoardViewport.RESET.transformedBy(Point2D(10f, 10f), Point2D.ZERO, 0.01f, view)
        assertEquals(BoardViewport.MAX_ZOOM, tooFar.zoom, 0.001f)
        assertEquals(BoardViewport.MIN_ZOOM, tooSmall.zoom, 0.001f)
    }

    @Test
    fun `dragging pans the board with the finger`() {
        val zoomed = BoardViewport(zoom = 3f, pan = Point2D(-400f, -400f))
        val moved = zoomed.transformedBy(Point2D(200f, 300f), Point2D(30f, -20f), 1f, view)
        assertPoint(Point2D(-370f, -420f), moved.pan)
    }

    @Test
    fun `board cannot be flung out of reach`() {
        val zoomed = BoardViewport(zoom = 2f)
        val flungRight = zoomed.transformedBy(Point2D.ZERO, Point2D(5000f, 5000f), 1f, view)
        val flungLeft = zoomed.transformedBy(Point2D.ZERO, Point2D(-5000f, -5000f), 1f, view)

        // Never more than the boundary margin past the top-left edge.
        assertEquals(view.x * BoardViewport.BOUNDARY_FRACTION, flungRight.pan.x, 0.01f)
        assertEquals(view.y * BoardViewport.BOUNDARY_FRACTION, flungRight.pan.y, 0.01f)
        // And the far edge of the board can always be reached.
        val farEdge = flungLeft.toScreen(Point2D(view.x, view.y))
        assertTrue(farEdge.x <= view.x * (1 + BoardViewport.BOUNDARY_FRACTION) + 0.01f)
        assertTrue(farEdge.y <= view.y * (1 + BoardViewport.BOUNDARY_FRACTION) + 0.01f)
    }

    @Test
    fun `zoomed out the board stays centred instead of drifting`() {
        val out = BoardViewport.RESET.transformedBy(Point2D(200f, 300f), Point2D(500f, 500f), 0.5f, view)
        assertEquals(BoardViewport.MIN_ZOOM, out.zoom, 0.001f)
        val centre = out.toScreen(Point2D(view.x / 2, view.y / 2))
        assertPoint(Point2D(view.x / 2, view.y / 2), centre, eps = view.x * 0.25f)
    }

    @Test
    fun `tap tolerance shrinks with zoom but never vanishes`() {
        assertEquals(22f, BoardViewport.tapRadius(22f, 1f), 0.001f)
        assertEquals(11f, BoardViewport.tapRadius(22f, 2f), 0.001f)
        assertEquals(5.5f, BoardViewport.tapRadius(22f, 4f), 0.001f)
        assertTrue(BoardViewport.tapRadius(22f, 100f) >= 5.5f)
    }
}
