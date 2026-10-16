package com.arrowescape.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.arrowescape.BuildConfig
import com.arrowescape.analytics.AdFormat
import com.arrowescape.analytics.AnalyticsTracker
import com.arrowescape.analytics.Events
import com.arrowescape.analytics.NoOpAnalyticsTracker

/**
 * Abstraction for ad operations.
 */
interface AdManager {
    fun initialize(context: Context)
    fun loadRewardedAd(context: Context)
    fun showRewardedAd(activity: Activity, onRewarded: () -> Unit, onFailed: () -> Unit)
    fun isRewardedAdReady(): Boolean
    fun loadInterstitialAd(context: Context)
    fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit)
    fun isInterstitialAdReady(): Boolean
}

/**
 * Real AdMob implementation.
 *
 * Every SDK callback is mirrored to [analytics]. Ads are the revenue path, so
 * the funnel - requested, loaded, shown, impression, clicked, dismissed, plus
 * every failure in between - is the one thing worth logging in full detail.
 * Volume control is the tracker's job, not this class's: see
 * `RateLimitedAnalyticsTracker`.
 */
class AdManagerImpl(
    private val analytics: AnalyticsTracker = NoOpAnalyticsTracker
) : AdManager {

    companion object {
        private const val TAG = "AdManager"
    }

    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingRewarded = false
    private var isLoadingInterstitial = false

    override fun initialize(context: Context) {
        MobileAds.initialize(context) { initStatus ->
            Log.d(TAG, "AdMob initialized: $initStatus")
        }
    }

    // ---- Rewarded --------------------------------------------------------

    override fun loadRewardedAd(context: Context) {
        if (isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true
        analytics.log(Events.adLoadRequested(AdFormat.REWARDED))

        RewardedAd.load(
            context,
            BuildConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded")
                    analytics.log(Events.adLoaded(AdFormat.REWARDED))
                    ad.onPaidEventListener = paidListener(AdFormat.REWARDED)
                    rewardedAd = ad
                    isLoadingRewarded = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                    analytics.log(Events.adLoadFailed(AdFormat.REWARDED, error.code, error.message))
                    rewardedAd = null
                    isLoadingRewarded = false
                }
            }
        )
    }

    override fun showRewardedAd(activity: Activity, onRewarded: () -> Unit, onFailed: () -> Unit) {
        analytics.log(Events.adShowRequested(AdFormat.REWARDED))

        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded ad not ready")
            analytics.log(Events.adNotReady(AdFormat.REWARDED))
            onFailed()
            return
        }

        ad.fullScreenContentCallback = contentCallback(
            format = AdFormat.REWARDED,
            onDismissed = {
                rewardedAd = null
                loadRewardedAd(activity) // Preload next
            },
            onShowFailed = {
                rewardedAd = null
                onFailed()
            }
        )

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            analytics.log(
                Events.adRewardEarned(AdFormat.REWARDED, rewardItem.type, rewardItem.amount)
            )
            onRewarded()
        }
    }

    override fun isRewardedAdReady(): Boolean = rewardedAd != null

    // ---- Interstitial ----------------------------------------------------

    override fun loadInterstitialAd(context: Context) {
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true
        analytics.log(Events.adLoadRequested(AdFormat.INTERSTITIAL))

        InterstitialAd.load(
            context,
            BuildConfig.INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    analytics.log(Events.adLoaded(AdFormat.INTERSTITIAL))
                    ad.onPaidEventListener = paidListener(AdFormat.INTERSTITIAL)
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${error.message}")
                    analytics.log(
                        Events.adLoadFailed(AdFormat.INTERSTITIAL, error.code, error.message)
                    )
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    override fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit) {
        analytics.log(Events.adShowRequested(AdFormat.INTERSTITIAL))

        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "Interstitial ad not ready")
            analytics.log(Events.adNotReady(AdFormat.INTERSTITIAL))
            onDismissed()
            return
        }

        ad.fullScreenContentCallback = contentCallback(
            format = AdFormat.INTERSTITIAL,
            onDismissed = {
                interstitialAd = null
                loadInterstitialAd(activity) // Preload next
                onDismissed()
            },
            onShowFailed = {
                interstitialAd = null
                onDismissed()
            }
        )

        ad.show(activity)
    }

    override fun isInterstitialAdReady(): Boolean = interstitialAd != null

    // ---- Shared callback plumbing ----------------------------------------

    /**
     * The full-screen callbacks are identical for both formats apart from the
     * cleanup each one needs, so the logging lives here once. Rewarded and
     * interstitial ads share no SDK supertype, which is the only reason the
     * load paths above are still written out twice.
     */
    private fun contentCallback(
        format: AdFormat,
        onDismissed: () -> Unit,
        onShowFailed: () -> Unit
    ) = object : FullScreenContentCallback() {

        override fun onAdShowedFullScreenContent() {
            Log.d(TAG, "${format.reportingName} ad showed")
        }

        override fun onAdImpression() {
            analytics.log(Events.adImpression(format))
        }

        override fun onAdClicked() {
            analytics.log(Events.adClicked(format))
        }

        override fun onAdDismissedFullScreenContent() {
            Log.d(TAG, "${format.reportingName} ad dismissed")
            analytics.log(Events.adDismissed(format))
            onDismissed()
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            Log.e(TAG, "${format.reportingName} ad failed to show: ${error.message}")
            analytics.log(Events.adShowFailed(format, error.code, error.message))
            onShowFailed()
        }
    }

    /** Revenue is only observable here, so it is never rate limited. */
    private fun paidListener(format: AdFormat) = OnPaidEventListener { adValue ->
        analytics.log(
            Events.adRevenue(
                format = format,
                valueMicros = adValue.valueMicros,
                currency = adValue.currencyCode,
                precision = adValue.precisionType
            )
        )
    }
}

/**
 * No-op implementation for testing or ad-free builds.
 */
class NoOpAdManager : AdManager {
    override fun initialize(context: Context) {}
    override fun loadRewardedAd(context: Context) {}
    override fun showRewardedAd(activity: Activity, onRewarded: () -> Unit, onFailed: () -> Unit) {
        onFailed()
    }
    override fun isRewardedAdReady(): Boolean = false
    override fun loadInterstitialAd(context: Context) {}
    override fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit) {
        onDismissed()
    }
    override fun isInterstitialAdReady(): Boolean = false
}
