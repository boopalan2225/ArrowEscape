package com.arrowescape.game.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DependencyTrackerTest {

    private val board = CurvedGameEngine.Board(400f, 600f)
    private val random = Random(1)

    private fun tracker() = DependencyTracker(board.width, board.height)

    private fun straight(id: Int, from: Point2D, to: Point2D) =
        CurvedArrow.simple(id, from, to, bendStrength = 0f).copy(strokeWidth = 6f)

    private fun tryAdd(t: DependencyTracker, arrow: CurvedArrow, max: Int) =
        t.tryAdd(listOf(arrow), max, random)

    @Test
    fun `an arrow with a clear exit is always admitted`() {
        val t = tracker()
        assertNotNull(tryAdd(t, straight(0, Point2D(50f, 300f), Point2D(150f, 300f)), max = 0))
    }

    @Test
    fun `zero dependencies reproduces the strict clear-exit rule`() {
        val t = tracker()
        tryAdd(t, straight(0, Point2D(250f, 200f), Point2D(250f, 400f)), max = 0)

        // Aimed straight at the wall above: has a blocker, so rejected at max 0.
        val blocked = straight(1, Point2D(50f, 300f), Point2D(150f, 300f))
        assertNull(tryAdd(t, blocked, max = 0))
    }

    @Test
    fun `a tucked-away arrow is admitted once dependencies are allowed`() {
        val t = tracker()
        tryAdd(t, straight(0, Point2D(250f, 200f), Point2D(250f, 400f)), max = 1)

        val blocked = straight(1, Point2D(50f, 300f), Point2D(150f, 300f))
        assertNotNull(tryAdd(t, blocked, max = 1))
    }

    @Test
    fun `two arrows facing each other would deadlock and are refused`() {
        val t = tracker()
        // Right-facing arrow on the left, then a left-facing arrow in its path.
        tryAdd(t, straight(0, Point2D(50f, 300f), Point2D(150f, 300f)), max = 8)
        val headOn = straight(1, Point2D(350f, 300f), Point2D(250f, 300f))

        // Each would wait for the other, so no allowance can admit it.
        assertNull(tryAdd(t, headOn, max = 8))
    }

    @Test
    fun `the tracker offers the opposite head when one orientation deadlocks`() {
        val t = tracker()
        tryAdd(t, straight(0, Point2D(50f, 300f), Point2D(150f, 300f)), max = 8)
        val headOn = straight(1, Point2D(350f, 300f), Point2D(250f, 300f))

        val admitted = t.tryAdd(listOf(headOn, headOn.reversed()), maxDependencies = 8, random = random)

        assertNotNull(admitted)
        // The flipped arrow points away from the first one.
        assertTrue(admitted!!.direction.x > 0f)
    }

    @Test
    fun `fewest dependencies wins among viable orientations`() {
        val t = tracker()
        tryAdd(t, straight(0, Point2D(300f, 200f), Point2D(300f, 400f)), max = 8)

        // Right-facing hits the wall (1 dependency); left-facing is clear (0).
        val right = straight(1, Point2D(100f, 300f), Point2D(200f, 300f))
        val admitted = t.tryAdd(listOf(right, right.reversed()), maxDependencies = 8, random = random)

        assertTrue("should prefer the clear exit", admitted!!.direction.x < 0f)
    }

    @Test
    fun `everything admitted together is solvable by the real engine`() {
        // Pack a grid of arrows at random headings with a generous allowance,
        // then confirm the engine - not just the tracker - can clear them all.
        val t = tracker()
        val rng = Random(7)
        val admitted = mutableListOf<CurvedArrow>()
        var id = 0
        for (row in 0 until 12) for (col in 0 until 8) {
            val x = 30f + col * 46f
            val y = 30f + row * 46f
            val from = Point2D(x, y)
            val to = Point2D(x + 28f, y + rng.nextFloat() * 20f - 10f)
            val arrow = straight(id, from, to)
            t.tryAdd(listOf(arrow, arrow.reversed()), maxDependencies = 6, random = rng)
                ?.also { admitted.add(it); id++ }
        }

        assertTrue("expected a decent crowd, got ${admitted.size}", admitted.size > 40)
        assertEquals(admitted.size, t.count)
        val result = CurvedGameEngine.analyze(admitted, board)
        assertTrue("tracker admitted a deadlock the engine can see", result.solved)
        assertEquals(admitted.size, result.order.size)
    }
}
