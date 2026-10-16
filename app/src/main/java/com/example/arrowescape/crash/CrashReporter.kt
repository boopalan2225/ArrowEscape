package com.example.arrowescape.crash

/**
 * Crash reporting, kept behind an interface for the same reason analytics is:
 * tests, previews, and ad-free builds should not drag in Firebase.
 */
interface CrashReporter {
    /** Breadcrumb attached to the next crash report. */
    fun log(message: String)

    /** Searchable key on every subsequent report. Value is stringified. */
    fun setKey(key: String, value: Any)

    /** A handled problem worth seeing in the dashboard, without crashing. */
    fun recordException(throwable: Throwable)

    /** Ties reports to a player once a stable id exists. */
    fun setUserId(userId: String?)

    fun setCollectionEnabled(enabled: Boolean)
}

/** Keys used across the app, spelled once. */
object CrashKeys {
    const val GAME_MODE = "game_mode"
    const val CURRENT_LEVEL = "current_level"
    const val LAST_AD_EVENT = "last_ad_event"
    const val USING_TEST_ADS = "using_test_ads"
}

object NoOpCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun setKey(key: String, value: Any) = Unit
    override fun recordException(throwable: Throwable) = Unit
    override fun setUserId(userId: String?) = Unit
    override fun setCollectionEnabled(enabled: Boolean) = Unit
}
