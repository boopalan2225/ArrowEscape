package com.clevercorner.arrowescape.notifications

import com.clevercorner.arrowescape.data.GamePreferences

/**
 * This install's address for push notifications.
 *
 * Abstracted mainly so a real implementation can drop in later without
 * touching anything that holds a reference to this interface: a Firebase
 * Cloud Messaging-backed version would ask the SDK for a token, which both
 * suspends and reaches the network - a real version of [getOrCreateToken]
 * would still be a one-liner, it would just be a *different* one-liner.
 */
interface NotificationTokenProvider {
    suspend fun getOrCreateToken(): String
}

/**
 * Mints and persists a [NotificationToken] entirely on-device - no push SDK,
 * no network call, nothing a server could ever see. Exists so the rest of
 * the app can depend on [NotificationTokenProvider] today and get a real
 * implementation later for free.
 */
class LocalNotificationTokenProvider(
    private val preferences: GamePreferences
) : NotificationTokenProvider {
    override suspend fun getOrCreateToken(): String =
        preferences.notificationToken(NotificationToken.generate())
}
