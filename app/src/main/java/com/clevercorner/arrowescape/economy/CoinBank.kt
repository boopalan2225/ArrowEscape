package com.clevercorner.arrowescape.economy

import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.data.GamePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The teller: the one object that moves coins.
 *
 * Nothing else in the app adds to a balance. Screens and ViewModels get on
 * with the game; whenever the saved state changes, the bank re-reads it, asks
 * [CoinRules] what is owed and settles up. So "solving a daily pays 8 coins"
 * needed no edit to the daily screen, the game engine or either ViewModel -
 * they already record the solve, and recording the solve *is* the payment.
 *
 * Every settled grant is announced on [rewards], which is the only reason the
 * UI knows to throw coins across the screen.
 */
class CoinBank(
    private val preferences: GamePreferences,
    /**
     * How many chapters of [GameMode]'s journey a progress record has
     * finished. Injected because journey lengths are the Journey Map's data,
     * and an economy that imports a screen to count chapters has its layers
     * upside down.
     */
    private val chaptersCleared: (GameMode, GamePreferences.ModeProgress) -> Int
) {

    /**
     * Rewards earned but not yet *shown*, oldest first.
     *
     * A queue rather than an event stream, and deliberately so: the welcome
     * bonus settles within milliseconds of launch, comfortably before any
     * screen has composed, and an event shouted into an empty room is a
     * reward the player never learns they were given. State waits.
     */
    private val _pending = MutableStateFlow<List<CoinGrant>>(emptyList())
    val pending: StateFlow<List<CoinGrant>> = _pending

    /** Called by the overlay once a reward has had its moment on screen. */
    fun acknowledge(grant: CoinGrant) {
        _pending.update { queue -> queue.filterNot { it.id == grant.id } }
    }

    /** One settle at a time; two concurrent bills would pay the same grant twice. */
    private val till = Mutex()

    /**
     * Watches saved progress and settles whatever it owes, forever.
     *
     * Settling writes preferences, which re-emits, which settles again - and
     * finds nothing owed, because the ledger it just wrote says so. The loop
     * is self-terminating by construction rather than by a flag.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            preferences.prefs.filterNotNull().collect { settle(it) }
        }
    }

    private suspend fun settle(prefs: GamePreferences.Prefs) {
        val wallet = prefs.wallet
        val owed = CoinRules.owed(
            wallet = wallet,
            chaptersCleared = GameMode.entries.associateWith {
                chaptersCleared(it, prefs.progressOf(it))
            },
            daily = prefs.daily
        )
        if (owed.isEmpty()) return

        // The first settle of a player's life writes their history off
        // instead of paying it. A veteran with ninety daily solves and every
        // chapter beaten earned those before coins existed; paying it out
        // retroactively would hand them the entire shop on launch day and
        // make the currency meaningless in the same breath. They keep the
        // welcome bonus, same as everybody.
        val openingLedger = wallet.isUntouched
        val (credited, acknowledged) =
            if (openingLedger) owed.partition { it.source == CoinSource.WELCOME }
            else Pair(owed, emptyList())

        till.withLock {
            preferences.creditCoins(credited, acknowledged)
        }
        if (credited.isNotEmpty()) _pending.update { it + credited }
    }

    /**
     * Buys [themeId]. False means the purse was short or the theme was
     * already owned - the caller animates accordingly.
     *
     * The price is not a parameter: it depends on how many themes are
     * already owned, and the only place that knows for certain is inside the
     * write transaction.
     */
    suspend fun buyTheme(themeId: String): Boolean =
        till.withLock { preferences.purchaseTheme(themeId, CoinRules::unlockPrice) != null }

    /** This player's own invite code, minted on first ask and kept forever. */
    suspend fun myReferralCode(): String =
        preferences.referralCode(ReferralCode.generate())

    /**
     * Redeems a friend's invite code.
     *
     * The honest limit of a game with no server: this pays the *invitee*
     * immediately, because that is the half we can verify locally. Paying the
     * inviter needs someone neutral to witness the redeem, so when cloud save
     * grows a social side, it calls [creditReferral] - the payout path is
     * already here and already one-time-only.
     */
    suspend fun redeem(rawCode: String, wallet: Wallet): RedeemResult {
        val code = ReferralCode.normalise(rawCode)
        return when {
            !ReferralCode.isValid(code) -> RedeemResult.MALFORMED
            code == wallet.referralCode -> RedeemResult.OWN_CODE
            wallet.hasPaid(CoinRules.REFERRAL_ID) -> RedeemResult.ALREADY_REDEEMED
            else -> {
                creditReferral()
                RedeemResult.CREDITED
            }
        }
    }

    private suspend fun creditReferral() {
        val grant = CoinGrant(CoinRules.REFERRAL_ID, CoinRules.REFERRAL, CoinSource.REFERRAL)
        till.withLock { preferences.creditCoins(listOf(grant)) }
        _pending.update { it + grant }
    }
}
