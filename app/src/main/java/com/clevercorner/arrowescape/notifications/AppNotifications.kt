package com.clevercorner.arrowescape.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager as SystemNotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.clevercorner.arrowescape.R

/**
 * The one thing this stack knows how to do: put a title and a body on
 * screen as a notification, once a channel exists and permission is
 * granted.
 *
 * Deliberately ignorant of *why* a notification is being shown - deciding
 * that is a business rule, and belongs to a use case like
 * [DailyReminderUseCase], not here. Named `AppNotifications` rather than
 * `NotificationManager` to stay out of the way of
 * [android.app.NotificationManager], which this class wraps.
 */
interface AppNotifications {
    /** Idempotent and cheap - safe to call on every app start. */
    fun ensureChannel(context: Context)

    /** Always true below API 33, where this was never a runtime permission. */
    fun hasPermission(context: Context): Boolean

    /** Quietly does nothing if [hasPermission] is false, rather than throwing. */
    fun show(context: Context, notificationId: Int, title: String, body: String)

    companion object {
        /** One channel for now - a game this size has no reason to let players mute half of it. */
        const val CHANNEL_ID_GENERAL = "general"
    }
}

/** Real implementation, backed by the framework's own [SystemNotificationManager]. */
class SystemAppNotifications : AppNotifications {

    override fun ensureChannel(context: Context) {
        // Channels did not exist before Android 8; there is nothing to create.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            AppNotifications.CHANNEL_ID_GENERAL,
            context.getString(R.string.notification_channel_general_name),
            SystemNotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_general_description)
        }
        context.getSystemService(SystemNotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    override fun hasPermission(context: Context): Boolean {
        // POST_NOTIFICATIONS only became a runtime permission on API 33;
        // below that, holding the manifest entry is already enough.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Quietly does nothing if [hasPermission] is false, rather than throwing. */
    @android.annotation.SuppressLint("MissingPermission") // guarded by hasPermission() at the call site
    override fun show(context: Context, notificationId: Int, title: String, body: String) {
        if (!hasPermission(context)) return

        val notification = NotificationCompat.Builder(context, AppNotifications.CHANNEL_ID_GENERAL)
            // The launcher's own arrow mark: a simple solid shape on a
            // transparent background, which is exactly what a status-bar
            // icon needs (the OS silhouettes it). A dedicated monochrome
            // asset would be the polished choice before shipping; this one
            // already satisfies the actual constraint.
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
