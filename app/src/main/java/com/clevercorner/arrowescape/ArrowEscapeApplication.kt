package com.clevercorner.arrowescape

import android.app.Application
import com.clevercorner.arrowescape.ads.AdManager
import com.clevercorner.arrowescape.ads.AdManagerImpl
import com.clevercorner.arrowescape.analytics.AnalyticsTracker
import com.clevercorner.arrowescape.crash.CrashReporter
import com.clevercorner.arrowescape.analytics.Telemetry
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.ui.game.GameFeedback

/**
 * The things that live as long as the process does.
 *
 * They used to be built in `MainActivity.onCreate`, which quietly meant
 * "once per Activity" - and an Activity is recreated by a locale change, a
 * dark-mode toggle, a font-size change. Each recreation built a second
 * [GamePreferences] with its own eagerly-started read, and a second
 * [GameFeedback] with its own SoundPool, while the ViewModels - which
 * survive recreation - kept pointing at the first ones. That is a leak on
 * one side and a released SoundPool on the other.
 *
 * None of this is a dependency-injection framework, and it should not grow
 * into one. It is a list of genuinely process-scoped objects, held at
 * process scope, because that is where they belong.
 */
class ArrowEscapeApplication : Application() {

    /** Built lazily so an empty process (e.g. a content provider) pays nothing. */
    val preferences: GamePreferences by lazy { GamePreferences(applicationContext) }

    /** One voice for the whole app - both game modes sound the same. */
    val feedback: GameFeedback by lazy { GameFeedback(applicationContext) }

    val adManager: AdManager by lazy { AdManagerImpl(analytics) }

    private val telemetry by lazy { Telemetry.create(applicationContext) }

    val analytics: AnalyticsTracker get() = telemetry.analytics

    val crashReporter: CrashReporter get() = telemetry.crashReporter
}
