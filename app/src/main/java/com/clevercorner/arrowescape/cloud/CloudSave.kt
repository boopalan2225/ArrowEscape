package com.clevercorner.arrowescape.cloud

import android.app.Activity
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.data.GamePreferences.ModeProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The signed-in player, as much as the UI needs to know about them. */
data class CloudAccount(val displayName: String)

/**
 * Cloud save over Google Play Games: who is signed in, and moving progress
 * to and from their account.
 *
 * An interface (rather than one concrete class) for the same reason
 * [com.clevercorner.arrowescape.ads.AdManager] is one: Compose previews and any
 * future tests should never need a real Google account or network access.
 */
interface CloudSave {

    /** Null when signed out. Updated by [signInSilently] and [signIn]. */
    val account: StateFlow<CloudAccount?>

    /**
     * Call once, early, with the app's one long-lived Activity. Play Games'
     * sign-in clients are Activity-scoped, not Context-scoped - there is
     * nothing to initialize without one.
     */
    fun initialize(activity: Activity)

    /**
     * Tries to sign in using an account already associated with this device,
     * with no UI. Safe to call on every app start - it is a no-op if nothing
     * is available, and never throws.
     */
    fun signInSilently()

    /** Sign-in with UI, for a player [signInSilently] could not pick up. */
    fun signIn(activity: Activity)

    fun signOut()

    /**
     * The signed-in player's saved progress, or null if there is none yet or
     * nobody is signed in. Never throws - cloud trouble should degrade to
     * "play locally", not crash the game.
     */
    suspend fun pull(): Map<GameMode, ModeProgress>?

    /** Best-effort upload. Failures are swallowed; there is always a local copy. */
    suspend fun push(progress: Map<GameMode, ModeProgress>)
}

/** For previews and anywhere a real Google account would be noise. */
class NoOpCloudSave : CloudSave {
    override val account: StateFlow<CloudAccount?> = MutableStateFlow(null)
    override fun initialize(activity: Activity) {}
    override fun signInSilently() {}
    override fun signIn(activity: Activity) {}
    override fun signOut() {}
    override suspend fun pull(): Map<GameMode, ModeProgress>? = null
    override suspend fun push(progress: Map<GameMode, ModeProgress>) {}
}
