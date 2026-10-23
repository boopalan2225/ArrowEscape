package com.clevercorner.arrowescape.review

/**
 * What we remember about ever having asked for a review.
 *
 * Deliberately four small numbers: anything richer (timestamps per ask, star
 * counts, sentiment) would be data we are not allowed to act on anyway - see
 * [ReviewPolicy] for why.
 */
data class ReviewState(
    /** Levels cleared back to back without running out of lives. */
    val winStreak: Int = 0,
    /** How many times the review flow has been offered. */
    val timesAsked: Int = 0,
    /** Total levels cleared at the moment of the last ask. */
    val levelsAtLastAsk: Int = 0,
    /** Set once the player actually completed a review flow. */
    val rated: Boolean = false
)

/**
 * Decides *when* to ask for a store review - never *who* to ask.
 *
 * Play's In-App Review guidelines forbid screening players by sentiment
 * ("do you like the game?" then only sending the happy ones to the store),
 * so the only lever left is timing: catch people mid-win, not mid-rage-quit.
 *
 * Pure Kotlin on purpose. The rules are the part worth testing; launching
 * an Android dialog is not.
 */
object ReviewPolicy {

    /** A player on a roll is enjoying themselves; that is the whole signal. */
    const val STREAK_REQUIRED = 3

    /** Nobody can judge a game they have played for four minutes. */
    const val MIN_LEVELS_CLEARED = 6

    /** A "no thanks" has to buy real quiet, measured in levels played. */
    const val LEVELS_BETWEEN_ASKS = 30

    /** Asked three times and ignored three times means the answer is no. */
    const val MAX_ASKS = 3

    /**
     * True when this moment - just after clearing [totalLevelsCleared] - is a
     * good one to show the review flow.
     */
    fun shouldAsk(state: ReviewState, totalLevelsCleared: Int): Boolean {
        if (state.rated) return false
        if (state.timesAsked >= MAX_ASKS) return false
        if (state.winStreak < STREAK_REQUIRED) return false
        if (totalLevelsCleared < MIN_LEVELS_CLEARED) return false

        val firstAsk = state.timesAsked == 0
        return firstAsk || totalLevelsCleared - state.levelsAtLastAsk >= LEVELS_BETWEEN_ASKS
    }

    /** A cleared level extends the streak. */
    fun afterWin(state: ReviewState): ReviewState =
        state.copy(winStreak = state.winStreak + 1)

    /** Running out of lives - or giving up on a level - ends it. */
    fun afterLoss(state: ReviewState): ReviewState =
        state.copy(winStreak = 0)

    /**
     * Records that we asked. The streak resets too: the next ask should need
     * a fresh run of wins, not the tail of the one we already cashed in.
     */
    fun afterAsk(state: ReviewState, totalLevelsCleared: Int): ReviewState =
        state.copy(
            timesAsked = state.timesAsked + 1,
            levelsAtLastAsk = totalLevelsCleared,
            winStreak = 0
        )
}
