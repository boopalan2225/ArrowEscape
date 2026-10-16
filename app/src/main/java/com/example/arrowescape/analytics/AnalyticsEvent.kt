package com.example.arrowescape.analytics

/**
 * One analytics event: a name and its parameters.
 *
 * Every sink (Logcat, Firebase, Crashlytics breadcrumbs) receives this same
 * value, so event names and parameter keys are spelled exactly once - in
 * [Events] below - instead of once per backend.
 */
data class AnalyticsEvent(
    val name: String,
    val params: Map<String, Any> = emptyMap()
)

/**
 * Which ad surface an event is about.
 *
 * Lives in the analytics package on purpose: it is part of the reporting
 * vocabulary, and keeping it here lets `ads` depend on `analytics` and never
 * the other way around.
 */
enum class AdFormat(val reportingName: String) {
    REWARDED("rewarded"),
    INTERSTITIAL("interstitial")
}

/** Parameter keys. Firebase truncates at 40 chars - these stay well short. */
object Params {
    const val LEVEL = "level"
    const val ARROWS = "arrows"
    const val REMAINING = "remaining"
    const val LIVES = "lives"
    const val TIME_SECONDS = "time_seconds"
    const val MISTAKES = "mistakes"
    const val CONTINUE_USED = "continue_used"
    const val CLEARED_PCT = "cleared_pct"
    const val AD_FORMAT = "ad_format"
    const val ERROR_CODE = "error_code"
    const val ERROR_MESSAGE = "error_message"
    const val REWARD_TYPE = "reward_type"
    const val REWARD_AMOUNT = "reward_amount"
    const val VALUE_MICROS = "value_micros"
    const val CURRENCY = "currency"
    const val PRECISION = "precision"
}

/**
 * The complete event catalog.
 *
 * Adding an event means adding exactly one factory here; no sink needs to
 * change, which is the whole point of routing everything through
 * [AnalyticsEvent].
 */
object Events {

    // ---- Lifecycle / gameplay -------------------------------------------

    fun appOpen() = AnalyticsEvent("app_open")

    fun gameStarted(level: Int) =
        AnalyticsEvent("game_started", mapOf(Params.LEVEL to level))

    fun levelStarted(level: Int, arrowCount: Int) =
        AnalyticsEvent("level_started", mapOf(Params.LEVEL to level, Params.ARROWS to arrowCount))

    fun validMove(level: Int, remainingArrows: Int) =
        AnalyticsEvent("valid_move", mapOf(Params.LEVEL to level, Params.REMAINING to remainingArrows))

    fun invalidMove(level: Int, remainingLives: Int) =
        AnalyticsEvent("invalid_move", mapOf(Params.LEVEL to level, Params.LIVES to remainingLives))

    fun levelCompleted(level: Int, timeSeconds: Int, mistakes: Int, continueUsed: Boolean) =
        AnalyticsEvent(
            "level_completed",
            mapOf(
                Params.LEVEL to level,
                Params.TIME_SECONDS to timeSeconds,
                Params.MISTAKES to mistakes,
                Params.CONTINUE_USED to continueUsed
            )
        )

    fun levelRestarted(level: Int) =
        AnalyticsEvent("level_restarted", mapOf(Params.LEVEL to level))

    fun livesExhausted(level: Int, clearedPercentage: Int) =
        AnalyticsEvent(
            "lives_exhausted",
            mapOf(Params.LEVEL to level, Params.CLEARED_PCT to clearedPercentage)
        )

    // ---- Ad funnel, as seen by the game ---------------------------------

    fun rewardedAdOffered(level: Int) =
        AnalyticsEvent("rewarded_ad_offered", mapOf(Params.LEVEL to level))

    fun rewardedAdStarted(level: Int) =
        AnalyticsEvent("rewarded_ad_started", mapOf(Params.LEVEL to level))

    fun rewardedAdCompleted(level: Int) =
        AnalyticsEvent("rewarded_ad_completed", mapOf(Params.LEVEL to level))

    fun rewardedAdFailed(level: Int) =
        AnalyticsEvent("rewarded_ad_failed", mapOf(Params.LEVEL to level))

    fun interstitialShown(level: Int) =
        AnalyticsEvent("interstitial_shown", mapOf(Params.LEVEL to level))

    // ---- Ad SDK callbacks, as seen by AdManager --------------------------
    // These mirror the AdMob callbacks one-for-one so a drop-off in the
    // funnel (requested -> loaded -> shown -> impression -> dismissed) is
    // visible without guessing.

    fun adLoadRequested(format: AdFormat) = adEvent("ad_load_requested", format)

    fun adLoaded(format: AdFormat) = adEvent("ad_loaded", format)

    fun adLoadFailed(format: AdFormat, errorCode: Int, message: String) =
        adEvent(
            "ad_load_failed", format,
            Params.ERROR_CODE to errorCode,
            Params.ERROR_MESSAGE to message.take(100)
        )

    fun adShowRequested(format: AdFormat) = adEvent("ad_show_requested", format)

    fun adNotReady(format: AdFormat) = adEvent("ad_not_ready", format)

    fun adShowFailed(format: AdFormat, errorCode: Int, message: String) =
        adEvent(
            "ad_show_failed", format,
            Params.ERROR_CODE to errorCode,
            Params.ERROR_MESSAGE to message.take(100)
        )

    fun adImpression(format: AdFormat) = adEvent("ad_impression", format)

    fun adClicked(format: AdFormat) = adEvent("ad_clicked", format)

    fun adDismissed(format: AdFormat) = adEvent("ad_dismissed", format)

    fun adRewardEarned(format: AdFormat, type: String, amount: Int) =
        adEvent(
            "ad_reward_earned", format,
            Params.REWARD_TYPE to type,
            Params.REWARD_AMOUNT to amount
        )

    /** Paid-event callback: the only place real ad revenue is observable. */
    fun adRevenue(format: AdFormat, valueMicros: Long, currency: String, precision: Int) =
        adEvent(
            "ad_revenue", format,
            Params.VALUE_MICROS to valueMicros,
            Params.CURRENCY to currency,
            Params.PRECISION to precision
        )

    private fun adEvent(name: String, format: AdFormat, vararg extras: Pair<String, Any>) =
        AnalyticsEvent(name, mapOf(Params.AD_FORMAT to format.reportingName) + extras)
}
