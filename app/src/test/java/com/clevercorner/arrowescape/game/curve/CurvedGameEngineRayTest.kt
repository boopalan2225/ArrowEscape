package com.clevercorner.arrowescape.game.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurvedGameEngineRayTest {

    private val board = CurvedGameEngine.Board(400f, 600f)

    @Test
    fun `ray exits through the right edge`() {
        val exit = board.rayExit(Point2D(100f, 300f), Point2D(1f, 0f))
        assertEquals(400f, exit.x, 0.01f)
        assertEquals(300f, exit.y, 0.01f)
    }

    @Test
    fun `ray exits through the top edge`() {
        val exit = board.rayExit(Point2D(100f, 300f), Point2D(0f, -1f))
        assertEquals(100f, exit.x, 0.01f)
        assertEquals(0f, exit.y, 0.01f)
    }

    @Test
    fun `diagonal ray stops at whichever edge it meets first`() {
        // From (350, 100) heading down-right: the right edge is 50 away, bottom is 500.
        val exit = board.rayExit(Point2D(350f, 100f), Point2D(1f, 1f))
        assertEquals(400f, exit.x, 0.01f)
        assertEquals(150f, exit.y, 0.01f)
    }

    @Test
    fun `zero direction returns the origin`() {
        val origin = Point2D(10f, 10f)
        assertEquals(origin, board.rayExit(origin, Point2D.ZERO))
    }

    @Test
    fun `runway to a blocker is the gap in front of the tip`() {
        val arrow = CurvedArrow.simple(0, Point2D(50f, 300f), Point2D(150f, 300f), bendStrength = 0f)
        val wall = CurvedArrow.simple(1, Point2D(250f, 200f), Point2D(250f, 400f), bendStrength = 0f)

        val runway = CurvedGameEngine.runwayBeforeContact(arrow, listOf(wall), board)

        // Gap is 100 minus roughly one stroke width of contact.
        assertTrue("runway $runway should be shy of the 100 gap", runway in 70f..100f)
    }

    @Test
    fun `runway with nothing in the way runs to the board edge`() {
        val arrow = CurvedArrow.simple(0, Point2D(50f, 300f), Point2D(150f, 300f), bendStrength = 0f)
        val runway = CurvedGameEngine.runwayBeforeContact(arrow, emptyList(), board)
        assertTrue("runway $runway should reach the edge (~250)", runway >= 240f)
    }

    @Test
    fun `an arrow is never its own blocker`() {
        val arrow = CurvedArrow.simple(0, Point2D(50f, 300f), Point2D(150f, 300f), bendStrength = 0f)
        val runway = CurvedGameEngine.runwayBeforeContact(arrow, listOf(arrow), board)
        assertTrue(runway >= 240f)
    }
}
