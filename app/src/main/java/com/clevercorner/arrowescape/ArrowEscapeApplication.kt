package com.clevercorner.arrowescape

import android.app.Application
import com.clevercorner.arrowescape.ads.AdManager
import com.clevercorner.arrowescape.ads.AdManagerImpl
import com.clevercorner.arrowescape.analytics.AnalyticsTracker
import com.clevercorner.arrowescape.crash.CrashReporter
import com.clevercorner.arrowescape.analytics.Telemetry
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.economy.CoinBank
import com.clevercorner.arrowescape.game.curve.CurvedAssetShapes
import com.clevercorner.arrowescape.notifications.AppNotifications
import com.clevercorner.arrowescape.notifications.DailyReminderUseCase
import com.clevercorner.arrowescape.notifications.DailyReminderWorker
import com.clevercorner.arrowescape.notifications.LocalNotificationTokenProvider
import com.clevercorner.arrowescape.notifications.NotificationTokenProvider
import com.clevercorner.arrowescape.notifications.SystemAppNotifications
import com.clevercorner.arrowescape.ui.game.GameFeedback
import com.clevercorner.arrowescape.ui.milestone.MilestoneData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    override fun onCreate() {
        super.onCreate()
        // Must run before anything touches ShapeCatalog.ALL (the generator
        // reads it lazily on first level build, which is always later than
        // this) - see ShapeCatalog.register's ordering guard.
        CurvedAssetShapes.loadAndRegister(this)

        // Idempotent and cheap; channels need to exist before anything can
        // post to them. Minting the token here too is one-shot, fire-and-
        // forget plumbing-proving - nothing downstream reads it yet, but the
        // day something does, it will already be sitting in [preferences].
        appNotifications.ensureChannel(this)
        if (preferences.prefsOrDefault.value.notificationsEnabled) {
            DailyReminderWorker.schedule(this)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            notificationTokenProvider.getOrCreateToken()
        }
    }

    /** Built lazily so an empty process (e.g. a content provider) pays nothing. */
    val preferences: GamePreferences by lazy { GamePreferences(applicationContext) }

    /** One voice for the whole app - both game modes sound the same. */
    val feedback: GameFeedback by lazy { GameFeedback(applicationContext) }

    /**
     * The only thing that moves coins, and process-scoped for the same
     * reason [preferences] is: two banks would settle the same grant twice,
     * each believing it got there first.
     *
     * Chapter counting is handed in here because this is the composition
     * root - the one place entitled to know that the economy and the Journey
     * Map both exist.
     */
    val coinBank: CoinBank by lazy {
        CoinBank(preferences) { mode, progress ->
            // Each mode answers "how many chapters cleared" against its own
            // chapter length - see Journey.chaptersClearedFor. Dividing by a
            // single shared constant here used to under-count curved, whose
            // chapters are half as long as classic's.
            val journey = MilestoneData.forMode(mode)
            journey.chaptersClearedFor(progress.highestLevel)
                .coerceIn(0, journey.chapterCountFor(progress.highestLevel))
        }
    }

    val adManager: AdManager by lazy { AdManagerImpl(analytics) }

    /** The capability: turn a title/body into a notification. No opinions about why. */
    val appNotifications: AppNotifications by lazy { SystemAppNotifications() }

    /** This install's (dummy, local-only) push-registration token - see [NotificationTokenProvider]. */
    val notificationTokenProvider: NotificationTokenProvider by lazy {
        LocalNotificationTokenProvider(preferences)
    }

    /** Smart notification engine: streak saver, inactivity winback, weekend zen. */
    val dailyReminder: DailyReminderUseCase by lazy {
        DailyReminderUseCase(appNotifications, preferences, analytics)
    }

    private val telemetry by lazy { Telemetry.create(applicationContext) }

    val analytics: AnalyticsTracker get() = telemetry.analytics

    val crashReporter: CrashReporter get() = telemetry.crashReporter
}
