package com.arrowescape.review

import com.arrowescape.analytics.AnalyticsTracker
import com.arrowescape.analytics.Events
import com.arrowescape.data.GamePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the win streak up to date and raises a flag when [ReviewPolicy] says
 * the moment is right.
 *
 * Sits between the ViewModels and the Android review API so neither game mode
 * grows its own copy of the rules, and so the rules themselves stay in a pure
 * Kotlin object that tests can reason about.
 */
class ReviewPrompter(
    private val preferences: GamePreferences,
    private val analytics: AnalyticsTracker
) {
    private val _requested = MutableStateFlow(false)

    /** True while a review flow is waiting to be launched by the UI. */
    val requested: StateFlow<Boolean> = _requested.asStateFlow()

    /**
     * Call on every cleared level, with the player's freshly updated totals.
     *
     * Writing the new state is the caller's reward for giving us the current
     * one - there is no second read of preferences here, so the streak cannot
     * drift away from the completion count it is judged against.
     */
    suspend fun onLevelCleared(state: ReviewState, totalLevelsCleared: Int) {
        val afterWin = ReviewPolicy.afterWin(state)
        if (!ReviewPolicy.shouldAsk(afterWin, totalLevelsCleared)) {
            preferences.saveReviewState(afterWin)
            return
        }

        preferences.saveReviewState(ReviewPolicy.afterAsk(afterWin, totalLevelsCleared))
        analytics.log(Events.reviewPromptRequested(totalLevelsCleared, afterWin.winStreak))
        _requested.value = true
    }

    /** Call when the player runs out of lives or restarts: the run is over. */
    suspend fun onLevelFailed(state: ReviewState) {
        if (state.winStreak == 0) return
        preferences.saveReviewState(ReviewPolicy.afterLoss(state))
    }

    /** The UI has launched (or failed to launch) the flow; lower the flag. */
    fun onRequestHandled() {
        _requested.value = false
    }
}
