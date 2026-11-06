package com.clevercorner.arrowescape.ui.coins

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.clevercorner.arrowescape.ui.components.readableWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.economy.CoinGrant
import com.clevercorner.arrowescape.economy.CoinRules
import com.clevercorner.arrowescape.economy.RedeemResult
import com.clevercorner.arrowescape.economy.Wallet
import com.clevercorner.arrowescape.game.daily.DailyProgress
import com.clevercorner.arrowescape.ui.components.GlyphIcon

/**
 * The purse, and the only page that explains it.
 *
 * Every way of earning is listed with what the player has actually done
 * against it, because a currency whose rules are a mystery is a currency
 * players assume is rigged. Nothing here is a grind meter: the four honest
 * ways to earn are showing up daily, keeping a run alive, finishing chapters
 * and bringing a friend.
 */
@Composable
fun CoinScreen(
    wallet: Wallet,
    daily: DailyProgress,
    chaptersCleared: Int,
    /** Today's "watch an ad" offer, or null once the allowance is spent. */
    adOffer: CoinGrant?,
    adReady: Boolean,
    onWatchAd: (CoinGrant) -> Unit,
    ensureReferralCode: suspend () -> String,
    onRedeem: suspend (String) -> RedeemResult,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CoinHeader(onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .readableWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BalanceBanner(
                balance = wallet.balance,
                lifetime = wallet.lifetimeEarned,
                spent = wallet.lifetimeSpent
            )

            Spacer(Modifier.height(20.dp))

            AdOfferCard(offer = adOffer, adReady = adReady, onWatchAd = onWatchAd)

            Spacer(Modifier.height(20.dp))

            EarnList(wallet = wallet, daily = daily, chaptersCleared = chaptersCleared)

            Spacer(Modifier.height(20.dp))

            ThemeShelf(wallet = wallet)

            Spacer(Modifier.height(20.dp))

            ReferralCard(
                wallet = wallet,
                ensureReferralCode = ensureReferralCode,
                onRedeem = onRedeem
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CoinHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            text = stringResource(R.string.coins_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * The balance, large and slowly glinting.
 *
 * The sheen is an infinite transition rather than a one-shot: a wallet is a
 * place you *arrive* at, often mid-animation, and a one-shot shine would be
 * over before most visits had begun.
 */
@Composable
private fun BalanceBanner(balance: Int, lifetime: Int, spent: Int) {
    val shimmer = rememberInfiniteTransition(label = "walletShine")
    val sweep by shimmer.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val shown by animateIntAsState(
        targetValue = balance,
        animationSpec = tween(800, easing = LinearEasing),
        label = "walletBalance"
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            // The glint: a narrow translucent band travelling across the card.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            (sweep - 0.12f).coerceIn(0f, 1f) to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            sweep.coerceIn(0f, 1f) to CoinArt.Shine.copy(alpha = 0.22f),
                            (sweep + 0.12f).coerceIn(0f, 1f) to MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CoinIcon(size = 38.dp, flipping = true)
                    Text(
                        text = "$shown",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Earned *and* spent, because the balance alone makes a
                // player who just bought a theme look like they have done
                // nothing. The ledger is the achievement; the purse is the
                // leftovers.
                Text(
                    text = stringResource(R.string.coins_lifetime, lifetime, spent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The fast lane: coins for thirty seconds of advertising.
 *
 * Given its own card above the earn list rather than a row inside it,
 * because it is the one offer on this screen a player can act on *right
 * now* - everything else describes how to earn tomorrow. That is the line
 * being drawn: ads are never required for anything, and never hidden
 * either.
 *
 * When the allowance is spent the card stays put and goes quiet. An offer
 * that vanishes teaches nobody that it will be back tomorrow.
 */
@Composable
private fun AdOfferCard(offer: CoinGrant?, adReady: Boolean, onWatchAd: (CoinGrant) -> Unit) {
    val available = offer != null && adReady

    // A slow shimmer while there is something to claim, still when there is
    // not - motion is the honest signal for "this is actionable".
    val pulse = rememberInfiniteTransition(label = "adOffer")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "adGlow"
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (available) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(CoinArt.Face.copy(alpha = if (available) glow else 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(glyph = "\u25B6", fontSize = 18.sp, color = CoinArt.Rim)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.coins_ad_title, CoinRules.AD_REWARD),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        offer == null -> stringResource(R.string.coins_ad_spent, CoinRules.ADS_PER_DAY)
                        !adReady -> stringResource(R.string.coins_ad_not_ready)
                        else -> stringResource(R.string.coins_ad_left, offer.detail + 1)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { offer?.let(onWatchAd) },
                enabled = available,
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(R.string.coins_ad_button), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * What the coins are *for*, and the ladder they are spent on.
 *
 * Shown here rather than only in Settings because a tiered price is a
 * surprise waiting to happen: a player who saves 150 for their first look
 * deserves to know the second one costs 300 before they get there, not
 * after.
 */
@Composable
private fun ThemeShelf(wallet: Wallet) {
    val owned = wallet.unlockedThemes.size

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.coins_shelf_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.coins_shelf_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoinRules.UNLOCK_PRICES.forEachIndexed { rung, price ->
                    val bought = rung < owned
                    // The rung you are standing on is the only one lit: the
                    // ones below are paid for, the ones above are not yours
                    // to worry about yet.
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = if (rung == owned) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.coins_shelf_rung, rung + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (bought) "\u2713" else "$price",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One way to earn: what it pays, and how the player is doing at it. */
private data class EarnRow(
    val glyph: String,
    val title: String,
    val detail: String,
    val reward: String,
    /** Null for the ways that pay again and again. */
    val progress: Float? = null,
    val done: Boolean = false
)

@Composable
private fun EarnList(wallet: Wallet, daily: DailyProgress, chaptersCleared: Int) {
    // The next tier this wallet has never been paid for - asked of the
    // ledger, not of the streak, so breaking a run does not re-offer a
    // reward already banked. Progress towards it is the *current* run of
    // days, which is the thing the player can actually act on today.
    val nextTier = CoinRules.nextStreakTier(wallet)

    val rows = listOf(
        EarnRow(
            glyph = "\uD83C\uDF81",
            title = stringResource(R.string.coin_reason_welcome),
            detail = stringResource(R.string.coins_earn_welcome_detail),
            reward = "+${CoinRules.WELCOME}",
            done = wallet.hasPaid(CoinRules.WELCOME_ID)
        ),
        EarnRow(
            glyph = "\uD83D\uDCC5",
            title = stringResource(R.string.coins_earn_daily_title),
            detail = stringResource(R.string.coins_earn_daily_detail, daily.cleared.size),
            reward = "+${CoinRules.DAILY_SOLVE}\u2026${CoinRules.DAILY_SOLVE + CoinRules.DAILY_FLAWLESS_BONUS}"
        ),
        EarnRow(
            glyph = "\uD83D\uDD25",
            title = stringResource(R.string.coins_earn_streak_title),
            detail = if (nextTier == null) {
                stringResource(R.string.coins_earn_streak_maxed)
            } else {
                stringResource(R.string.coins_earn_streak_detail, daily.streak, nextTier)
            },
            reward = nextTier?.let { "+${CoinRules.STREAK_TIERS[it]}" } ?: "\u2713",
            progress = nextTier?.let { (daily.streak.toFloat() / it).coerceIn(0f, 1f) },
            done = nextTier == null
        ),
        EarnRow(
            glyph = "\uD83D\uDDFA",
            title = stringResource(R.string.coins_earn_chapter_title),
            detail = stringResource(R.string.coins_earn_chapter_detail, chaptersCleared),
            reward = "+${CoinRules.CHAPTER}"
        ),
        EarnRow(
            glyph = "\uD83D\uDC65",
            title = stringResource(R.string.coins_earn_referral_title),
            detail = stringResource(R.string.coins_earn_referral_detail),
            reward = "+${CoinRules.REFERRAL}",
            done = wallet.hasPaid(CoinRules.REFERRAL_ID)
        )
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.coins_earn_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            rows.forEach { EarnItem(it) }
        }
    }
}

@Composable
private fun EarnItem(row: EarnRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph = row.glyph, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = row.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            row.progress?.let { fraction ->
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                )
            }
        }

        // Claimed one-offs keep their place in the list but stop shouting a
        // price at a player who can never collect it again.
        Text(
            text = if (row.done) "\u2713" else row.reward,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (row.done) MaterialTheme.colorScheme.onSurfaceVariant else CoinArt.Shadow
        )
    }
}
