package com.clevercorner.arrowescape.cloud

import android.util.Log
import com.clevercorner.arrowescape.data.GamePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The only place that decides *when* local state and cloud state talk to
 * each other. Neither [GamePreferences] nor the game ViewModels know this
 * exists - progress just happens to get backed up.
 *
 * Signed out, this object does nothing at all and the game is untouched:
 * every puzzle, coin and streak still works, just on one device. That is the
 * deal, and it is checked in exactly one place - the `account.value != null`
 * below - rather than being a condition sprinkled through the app.
 */
class CloudSyncCoordinator(
    private val cloudSave: CloudSave,
    private val preferences: GamePreferences
) {
    companion object {
        private const val TAG = "CloudSync"

        /** Levels complete in bursts (hint, tap, tap...); no need to push every one. */
        private const val PUSH_DEBOUNCE_MS = 3000L
    }

    /**
     * Starts two independent watchers for the life of [scope] (the app's own
     * scope - this never stops while the app is running):
     *
     * 1. On sign-in: pull the cloud copy, merge it with whatever is on this
     *    device (never regressing either), save the result locally, and push
     *    it straight back - so the cloud reflects the merge too, not just
     *    whichever side happened to be bigger.
     * 2. Whenever local state changes while signed in: push it, debounced.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            cloudSave.account.filterNotNull().collectLatest {
                syncOnSignIn()
            }
        }

        scope.launch {
            preferences.prefs
                .filterNotNull()
                .map(CloudState::of)
                .distinctUntilChanged()
                .drop(1) // the initial emission is not a "change" worth pushing
                .debounce(PUSH_DEBOUNCE_MS)
                .collectLatest { state ->
                    if (cloudSave.account.value != null) {
                        cloudSave.push(state)
                    }
                }
        }
    }

    private suspend fun syncOnSignIn() {
        // Deliberately the nullable flow: merging against a defaulted Prefs
        // before disk has answered would offer the cloud an empty local side
        // and call it a merge.
        val localBeforeMerge = CloudState.of(preferences.prefs.filterNotNull().first())
        val remote = cloudSave.pull()

        if (remote == null) {
            Log.d(TAG, "No cloud save yet - this device's progress becomes the first one")
            cloudSave.push(localBeforeMerge)
            return
        }

        preferences.applyCloudState(ProgressSnapshot.merge(localBeforeMerge, remote))
        // Read back the merged result and push it, so the cloud never keeps
        // holding the smaller side of the merge.
        cloudSave.push(CloudState.of(preferences.prefs.filterNotNull().first()))
    }
}
