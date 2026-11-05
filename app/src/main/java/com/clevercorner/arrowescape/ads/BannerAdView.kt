package com.clevercorner.arrowescape.ads

import android.app.Activity
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.clevercorner.arrowescape.analytics.AdFormat
import com.clevercorner.arrowescape.analytics.AnalyticsTracker
import com.clevercorner.arrowescape.analytics.Events
import com.clevercorner.arrowescape.analytics.NoOpAnalyticsTracker
import kotlin.math.roundToInt

private const val TAG = "BannerAd"

/**
 * A full-width anchored adaptive banner, meant to live at the true bottom of
 * a screen - below every other control, same as the system nav bar it sits
 * just above.
 *
 * Deliberately not routed through [AdManager]: that interface exists for
 * rewarded/interstitial's shared "load once, show on demand, tell me when
 * it's done" shape. A banner has none of that - it is a [AdView] that loads
 * and refreshes itself once attached - so wrapping it in the same interface
 * would mean inventing methods ([AdManager] has no concept of "attach this
 * to a layout") purely to satisfy a shape it does not share. `AndroidView`
 * is the correct, idiomatic bridge for an SDK View like this one.
 */
@Composable
fun BannerAdView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    analytics: AnalyticsTracker = NoOpAnalyticsTracker
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val adSize = remember {
        val widthDp = (context.resources.displayMetrics.widthPixels / density.density).roundToInt()
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
    }

    val adView = remember {
        AdView(activity).apply {
            this.adUnitId = adUnitId
            setAdSize(adSize)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner loaded")
                    analytics.log(Events.adLoaded(AdFormat.BANNER))
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Banner failed to load: ${error.message}")
                    analytics.log(Events.adLoadFailed(AdFormat.BANNER, error.code, error.message))
                }

                override fun onAdImpression() {
                    analytics.log(Events.adImpression(AdFormat.BANNER))
                }

                override fun onAdClicked() {
                    analytics.log(Events.adClicked(AdFormat.BANNER))
                }
            }
        }
    }

    // AdView needs resume()/pause() mirrored to the host's own lifecycle so
    // it stops refreshing (and spending battery/network) while the screen
    // isn't visible, and destroy() exactly once when this leaves composition
    // for good - not on every recomposition, which is why the listener is
    // attached in DisposableEffect rather than inline above.
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        analytics.log(Events.adLoadRequested(AdFormat.BANNER))
        adView.loadAd(AdRequest.Builder().build())

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        factory = { FrameLayout(it).apply { addView(adView) } },
        modifier = modifier
            .fillMaxWidth()
            .height(adSize.height.dp)
    )
}
