package com.clevercorner.arrowescape.ui.coins

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.clevercorner.arrowescape.ads.AdManager
import com.clevercorner.arrowescape.economy.CoinBank
import com.clevercorner.arrowescape.economy.CoinGrant
import kotlinx.coroutines.launch

/**
 * Shows a rewarded ad and, if the player sits through it, pays the offer.
 *
 * One helper for every "watch an ad for coins" button in the app, because
 * the sequence is subtle enough to get wrong twice: the grant is claimed in
 * the *reward* callback, never before and never on dismissal. An ad closed
 * after four seconds has not been watched, and paying optimistically would
 * quietly turn the fast lane into a free one.
 *
 * Callers are expected to disable their button when [AdManager.isRewardedAdReady]
 * is false - a button that silently does nothing is worse than one that is
 * visibly greyed out.
 */
@Composable
fun rememberCoinAdLauncher(adManager: AdManager, bank: CoinBank): (CoinGrant) -> Unit {
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()

    return remember(adManager, bank, activity, scope) {
        { offer ->
            activity?.let { host ->
                adManager.showRewardedAd(
                    activity = host,
                    onRewarded = { scope.launch { bank.creditAd(offer) } },
                    onFailed = { /* Nothing was watched, so nothing is owed. */ }
                )
            }
        }
    }
}
