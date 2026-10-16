package com.arrowescape.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay

/** Shows the store review flow. One method, so a fake is one line in a test. */
fun interface ReviewLauncher {
    /** [onFinished] runs however the flow ends - shown, skipped or failed. */
    fun launch(activity: Activity, onFinished: () -> Unit)
}

/**
 * The real thing: Play's in-app review sheet.
 *
 * Play quietly does nothing when the player has already reviewed or the
 * quota is spent, and reports success anyway - by design, so that apps cannot
 * detect and pester. We respect that: only a genuine error falls back to
 * opening the store listing.
 */
class PlayReviewLauncher : ReviewLauncher {

    override fun launch(activity: Activity, onFinished: () -> Unit) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow()
            .addOnCompleteListener { request ->
                if (request.isSuccessful) {
                    manager.launchReviewFlow(activity, request.result)
                        .addOnCompleteListener { onFinished() }
                } else {
                    openStoreListing(activity)
                    onFinished()
                }
            }
    }

    private fun openStoreListing(activity: Activity) {
        val uri = Uri.parse("market://details?id=${activity.packageName}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No Play Store on this device (emulator, sideload). Asking for a
            // review there is meaningless, so let it go quietly.
        }
    }
}

/**
 * How long the celebration gets before the sheet slides over it.
 *
 * Long enough for the confetti and the stars to land, short enough that the
 * player has not yet reached for "Next level".
 */
private const val CELEBRATION_GRACE_MS = 1_400L

/**
 * Watches [prompter] and shows the review flow when it asks.
 *
 * Both game screens call this with one line; the timing, the Activity lookup
 * and the "only once" bookkeeping live here rather than twice over there.
 */
@Composable
fun ReviewPromptEffect(
    prompter: ReviewPrompter,
    launcher: ReviewLauncher = remember { PlayReviewLauncher() }
) {
    val requested by prompter.requested.collectAsState()
    val activity = LocalContext.current as? Activity

    LaunchedEffect(requested) {
        if (!requested || activity == null) return@LaunchedEffect
        delay(CELEBRATION_GRACE_MS)
        launcher.launch(activity) { prompter.onRequestHandled() }
    }
}
