package com.clevercorner.arrowescape.economy

/**
 * Where a coin came from. The player is always told, and the ledger always
 * remembers - a currency nobody can audit is a currency nobody trusts.
 */
enum class CoinSource {
    /** A handful on the house, once, so the wallet is never a zero. */
    WELCOME,

    /** Today's puzzle, solved. The steady drip. */
    DAILY,

    /** A run of daily solves reaching a length you have never reached before. */
    STREAK,

    /** A chapter of the journey map finished off. */
    CHAPTER,

    /** A friend's invite code, redeemed. */
    REFERRAL,

    /** A rewarded ad, watched by choice. Never the only way to get anything. */
    AD
}

/**
 * One payable thing, with an [id] that makes paying it twice impossible.
 *
 * Grants are *derived*, not fired: nothing calls "give me 10 coins" at the
 * moment a puzzle is solved. [CoinRules.owed] looks at what the player has
 * done and at what has already been paid, and the difference is the bill.
 * That means a crash, a force-quit or a lost write costs nobody anything -
 * the next settle notices and pays up.
 */
data class CoinGrant(
    val id: String,
    val amount: Int,
    val source: CoinSource,
    /** Flavour for the UI: the streak length, the chapter number, nothing. */
    val detail: Int = 0
)

/**
 * The player's purse and everything they have bought with it.
 *
 * [paidGrants] is the whole anti-double-pay mechanism and the whole
 * retroactivity mechanism at once, which is why there is one set of ids here
 * rather than a boolean per reward type.
 *
 * Note what is *not* stored: the balance. Two running totals that only ever
 * go up are a thing two devices can merge without argument - "take the
 * larger of each" - whereas a balance is a number both sides would have
 * edited, and merging those means either robbing a player or minting coins.
 * So the balance is arithmetic, not state.
 */
data class Wallet(
    /** Every coin ever earned, never spent down. The bragging number. */
    val lifetimeEarned: Int = 0,
    /** Every coin ever spent. Also monotone, for the same reason. */
    val lifetimeSpent: Int = 0,
    val paidGrants: Set<String> = emptySet(),
    /** [com.clevercorner.arrowescape.ui.theme.AppTheme.id]s bought and owned. */
    val unlockedThemes: Set<String> = emptySet(),
    /** This player's own invite code, minted on first sight of the coin screen. */
    val referralCode: String? = null
) {
    /** What is actually in the purse right now. */
    val balance: Int get() = lifetimeEarned - lifetimeSpent

    fun canAfford(price: Int): Boolean = balance >= price

    fun hasPaid(grantId: String): Boolean = grantId in paidGrants

    /**
     * True before any grant has ever been settled - including the welcome
     * bonus, which is the very first thing settled. An existing player's
     * history is therefore recognisable exactly once, and only once.
     */
    val isUntouched: Boolean get() = paidGrants.isEmpty()
}
