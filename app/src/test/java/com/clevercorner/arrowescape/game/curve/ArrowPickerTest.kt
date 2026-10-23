package com.clevercorner.arrowescape.game.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrowPickerTest {

    private val left = CurvedArrow.simple(1, Point2D(0f, 100f), Point2D(100f, 100f), bendStrength = 0f)
    private val right = CurvedArrow.simple(2, Point2D(0f, 120f), Point2D(100f, 120f), bendStrength = 0f)

    @Test
    fun `nearest arrow wins when several are in reach`() {
        // 8 from the first arrow, 12 from the second, both inside a 22 radius.
        val picked = ArrowPicker.pick(listOf(left, right), Point2D(50f, 108f), radius = 22f)
        assertEquals(1, picked?.id)
    }

    @Test
    fun `order of the list does not matter`() {
        val picked = ArrowPicker.pick(listOf(right, left), Point2D(50f, 108f), radius = 22f)
        assertEquals(1, picked?.id)
    }

    @Test
    fun `nothing in reach picks nothing`() {
        assertNull(ArrowPicker.pick(listOf(left, right), Point2D(50f, 300f), radius = 22f))
    }

    @Test
    fun `escaped arrows cannot be picked`() {
        val gone = left.withActive(false)
        assertNull(ArrowPicker.pick(listOf(gone), Point2D(50f, 100f), radius = 22f))
    }

    @Test
    fun `smaller radius tightens the pick`() {
        assertNull(ArrowPicker.pick(listOf(left), Point2D(50f, 108f), radius = 5f))
    }
}
