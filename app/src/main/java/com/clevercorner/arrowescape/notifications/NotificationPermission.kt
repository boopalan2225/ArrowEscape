package com.clevercorner.arrowescape.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * A launcher for the one runtime permission this stack needs.
 *
 * Not called from anywhere yet - deliberately. Firing a permission prompt
 * the instant a screen opens is the kind of thing that gets an app a
 * one-star review; that decision belongs to whichever screen first has a
 * genuine reason to ask (a "remind me daily" toggle is the natural home),
 * not to this file. This exists so that screen does not also need to know
 * API 33 is where the permission became runtime-requestable at all.
 */
@Composable
fun rememberNotificationPermissionLauncher(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult
    )
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Nothing to ask for below API 33 - the manifest entry is enough.
                onResult(true)
            }
        }
    }
}
