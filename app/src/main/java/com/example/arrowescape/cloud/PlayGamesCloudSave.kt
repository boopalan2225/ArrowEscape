package com.example.arrowescape.cloud

import android.app.Activity
import android.util.Log
import com.example.arrowescape.data.GameMode
import com.example.arrowescape.data.GamePreferences.ModeProgress
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * Cloud save backed by Google Play Games Services v2: Sign-In for identity,
 * Snapshots for the actual save data.
 *
 * Everything here is best-effort. A player with no Google account, a device
 * with no Play Services, or an app not yet configured in the Play Console
 * (see the GAMES_APP_ID comment in build.gradle.kts) should see nothing worse
 * than "cloud save isn't available" - never a crash, and never a blocked game.
 */
class PlayGamesCloudSave : CloudSave {

    companion object {
        private const val TAG = "PlayGamesCloudSave"

        /** One save slot is all this game needs - there is nothing per-player beyond progress. */
        private const val SNAPSHOT_NAME = "arrow_escape_progress"
    }

    private val _account = MutableStateFlow<CloudAccount?>(null)
    override val account: StateFlow<CloudAccount?> = _account

    private var activity: Activity? = null
    private var sdkInitialized = false

    override fun initialize(activity: Activity) {
        this.activity = activity
        if (sdkInitialized) return
        runCatching {
            PlayGamesSdk.initialize(activity)
            sdkInitialized = true
        }.onFailure {
            Log.w(TAG, "Play Games SDK unavailable - cloud save disabled", it)
        }
    }

    override fun signInSilently() {
        val act = activity ?: return
        if (!sdkInitialized) return
        runCatching {
            PlayGames.getGamesSignInClient(act).isAuthenticated
                .addOnCompleteListener { task ->
                    val authenticated = task.isSuccessful && task.result?.isAuthenticated == true
                    if (authenticated) onAuthenticated(act) else _account.value = null
                }
        }.onFailure { Log.w(TAG, "Silent sign-in failed", it) }
    }

    override fun signIn(activity: Activity) {
        if (!sdkInitialized) return
        runCatching {
            PlayGames.getGamesSignInClient(activity).signIn()
                .addOnCompleteListener { task ->
                    val authenticated = task.isSuccessful && task.result?.isAuthenticated == true
                    if (authenticated) onAuthenticated(activity) else _account.value = null
                }
        }.onFailure { Log.w(TAG, "Sign-in failed", it) }
    }

    override fun signOut() {
        // Play Games Sign-In has no app-triggered sign-out - a player manages that
        // from their Google account, same as any other Play Games title. We can
        // only stop treating this session as signed in.
        _account.value = null
    }

    private fun onAuthenticated(activity: Activity) {
        runCatching {
            PlayGames.getPlayersClient(activity).currentPlayer
                .addOnCompleteListener { task ->
                    val name = task.result?.displayName ?: "Player"
                    _account.value = CloudAccount(name)
                }
        }.onFailure {
            // Signed in, but could not fetch a name to show - still signed in.
            _account.value = CloudAccount("Player")
        }
    }

    override suspend fun pull(): Map<GameMode, ModeProgress>? {
        val act = activity ?: return null
        if (_account.value == null) return null
        return runCatching {
            val snapshot = openResolved(act)
            val bytes = snapshot.snapshotContents.readFully()
            ProgressSnapshot.decode(String(bytes, Charsets.UTF_8))
        }.onFailure {
            Log.w(TAG, "Cloud pull failed - continuing with local progress only", it)
        }.getOrNull()
    }

    override suspend fun push(progress: Map<GameMode, ModeProgress>) {
        val act = activity ?: return
        if (_account.value == null) return
        runCatching {
            val snapshot = openResolved(act)
            val encoded = ProgressSnapshot.encode(progress).toByteArray(Charsets.UTF_8)
            snapshot.snapshotContents.writeBytes(encoded)
            val change = SnapshotMetadataChange.Builder().build()
            PlayGames.getSnapshotsClient(act).commitAndClose(snapshot, change).await()
        }.onFailure {
            Log.w(TAG, "Cloud push failed - progress stays local for now", it)
        }
    }

    /**
     * Opens the save slot, resolving any conflict by merging rather than
     * picking a side - a conflict means two devices both made progress, and
     * [ProgressSnapshot.merge] is exactly "neither loses anything".
     */
    private suspend fun openResolved(activity: Activity): Snapshot {
        val client = PlayGames.getSnapshotsClient(activity)
        var result = client.open(SNAPSHOT_NAME, true, SnapshotsClient.RESOLUTION_POLICY_MANUAL).await()

        while (result.isConflict) {
            val conflict = checkNotNull(result.conflict)
            val merged = mergeConflict(conflict.snapshot, conflict.conflictingSnapshot)
            val keep = conflict.snapshot
            keep.snapshotContents.writeBytes(merged)
            result = client.resolveConflict(conflict.conflictId, keep).await()
        }

        return checkNotNull(result.data) { "Snapshot open succeeded with no data and no conflict" }
    }

    private fun mergeConflict(a: Snapshot?, b: Snapshot?): ByteArray {
        val progressA = a?.snapshotContents?.readFully()?.let { String(it, Charsets.UTF_8) }
            ?.let(ProgressSnapshot::decode).orEmpty()
        val progressB = b?.snapshotContents?.readFully()?.let { String(it, Charsets.UTF_8) }
            ?.let(ProgressSnapshot::decode).orEmpty()
        return ProgressSnapshot.encode(ProgressSnapshot.merge(progressA, progressB))
            .toByteArray(Charsets.UTF_8)
    }
}
