package com.example.arrowescape.game.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrowMotionTest {

    @Test
    fun `longer arrows take longer to escape but stay within bounds`() {
        val short = ArrowMotion.escapeDurationMs(40f)
        val long = ArrowMotion.escapeDurationMs(300f)
        assertTrue(long > short)
        assertTrue(short >= 600L)
        assertTrue(ArrowMotion.escapeDurationMs(100_000f) <= 1200L)
    }

    @Test
    fun `escape easing starts and ends at rest`() {
        assertEquals(0f, ArrowMotion.easeEscape(0f), 0.0001f)
        assertEquals(1f, ArrowMotion.easeEscape(1f), 0.0001f)
        assertEquals(0.5f, ArrowMotion.easeEscape(0.5f), 0.0001f)
        assertEquals(0f, ArrowMotion.easeEscape(-3f), 0.0001f)
        assertEquals(1f, ArrowMotion.easeEscape(9f), 0.0001f)
    }

    @Test
    fun `bump lunges toward the blocker but leaves room for the head`() {
        val sw = 10f
        val runway = 100f
        val bump = ArrowMotion.bumpDistance(runway, sw)
        // Tip + head depth must not pass the contact point.
        assertTrue(bump + sw * ArrowHeadGeometry.DEPTH <= runway + 0.001f)
        assertTrue(bump > 0f)
    }

    @Test
    fun `bump is always a visible nudge even when already touching`() {
        val sw = 10f
        assertTrue(ArrowMotion.bumpDistance(0f, sw) >= sw * 0.5f)
    }

    @Test
    fun `bump never turns into an escape`() {
        val sw = 10f
        assertTrue(ArrowMotion.bumpDistance(5000f, sw) <= sw * 8f)
    }

    @Test
    fun `bump profile goes out and comes back`() {
        assertEquals(0f, ArrowMotion.bumpProfile(0f), 0.0001f)
        assertEquals(1f, ArrowMotion.bumpProfile(0.5f), 0.0001f)
        assertEquals(0f, ArrowMotion.bumpProfile(1f), 0.0001f)
    }

    @Test
    fun `bump duration scales with distance within bounds`() {
        assertTrue(ArrowMotion.bumpDurationMs(10f) >= 220L)
        assertTrue(ArrowMotion.bumpDurationMs(10_000f) <= 420L)
    }
}
