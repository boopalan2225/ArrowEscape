package com.clevercorner.arrowescape.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Sends events to Firebase Analytics.
 *
 * Nothing game-specific lives here - it only knows how to turn an
 * [AnalyticsEvent] into a [Bundle].
 */
class FirebaseAnalyticsTracker(
    private val firebaseAnalytics: FirebaseAnalytics
) : AnalyticsTracker {

    constructor(context: Context) : this(FirebaseAnalytics.getInstance(context.applicationContext))

    override fun log(event: AnalyticsEvent) {
        firebaseAnalytics.logEvent(event.name.sanitized(), event.params.toBundle())
    }

    /** Lets reports split by player once a stable id exists (Play Games, etc.). */
    fun setUserId(userId: String?) = firebaseAnalytics.setUserId(userId)

    /** Honours the player's choice; Firebase persists it across launches. */
    fun setCollectionEnabled(enabled: Boolean) =
        firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)

    private fun Map<String, Any>.toBundle(): Bundle = Bundle().also { bundle ->
        forEach { (key, value) ->
            val name = key.sanitized()
            when (value) {
                is Int -> bundle.putLong(name, value.toLong())
                is Long -> bundle.putLong(name, value)
                is Float -> bundle.putDouble(name, value.toDouble())
                is Double -> bundle.putDouble(name, value)
                // Firebase has no boolean param type; 1/0 keeps it chartable.
                is Boolean -> bundle.putLong(name, if (value) 1L else 0L)
                else -> bundle.putString(name, value.toString().take(MAX_VALUE_LENGTH))
            }
        }
    }

    private companion object {
        /** Firebase limits: 40 chars for names, 100 for string values. */
        const val MAX_NAME_LENGTH = 40
        const val MAX_VALUE_LENGTH = 100

        fun String.sanitized() = take(MAX_NAME_LENGTH)
    }
}
