package com.example.arrowescape.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** [CrashReporter] backed by Firebase Crashlytics. */
class FirebaseCrashReporter(
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
) : CrashReporter {

    override fun log(message: String) = crashlytics.log(message)

    override fun setKey(key: String, value: Any) {
        when (value) {
            is Int -> crashlytics.setCustomKey(key, value)
            is Long -> crashlytics.setCustomKey(key, value)
            is Float -> crashlytics.setCustomKey(key, value)
            is Double -> crashlytics.setCustomKey(key, value)
            is Boolean -> crashlytics.setCustomKey(key, value)
            else -> crashlytics.setCustomKey(key, value.toString())
        }
    }

    override fun recordException(throwable: Throwable) = crashlytics.recordException(throwable)

    override fun setUserId(userId: String?) = crashlytics.setUserId(userId.orEmpty())

    override fun setCollectionEnabled(enabled: Boolean) =
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
}
