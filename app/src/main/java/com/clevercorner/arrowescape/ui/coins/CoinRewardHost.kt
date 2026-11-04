package com.clevercorner.arrowescape.ui.coins

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.economy.CoinBank
import com.clevercorner.arrowescape.economy.CoinGrant
import com.clevercorner.arrowescape.economy.CoinSource
import kotlinx.coroutines.delay

/** How long one reward owns the screen, start to finish. */
private const val BURST_MS = 1000
private const val HOLD_MS = 900L

/**
 * The app's single "you earned coins" announcement.
 *
 * Mounted once, at the root, over every screen - because a reward can be
 * settled while the player is anywhere (finishing a daily, finishing a
 * chapter, opening the app on day seven), and a toast that only exists on
 * Home would miss most of them.
 *
 * It drains [CoinBank.pending] one grant at a time: two landing together
 * (the last chapter of a journey *and* a streak tier) are two separate
 * pieces of good news, and stacking them would show neither. A banner at the
 * top rather than a card in the middle, so it can announce a daily reward
 * without sitting on top of the daily's own "Solved" card.
 */
@Composable
fun CoinRewardHost(bank: CoinBank, modifier: Modifier = Modifier) {
    val queue by bank.pending.collectAsState()
    val grant = queue.firstOrNull()
    val burst = remember { Animatable(0f) }

    // The card animates *out* after its grant has left the queue, and an exit
    // animation still needs something to draw. Remembering the last one is
    // the whole trick.
    var shown by remember { mutableStateOf<CoinGrant?>(null) }
    if (grant != null) shown = grant

    LaunchedEffect(grant) {
        val current = grant ?: return@LaunchedEffect
        burst.snapTo(0f)
        burst.animateTo(1f, tween(BURST_MS))
        delay(HOLD_MS)
        bank.acknowledge(current)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // Wrapped tight around the card so the coins fly from the banner
        // itself rather than from the middle of the screen.
        Box(contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = grant != null,
                enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(320, easing = EaseOutBack)),
                exit = fadeOut(tween(220)) + scaleOut(targetScale = 1.1f, animationSpec = tween(220))
            ) {
                shown?.let { RewardCard(it) }
            }

            CoinBurst(progress = burst.value)
        }
    }
}

@Composable
private fun RewardCard(grant: CoinGrant) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CoinIcon(size = 40.dp, flipping = true)

            Column {
                Text(
                    text = stringResource(R.string.coin_amount_gained, grant.amount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = grant.reasonText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One sentence saying why. Lives next to the overlay rather than on
 * [CoinSource] itself so the economy stays free of string resources.
 */
@Composable
fun CoinGrant.reasonText(): String = when (source) {
    CoinSource.WELCOME -> stringResource(R.string.coin_reason_welcome)
    CoinSource.DAILY -> stringResource(R.string.coin_reason_daily)
    CoinSource.STREAK -> stringResource(R.string.coin_reason_streak, detail)
    CoinSource.CHAPTER -> stringResource(R.string.coin_reason_chapter, detail)
    CoinSource.REFERRAL -> stringResource(R.string.coin_reason_referral)
}
