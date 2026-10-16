package com.example.arrowescape.analytics

import android.content.Context
import com.example.arrowescape.BuildConfig
import com.example.arrowescape.crash.CrashKeys
import com.example.arrowescape.crash.CrashReporter
import com.example.arrowescape.crash.FirebaseCrashReporter
import com.example.arrowescape.crash.NoOpCrashReporter
import com.google.firebase.FirebaseApp

/**
 * The app's telemetry, assembled in exactly one place.
 *
 * Callers receive the two interfaces and never learn whether Firebase is
 * behind them - which is what lets [BuildConfig.FIREBASE_ENABLED] flip the
 * whole thing off without touching a single call site.
 */
class Telemetry private constructor(
    val analytics: AnalyticsTracker,
    val crashReporter: CrashReporter
) {

    companion object {

        private const val TAG = "Telemetry"

        /**
         * Builds the sink stack:
         *
         *   rate limiter -> composite -> [ Logcat (debug), Firebase, Crashlytics ]
         *
         * The limiter wraps the composite rather than each sink, so a dropped
         * event is dropped consistently everywhere and the Logcat output stays
         * an honest preview of what the backend received.
         */
        fun create(context: Context): Telemetry {
            val firebaseReady = BuildConfig.FIREBASE_ENABLED && initializeFirebase(context)

            val crashReporter: CrashReporter =
                if (firebaseReady) FirebaseCrashReporter() else NoOpCrashReporter

            // The manifest ships with collection disabled; this is the single
            // switch that turns it back on, and only for a real project.
            crashReporter.setCollectionEnabled(firebaseReady)

            val sinks = buildList {
                if (BuildConfig.DEBUG) add(DebugAnalyticsTracker())
                if (firebaseReady) {
                    add(FirebaseAnalyticsTracker(context).apply { setCollectionEnabled(true) })
                    add(CrashlyticsBreadcrumbTracker(crashReporter))
                }
            }

            val analytics: AnalyticsTracker = when {
                sinks.isEmpty() -> NoOpAnalyticsTracker
                else -> RateLimitedAnalyticsTracker(CompositeAnalyticsTracker(sinks))
            }

            crashReporter.setKey(CrashKeys.USING_TEST_ADS, BuildConfig.USE_TEST_ADS)

            return Telemetry(analytics, crashReporter)
        }

        /**
         * Firebase is initialized from google-services.json at startup by its
         * own ContentProvider; this only confirms it worked. With the
         * placeholder credentials currently checked in it will fail, and the
         * app degrades to Logcat-only rather than crashing on launch.
         */
        private fun initializeFirebase(context: Context): Boolean =
            runCatching { FirebaseApp.initializeApp(context.applicationContext) != null }
                .onFailure { android.util.Log.w(TAG, "Firebase unavailable; telemetry stays local", it) }
                .getOrDefault(false)
    }
}
