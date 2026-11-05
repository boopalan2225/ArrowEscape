package com.clevercorner.arrowescape.notifications

import java.util.UUID

/**
 * Stands in for a real push-registration token (Firebase Cloud Messaging,
 * or whatever SDK eventually wins) until this app has a server worth
 * registering one with.
 *
 * A real token comes from a push SDK, is meaningful to a backend, and is
 * fetched with a suspending call that reaches the network. This one means
 * nothing to anyone, is minted entirely on-device, and [AppNotifications]
 * never sends it anywhere - it only exists so the rest of the notification
 * stack (see [NotificationTokenProvider]) has something shaped like a real
 * token to hold onto while there is nothing real to hold.
 *
 * The `local-` prefix is deliberate: the day a real push SDK is wired in,
 * grepping for it is how you find every place still holding the
 * placeholder.
 */
object NotificationToken {
    private const val PREFIX = "local-"

    /** A fresh token. Minted once per install and persisted forever - see [NotificationTokenProvider]. */
    fun generate(): String = PREFIX + UUID.randomUUID()

    /** True for a token this object minted, false for anything a real push SDK would issue. */
    fun isDummy(token: String): Boolean = token.startsWith(PREFIX)
}
