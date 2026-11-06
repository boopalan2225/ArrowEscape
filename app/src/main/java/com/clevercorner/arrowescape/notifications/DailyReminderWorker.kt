package com.clevercorner.arrowescape.notifications

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.clevercorner.arrowescape.ArrowEscapeApplication
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Executes periodically in the background to show the daily puzzle reminder
 * via [DailyReminderUseCase].
 */
class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "DailyReminderWorker doWork triggered")
        val app = applicationContext as? ArrowEscapeApplication ?: return Result.success()
        val currentPrefs = app.preferences.prefs.filterNotNull().first()
        if (!currentPrefs.notificationsEnabled) {
            cancel(applicationContext)
            return Result.success()
        }

        app.dailyReminder.maybeShow(applicationContext, ignoreClearedForTesting = TEST_MODE)

        // In test mode, chain the next one-time request 10 minutes out
        if (TEST_MODE) {
            scheduleTestInterval(applicationContext, delayMinutes = 10)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "DailyReminderWorker"
        const val WORK_NAME = "daily_puzzle_reminder"
        private const val TARGET_HOUR = 19 // 7:00 PM local time

        /**
         * Test mode flag:
         * - When `true`: Fires 10 seconds after app launch, then recurs every 10 minutes.
         *   Also displays even if today's puzzle is already cleared.
         * - When `false`: Standard production 24-hour cycle targeting 7:00 PM.
         */
        const val TEST_MODE = true

        /** Cancels any active or scheduled reminders. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled reminder work")
        }

        /**
         * Schedules the reminder.
         * Uses [TEST_MODE] if enabled, otherwise schedules for 7:00 PM daily.
         */
        fun schedule(context: Context) {
            if (TEST_MODE) {
                scheduleTestInterval(
                    context = context,
                    delayMinutes = 10,
                    initialDelaySeconds = 10,
                    policy = ExistingWorkPolicy.KEEP
                )
            } else {
                scheduleProduction(context)
            }
        }

        private fun scheduleTestInterval(
            context: Context,
            delayMinutes: Long = 10,
            initialDelaySeconds: Long? = null,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
        ) {
            val builder = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            if (initialDelaySeconds != null) {
                builder.setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
            } else {
                builder.setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            }

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                policy,
                builder.build()
            )
            Log.d(TAG, "Enqueued test reminder work (delay: ${initialDelaySeconds?.let { "$it seconds" } ?: "$delayMinutes minutes"})")
        }

        private fun scheduleProduction(context: Context) {
            val now = LocalDateTime.now()
            var target = now.with(LocalTime.of(TARGET_HOUR, 0, 0))
            if (now.isAfter(target)) {
                target = target.plusDays(1)
            }
            val initialDelayMinutes = Duration.between(now, target).toMinutes().coerceAtLeast(0)

            val workRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(
                24, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            )
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
