package com.clevercorner.arrowescape.review

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide when a player gets asked for a review.
 *
 * Worth testing precisely because they are invisible: a bug here is either
 * silence forever or nagging on every level, and neither shows up in a
 * five-minute play test.
 */
class ReviewPolicyTest {

    private val onARoll = ReviewState(winStreak = ReviewPolicy.STREAK_REQUIRED)
    private val plentyCleared = ReviewPolicy.MIN_LEVELS_CLEARED + 1

    @Test
    fun `asks a player on a streak who has played enough`() {
        assertTrue(ReviewPolicy.shouldAsk(onARoll, plentyCleared))
    }

    @Test
    fun `stays quiet below the streak`() {
        val oneShort = onARoll.copy(winStreak = ReviewPolicy.STREAK_REQUIRED - 1)
        assertFalse(ReviewPolicy.shouldAsk(oneShort, plentyCleared))
    }

    @Test
    fun `stays quiet for a brand new player`() {
        assertFalse(ReviewPolicy.shouldAsk(onARoll, ReviewPolicy.MIN_LEVELS_CLEARED - 1))
    }

    @Test
    fun `never asks someone who already rated`() {
        assertFalse(ReviewPolicy.shouldAsk(onARoll.copy(rated = true), plentyCleared))
    }

    @Test
    fun `gives up after the maximum number of asks`() {
        val exhausted = onARoll.copy(timesAsked = ReviewPolicy.MAX_ASKS, levelsAtLastAsk = 0)
        assertFalse(ReviewPolicy.shouldAsk(exhausted, 500))
    }

    @Test
    fun `waits out the quiet period between asks`() {
        val askedOnce = onARoll.copy(timesAsked = 1, levelsAtLastAsk = 20)
        val tooSoon = 20 + ReviewPolicy.LEVELS_BETWEEN_ASKS - 1
        assertFalse(ReviewPolicy.shouldAsk(askedOnce, tooSoon))
        assertTrue(ReviewPolicy.shouldAsk(askedOnce, tooSoon + 1))
    }

    @Test
    fun `a loss ends the streak and a win starts a new one`() {
        val afterLoss = ReviewPolicy.afterLoss(onARoll)
        assertEquals(0, afterLoss.winStreak)
        assertEquals(1, ReviewPolicy.afterWin(afterLoss).winStreak)
    }

    @Test
    fun `asking resets the streak so the next ask needs a fresh run`() {
        val afterAsk = ReviewPolicy.afterAsk(onARoll, plentyCleared)

        assertEquals(1, afterAsk.timesAsked)
        assertEquals(plentyCleared, afterAsk.levelsAtLastAsk)
        assertEquals(0, afterAsk.winStreak)
        assertFalse(ReviewPolicy.shouldAsk(afterAsk, plentyCleared))
    }
}
