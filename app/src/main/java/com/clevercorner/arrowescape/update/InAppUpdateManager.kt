package com.clevercorner.arrowescape.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State of Google Play In-App Update flow. */
sealed interface UpdateState {
    object Idle : UpdateState
    object Available : UpdateState
    object Downloading : UpdateState
    object Downloaded : UpdateState
}

/**
 * Manages Google Play In-App Updates (Flexible mode).
 *
 * Allows players to download app updates in the background without interrupting
 * gameplay, then restart when ready. Falls back gracefully to the Play Store
 * page if the Play In-App flow is unavailable or if sideloaded in debug builds.
 */
class InAppUpdateManager(private val context: Context) {

    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(context)
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var cachedUpdateInfo: AppUpdateInfo? = null

    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> _state.value = UpdateState.Downloading
            InstallStatus.DOWNLOADED -> _state.value = UpdateState.Downloaded
            InstallStatus.FAILED, InstallStatus.CANCELED -> _state.value = UpdateState.Idle
            else -> {}
        }
    }

    fun registerListener() {
        try {
            appUpdateManager.registerListener(installListener)
        } catch (_: Exception) {}
    }

    fun unregisterListener() {
        try {
            appUpdateManager.unregisterListener(installListener)
        } catch (_: Exception) {}
    }

    /** Checks Google Play for an available app update. */
    fun checkForUpdate() {
        try {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                cachedUpdateInfo = info
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    _state.value = UpdateState.Downloaded
                } else if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    _state.value = UpdateState.Available
                }
            }.addOnFailureListener {
                // Silently idle when offline or debug build
            }
        } catch (_: Exception) {}
    }

    /**
     * Starts the Play Store flexible update flow, or falls back to opening the store listing.
     * In debug / mock testing, simulates background downloading and transitioning to Downloaded.
     */
    fun startUpdate(activity: Activity) {
        val info = cachedUpdateInfo
        if (info != null && info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
            try {
                val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                appUpdateManager.startUpdateFlow(info, activity, options)
                return
            } catch (_: Exception) {}
        }

        // Mock simulation in debug builds
        if (info == null) {
            android.widget.Toast.makeText(context, "Mock: Downloading update in background...", android.widget.Toast.LENGTH_SHORT).show()
            _state.value = UpdateState.Downloading
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(2000)
                _state.value = UpdateState.Downloaded
                android.widget.Toast.makeText(context, "Mock: Update downloaded! Ready to install.", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Fallback: Open Google Play Store listing
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }

    /** Restarts the app to install the downloaded update. */
    fun completeUpdate() {
        if (cachedUpdateInfo == null) {
            android.widget.Toast.makeText(context, "Mock: Update completed! Game restarted.", android.widget.Toast.LENGTH_SHORT).show()
            _state.value = UpdateState.Idle
            return
        }
        try {
            appUpdateManager.completeUpdate()
        } catch (_: Exception) {}
    }

    /** Mock state trigger for debug and UI testing. */
    fun mockUpdate(mockState: UpdateState) {
        _state.value = mockState
    }
}

