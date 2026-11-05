package com.clevercorner.arrowescape.notifications

import android.content.Context
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.data.GamePreferences
import java.time.LocalDate

/**
 * The one concrete reason this app would show a notification today: a
 * player whose daily-puzzle streak (see `game.daily`) is waiting on them.
 *
 * Deciding *whether* to remind - already solved today, or the OS refusing
 * permission - is a business rule, so it lives here rather than in
 * [AppNotifications], which only knows how to put a title and a body on
 * screen and has no opinion about daily puzzles.
 */
class DailyReminderUseCase(
    private val notifications: AppNotifications,
    private val preferences: GamePreferences
) {
    companion object {
        /** Stable across calls so a second reminder replaces the first instead of stacking. */
        const val NOTIFICATION_ID = 1001
    }

    /**
     * Shows the reminder if, and only if, there is still something to remind
     * about.
     *
     * Nothing currently calls this on a schedule - that piece (a daily
     * trigger, most naturally `WorkManager`'s `PeriodicWorkRequest`) is
     * intentionally not part of this change; see the package's notification
     * stack for what exists so far and wire a trigger to this method when
     * ready.
     */
    fun maybeShow(context: Context) {
        if (!notifications.hasPermission(context)) return

        val today = preferences.prefsOrDefault.value.daily
        if (today.isCleared(LocalDate.now())) return

        notifications.show(
            context = context,
            notificationId = NOTIFICATION_ID,
            title = context.getString(R.string.notification_daily_reminder_title),
            body = context.getString(R.string.notification_daily_reminder_body)
        )
    }
}
