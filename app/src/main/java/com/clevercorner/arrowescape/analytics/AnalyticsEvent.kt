package com.clevercorner.arrowescape.analytics

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
    INTERSTITIAL("interstitial"),
    BANNER("banner")
}

/** Parameter keys. Firebase truncates at 40 chars - these stay well short. */
object Params {
    const val LEVEL = "level"
    const val ARROWS = "arrows"
    const val REMAINING = "remaining"
    const val LIVES = "lives"
    const val MISTAKES = "mistakes"
    const val CONTINUE_USED = "continue_used"
    const val CLEARED_PCT = "cleared_pct"
    const val LEVELS_CLEARED = "levels_cleared"
    const val WIN_STREAK = "win_streak"
    const val AD_FORMAT = "ad_format"
    const val ERROR_CODE = "error_code"
    const val ERROR_MESSAGE = "error_message"
    const val REWARD_TYPE = "reward_type"
    const val REWARD_AMOUNT = "reward_amount"
    const val VALUE_MICROS = "value_micros"
    const val CURRENCY = "currency"
    const val PRECISION = "precision"

    // Custom engagement & segmentation keys
    const val GAME_MODE = "game_mode"
    const val IS_OFFLINE = "is_offline"
    const val THEME_ID = "theme_id"
    const val COST = "cost"
    const val DATE = "date"
    const val REWARD_PURPOSE = "reward_purpose"
    const val ERROR_TYPE = "error_type"
    const val CONTEXT = "context"
    const val ACTION = "action"
    const val SOURCE = "source"
    const val STARS = "stars"
    const val METHOD = "method"
    const val IS_SILENT = "is_silent"
    const val TYPE = "type"
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

    fun appOpen(isOffline: Boolean = false) =
        AnalyticsEvent("app_open", mapOf(Params.IS_OFFLINE to isOffline))

    fun login(method: String = "play_games", isSilent: Boolean = false) =
        AnalyticsEvent(
            "login",
            mapOf(
                Params.METHOD to method,
                Params.IS_SILENT to isSilent
            )
        )

    fun signOut(method: String = "play_games") =
        AnalyticsEvent("sign_out", mapOf(Params.METHOD to method))

    fun gameModeChanged(mode: String) =
        AnalyticsEvent("game_mode_changed", mapOf(Params.GAME_MODE to mode))

    fun gameStarted(level: Int, mode: String = "", isOffline: Boolean = false) =
        AnalyticsEvent(
            "game_started",
            buildMap {
                put(Params.LEVEL, level)
                if (mode.isNotEmpty()) put(Params.GAME_MODE, mode)
                put(Params.IS_OFFLINE, isOffline)
            }
        )

    fun levelStarted(
        level: Int,
        arrowCount: Int,
        mode: String = "",
        isOffline: Boolean = false
    ) = AnalyticsEvent(
        "level_started",
        buildMap {
            put(Params.LEVEL, level)
            put(Params.ARROWS, arrowCount)
            if (mode.isNotEmpty()) put(Params.GAME_MODE, mode)
            put(Params.IS_OFFLINE, isOffline)
        }
    )

    fun levelCompleted(
        level: Int,
        mistakes: Int,
        continueUsed: Boolean,
        mode: String = "",
        isOffline: Boolean = false
    ) = AnalyticsEvent(
        "level_completed",
        buildMap {
            put(Params.LEVEL, level)
            put(Params.MISTAKES, mistakes)
            put(Params.CONTINUE_USED, continueUsed)
            if (mode.isNotEmpty()) put(Params.GAME_MODE, mode)
            put(Params.IS_OFFLINE, isOffline)
        }
    )

    fun levelRestarted(level: Int) =
        AnalyticsEvent("level_restarted", mapOf(Params.LEVEL to level))

    fun livesExhausted(level: Int, clearedPercentage: Int, isOffline: Boolean = false) =
        AnalyticsEvent(
            "lives_exhausted",
            mapOf(
                Params.LEVEL to level,
                Params.CLEARED_PCT to clearedPercentage,
                Params.IS_OFFLINE to isOffline
            )
        )

    // ---- Daily challenge ------------------------------------------------

    fun dailyChallengeStarted(date: String) =
        AnalyticsEvent("daily_challenge_started", mapOf(Params.DATE to date))

    fun dailyChallengeCompleted(date: String, mistakes: Int, streak: Int) =
        AnalyticsEvent(
            "daily_challenge_completed",
            mapOf(
                Params.DATE to date,
                Params.MISTAKES to mistakes,
                Params.WIN_STREAK to streak
            )
        )

    fun dailyChallengeFailed(date: String, clearedPercentage: Int) =
        AnalyticsEvent(
            "daily_challenge_failed",
            mapOf(
                Params.DATE to date,
                Params.CLEARED_PCT to clearedPercentage
            )
        )

    // ---- Themes ---------------------------------------------------------

    fun themeSelected(themeId: String) =
        AnalyticsEvent("theme_selected", mapOf(Params.THEME_ID to themeId))

    fun themePurchased(themeId: String, cost: Int = 0) =
        AnalyticsEvent(
            "theme_purchased",
            buildMap {
                put(Params.THEME_ID, themeId)
                if (cost > 0) put(Params.COST, cost)
            }
        )

    // ---- Errors ---------------------------------------------------------

    fun appError(errorType: String, message: String, context: String = "") =
        AnalyticsEvent(
            "app_error",
            buildMap {
                put(Params.ERROR_TYPE, errorType)
                put(Params.ERROR_MESSAGE, message.take(100))
                if (context.isNotEmpty()) put(Params.CONTEXT, context.take(50))
            }
        )

    // ---- Store review & rating ------------------------------------------

    /**
     * We decided to show the in-app review flow. Play never tells us what the
     * player did next - by design - so this is the only reviewable moment
     * analytics will ever see.
     */
    fun reviewPromptRequested(levelsCleared: Int, winStreak: Int) =
        AnalyticsEvent(
            "review_prompt_requested",
            mapOf(Params.LEVELS_CLEARED to levelsCleared, Params.WIN_STREAK to winStreak)
        )

    fun ratingScreenShown(source: String = "home_card") =
        AnalyticsEvent("rating_screen_shown", mapOf(Params.SOURCE to source))

    fun ratingAction(action: String, stars: Int = 0, source: String = "") =
        AnalyticsEvent(
            "rating_action",
            buildMap {
                put(Params.ACTION, action)
                if (stars > 0) put(Params.STARS, stars)
                if (source.isNotEmpty()) put(Params.SOURCE, source)
            }
        )

    // ---- Push & Local Notifications ------------------------------------

    fun notificationShown(type: String) =
        AnalyticsEvent("notification_shown", mapOf(Params.TYPE to type))

    fun notificationOpened(type: String) =
        AnalyticsEvent("notification_opened", mapOf(Params.TYPE to type))

    // ---- In-App Updates ------------------------------------------------

    fun appUpdateShown(status: String = "available") =
        AnalyticsEvent("app_update_shown", mapOf(Params.ACTION to status))

    fun appUpdateAction(action: String) =
        AnalyticsEvent("app_update_action", mapOf(Params.ACTION to action))

    // ---- Ad funnel, as seen by the game ---------------------------------

    fun rewardedAdOffered(level: Int, purpose: String = "continue") =
        AnalyticsEvent(
            "rewarded_ad_offered",
            mapOf(Params.LEVEL to level, Params.REWARD_PURPOSE to purpose)
        )

    fun rewardedAdStarted(level: Int, purpose: String = "continue") =
        AnalyticsEvent(
            "rewarded_ad_started",
            mapOf(Params.LEVEL to level, Params.REWARD_PURPOSE to purpose)
        )

    fun rewardedAdCompleted(level: Int, purpose: String = "continue", rewardAmount: Int = 0) =
        AnalyticsEvent(
            "rewarded_ad_completed",
            buildMap {
                put(Params.LEVEL, level)
                put(Params.REWARD_PURPOSE, purpose)
                if (rewardAmount > 0) put(Params.REWARD_AMOUNT, rewardAmount)
            }
        )

    fun rewardedAdFailed(level: Int, purpose: String = "continue") =
        AnalyticsEvent(
            "rewarded_ad_failed",
            mapOf(Params.LEVEL to level, Params.REWARD_PURPOSE to purpose)
        )

    fun interstitialShown(level: Int) =
        AnalyticsEvent("interstitial_shown", mapOf(Params.LEVEL to level))

    // ---- Ad Monetization & Core SDK callbacks ----------------------------

    fun adLoadFailed(format: AdFormat, errorCode: Int, message: String) =
        adEvent(
            "ad_load_failed", format,
            Params.ERROR_CODE to errorCode,
            Params.ERROR_MESSAGE to message.take(100)
        )

    fun adShowFailed(format: AdFormat, errorCode: Int, message: String) =
        adEvent(
            "ad_show_failed", format,
            Params.ERROR_CODE to errorCode,
            Params.ERROR_MESSAGE to message.take(100)
        )

    fun adImpression(format: AdFormat) = adEvent("ad_impression", format)

    fun adClicked(format: AdFormat) = adEvent("ad_clicked", format)

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
