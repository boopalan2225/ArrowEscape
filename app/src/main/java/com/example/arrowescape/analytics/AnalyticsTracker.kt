package com.example.arrowescape.analytics

/**
 * Abstraction for analytics tracking.
 * Allows easy swapping between Firebase, custom analytics, or no-op.
 */
interface AnalyticsTracker {
    fun trackAppOpen()
    fun trackGameStarted(level: Int)
    fun trackLevelStarted(level: Int, arrowCount: Int)
    fun trackValidMove(level: Int, remainingArrows: Int)
    fun trackInvalidMove(level: Int, remainingLives: Int)
    fun trackLevelCompleted(level: Int, timeSeconds: Int, mistakes: Int, continueUsed: Boolean)
    fun trackLevelRestarted(level: Int)
    fun trackLivesExhausted(level: Int, clearedPercentage: Int)
    fun trackRewardedAdOffered(level: Int)
    fun trackRewardedAdStarted(level: Int)
    fun trackRewardedAdCompleted(level: Int)
    fun trackRewardedAdFailed(level: Int)
    fun trackInterstitialShown(level: Int)
}

/**
 * No-op implementation - logs nothing but satisfies the interface.
 * Replace with Firebase Analytics or custom solution when ready.
 */
class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun trackAppOpen() {}
    override fun trackGameStarted(level: Int) {}
    override fun trackLevelStarted(level: Int, arrowCount: Int) {}
    override fun trackValidMove(level: Int, remainingArrows: Int) {}
    override fun trackInvalidMove(level: Int, remainingLives: Int) {}
    override fun trackLevelCompleted(level: Int, timeSeconds: Int, mistakes: Int, continueUsed: Boolean) {}
    override fun trackLevelRestarted(level: Int) {}
    override fun trackLivesExhausted(level: Int, clearedPercentage: Int) {}
    override fun trackRewardedAdOffered(level: Int) {}
    override fun trackRewardedAdStarted(level: Int) {}
    override fun trackRewardedAdCompleted(level: Int) {}
    override fun trackRewardedAdFailed(level: Int) {}
    override fun trackInterstitialShown(level: Int) {}
}

/**
 * Debug implementation that logs to Logcat.
 */
class DebugAnalyticsTracker : AnalyticsTracker {
    private fun log(event: String, params: Map<String, Any> = emptyMap()) {
        val paramsStr = if (params.isNotEmpty()) " | $params" else ""
        android.util.Log.d("Analytics", "EVENT: $event$paramsStr")
    }

    override fun trackAppOpen() = log("app_open")
    override fun trackGameStarted(level: Int) = log("game_started", mapOf("level" to level))
    override fun trackLevelStarted(level: Int, arrowCount: Int) = 
        log("level_started", mapOf("level" to level, "arrows" to arrowCount))
    override fun trackValidMove(level: Int, remainingArrows: Int) = 
        log("valid_move", mapOf("level" to level, "remaining" to remainingArrows))
    override fun trackInvalidMove(level: Int, remainingLives: Int) = 
        log("invalid_move", mapOf("level" to level, "lives" to remainingLives))
    override fun trackLevelCompleted(level: Int, timeSeconds: Int, mistakes: Int, continueUsed: Boolean) = 
        log("level_completed", mapOf("level" to level, "time" to timeSeconds, "mistakes" to mistakes, "continue" to continueUsed))
    override fun trackLevelRestarted(level: Int) = log("level_restarted", mapOf("level" to level))
    override fun trackLivesExhausted(level: Int, clearedPercentage: Int) = 
        log("lives_exhausted", mapOf("level" to level, "cleared" to clearedPercentage))
    override fun trackRewardedAdOffered(level: Int) = log("rewarded_ad_offered", mapOf("level" to level))
    override fun trackRewardedAdStarted(level: Int) = log("rewarded_ad_started", mapOf("level" to level))
    override fun trackRewardedAdCompleted(level: Int) = log("rewarded_ad_completed", mapOf("level" to level))
    override fun trackRewardedAdFailed(level: Int) = log("rewarded_ad_failed", mapOf("level" to level))
    override fun trackInterstitialShown(level: Int) = log("interstitial_shown", mapOf("level" to level))
}
