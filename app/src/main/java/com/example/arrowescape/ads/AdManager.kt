package com.example.arrowescape.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.example.arrowescape.BuildConfig

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
 */
class AdManagerImpl : AdManager {

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

    override fun loadRewardedAd(context: Context) {
        if (isLoadingRewarded || rewardedAd != null) return
        isLoadingRewarded = true

        val adUnitId = BuildConfig.REWARDED_AD_UNIT_ID

        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded")
                    rewardedAd = ad
                    isLoadingRewarded = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                    rewardedAd = null
                    isLoadingRewarded = false
                }
            }
        )
    }

    override fun showRewardedAd(activity: Activity, onRewarded: () -> Unit, onFailed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded ad not ready")
            onFailed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                rewardedAd = null
                loadRewardedAd(activity) // Preload next
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Rewarded ad failed to show: ${error.message}")
                rewardedAd = null
                onFailed()
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onRewarded()
        }
    }

    override fun isRewardedAdReady(): Boolean = rewardedAd != null

    override fun loadInterstitialAd(context: Context) {
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true

        val adUnitId = BuildConfig.INTERSTITIAL_AD_UNIT_ID

        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    interstitialAd = ad
                    isLoadingInterstitial = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${error.message}")
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    override fun showInterstitialAd(activity: Activity, onDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            Log.w(TAG, "Interstitial ad not ready")
            onDismissed()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                interstitialAd = null
                loadInterstitialAd(activity) // Preload next
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Interstitial ad failed to show: ${error.message}")
                interstitialAd = null
                onDismissed()
            }
        }

        ad.show(activity)
    }

    override fun isInterstitialAdReady(): Boolean = interstitialAd != null
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
