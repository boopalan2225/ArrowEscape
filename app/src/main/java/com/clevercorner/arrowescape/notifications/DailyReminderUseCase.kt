package com.clevercorner.arrowescape.notifications

import android.content.Context
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.analytics.AnalyticsTracker
import com.clevercorner.arrowescape.analytics.Events
import com.clevercorner.arrowescape.data.GamePreferences
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Intelligent notification use case.
 *
 * Chooses the right creative message and cadence without spamming the player:
 * 1. **Streak Saver**: Protects active player streaks (>= 1) in the evening before midnight.
 * 2. **Inactivity Win-Back**: Gentle, humorous nudge when the player has been inactive for 3+ days.
 * 3. **Weekend Zen**: Relaxing, peaceful invitation for Saturday / Sunday afternoons.
 * 4. **Daily / Alternate-Day Challenge**: Intriguing puzzle invite for non-streak players.
 *
 * Anti-spam guarantees:
 * - Never notifies during sleeping hours (10:00 PM to 10:00 AM).
 * - Never sends more than 1 notification within 20 hours.
 * - Does not buzz if today's puzzle is already completed.
 */
class DailyReminderUseCase(
    private val notifications: AppNotifications,
    private val preferences: GamePreferences,
    private val analyticsTracker: AnalyticsTracker? = null
) {
    enum class ReminderType(val id: Int, val analyticsName: String, val destination: String) {
        STREAK_SAVER(1001, "streak_saver", "daily"),
        DAILY_CHALLENGE(1002, "daily_challenge", "daily"),
        INACTIVITY_WINBACK(1003, "inactivity_winback", "home"),
        WEEKEND_ZEN(1004, "weekend_zen", "home")
    }

    /**
     * Evaluates whether a creative reminder should be shown right now.
     *
     * @param ignoreClearedForTesting When true (in test builds), bypasses cooldown
     * and cleared status so notification visual appearance can be verified immediately.
     */
    suspend fun maybeShow(context: Context, ignoreClearedForTesting: Boolean = false): Boolean {
        val currentPrefs = preferences.prefs.filterNotNull().first()
        if (!currentPrefs.notificationsEnabled) return false
        if (!notifications.hasPermission(context)) return false

        val now = LocalDateTime.now()
        val nowMillis = System.currentTimeMillis()
        val lastNotif = preferences.getLastNotificationTime()
        val lastAppOpen = preferences.getLastAppOpenTime()

        // Anti-spam & quiet-hour guards (bypassed only in test mode)
        if (!ignoreClearedForTesting) {
            val twentyHoursMillis = 20L * 60 * 60 * 1000
            if (nowMillis - lastNotif < twentyHoursMillis) return false

            // Quiet hours: strictly no disturbance between 10:00 PM and 10:00 AM
            val hour = now.hour
            if (hour < 10 || hour >= 22) return false
        }

        val todayDate = now.toLocalDate()
        val todayCleared = currentPrefs.daily.isCleared(todayDate)

        val daysSinceOpen = if (lastAppOpen > 0L) {
            (nowMillis - lastAppOpen) / (24L * 60 * 60 * 1000)
        } else {
            0L
        }

        val isWeekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY

        val reminderType: ReminderType
        val title: String
        val body: String

        if (ignoreClearedForTesting) {
            val streak = currentPrefs.daily.streak.coerceAtLeast(1)
            reminderType = ReminderType.STREAK_SAVER
            title = context.getString(R.string.notification_streak_saver_title)
            body = context.getString(R.string.notification_streak_saver_body, streak)
        } else if (daysSinceOpen >= 3) {
            // Case 1: Inactivity Win-Back (haven't opened in 3+ days)
            reminderType = ReminderType.INACTIVITY_WINBACK
            title = context.getString(R.string.notification_inactivity_title)
            body = context.getString(R.string.notification_inactivity_body)
        } else if (isWeekend && now.hour in 11..16 && !todayCleared) {
            // Case 2: Weekend Zen (Saturday / Sunday midday)
            reminderType = ReminderType.WEEKEND_ZEN
            title = context.getString(R.string.notification_weekend_title)
            body = context.getString(R.string.notification_weekend_body)
        } else if (!todayCleared && currentPrefs.daily.streak >= 1 && now.hour >= 18) {
            // Case 3: Evening Streak Saver (protect active streak before midnight)
            reminderType = ReminderType.STREAK_SAVER
            title = context.getString(R.string.notification_streak_saver_title)
            body = context.getString(R.string.notification_streak_saver_body, currentPrefs.daily.streak)
        } else if (!todayCleared && now.hour >= 17 && daysSinceOpen >= 1) {
            // Case 4: Alternate-Day Challenge (curiosity / gamified invite)
            reminderType = ReminderType.DAILY_CHALLENGE
            title = context.getString(R.string.notification_daily_fun_title)
            body = context.getString(R.string.notification_daily_fun_body)
        } else {
            return false
        }

        notifications.show(
            context = context,
            notificationId = reminderType.id,
            title = title,
            body = body,
            destination = reminderType.destination
        )

        preferences.recordNotificationShown(nowMillis)
        analyticsTracker?.log(Events.notificationShown(reminderType.analyticsName))
        return true
    }
}
