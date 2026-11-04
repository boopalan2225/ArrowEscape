package com.clevercorner.arrowescape.economy

import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.game.daily.DailyCalendar
import com.clevercorner.arrowescape.game.daily.DailyProgress

/**
 * What a coin is worth earning, and the one function that decides who is owed
 * what.
 *
 * Deliberately pure and Android-free. Every number the economy turns on lives
 * here, in one screenful, so balancing the game is editing a constant rather
 * than hunting through ViewModels.
 *
 * The shape of the economy is a deliberate one: **coins come from showing up,
 * not from grinding.** There is no per-level payout, because a player who
 * replays level 1 forty times has not earned anything. What pays is a day you
 * turned up for, a run you kept alive, a chapter you finished, and a friend
 * you brought.
 */
object CoinRules {

    /** On the house, once. Enough to feel like a wallet, nowhere near a theme. */
    const val WELCOME = 25

    /** Today's puzzle, solved. */
    const val DAILY_SOLVE = 8

    /** On top of [DAILY_SOLVE], for a daily solved without a single mistake. */
    const val DAILY_FLAWLESS_BONUS = 7

    /** One chapter of a journey map finished. */
    const val CHAPTER = 20

    /** A friend's code redeemed. */
    const val REFERRAL = 50

    /**
     * What the next theme costs, by how many have already been bought.
     *
     * A ladder rather than a price per theme: every look took the same work
     * to make, and tying the price to *which* one you fancy would quietly
     * rank them. Tying it to *how many you already own* says the real thing
     * instead - the first is a treat, the third is a collection.
     */
    val UNLOCK_PRICES: List<Int> = listOf(150, 300, 500)

    /** Price of the next unlock for somebody who already owns [owned] of them. */
    fun unlockPrice(owned: Int): Int =
        UNLOCK_PRICES.getOrElse(owned) { UNLOCK_PRICES.last() }

    /** Stars that count as flawless - see [com.clevercorner.arrowescape.game.Scoring]. */
    private const val FLAWLESS_STARS = 3

    /**
     * Consecutive *days* that pay, and what they pay.
     *
     * Days, emphatically - this is the daily-puzzle streak
     * ([DailyProgress.streak]), not the run of arrows escaped without a
     * mistake ([DailyProgress.bestRun]), which is a scoreboard stat and has
     * nothing to do with turning up.
     *
     * Each tier pays once in a lifetime, and the ledger is what remembers -
     * no "best streak ever" field required, because a grant already paid is
     * a record already set. Break a run and climb back to seven days and
     * nothing prints; otherwise the optimal play would be to skip every
     * eighth day.
     */
    val STREAK_TIERS: Map<Int, Int> = linkedMapOf(
        3 to 15,
        7 to 40,
        14 to 80,
        30 to 160,
        60 to 320,
        100 to 600
    )

    /**
     * The next run of days worth chasing - the first tier this wallet has
     * never been paid for. Asked of the ledger rather than of the current
     * streak, so breaking a run does not re-offer a reward already banked.
     */
    fun nextStreakTier(wallet: Wallet): Int? =
        STREAK_TIERS.keys.firstOrNull { !wallet.hasPaid(streakId(it)) }

    // ── Grant ids ────────────────────────────────────────────────────────
    // Short on purpose: these are kept forever, one per day played.

    const val WELCOME_ID = "welcome"
    private fun dailyId(stamp: Int) = "d$stamp"
    private fun streakId(tier: Int) = "s$tier"
    private fun chapterId(mode: GameMode, chapter: Int) = "c-${mode.id}-$chapter"
    /**
     * One invite bonus per player, ever - so the id names the *bonus*, not
     * the code. Keying it by code would let a determined player collect
     * fifty coins per friend they have ever met, which is an economy with a
     * hole in it rather than a referral programme.
     */
    const val REFERRAL_ID = "referral"

    /**
     * Everything [wallet] has earned but not yet been paid.
     *
     * Derived entirely from state that already existed before coins did -
     * solved days, best run, levels beaten - which is what makes the ledger
     * self-healing: the bill is always "what is true" minus "what is paid",
     * and a missed write just shows up on the next invoice.
     *
     * [chaptersCleared] is passed in rather than looked up because chapter
     * counts are journey data living up in the UI layer, and the economy has
     * no business importing a screen to count to ten.
     */
    fun owed(
        wallet: Wallet,
        chaptersCleared: Map<GameMode, Int>,
        daily: DailyProgress
    ): List<CoinGrant> {
        val bill = mutableListOf<CoinGrant>()

        if (!wallet.hasPaid(WELCOME_ID)) {
            bill += CoinGrant(WELCOME_ID, WELCOME, CoinSource.WELCOME)
        }

        // Oldest day first, so a player returning after a week of offline
        // solves watches their history pay out in the order it happened.
        for (stamp in daily.cleared.sorted()) {
            val id = dailyId(stamp)
            if (wallet.hasPaid(id)) continue
            val flawless = (daily.stars[stamp] ?: 0) >= FLAWLESS_STARS
            bill += CoinGrant(
                id = id,
                amount = DAILY_SOLVE + if (flawless) DAILY_FLAWLESS_BONUS else 0,
                source = CoinSource.DAILY,
                detail = DailyCalendar.dateOf(stamp).dayOfMonth
            )
        }

        for ((tier, amount) in STREAK_TIERS) {
            val id = streakId(tier)
            if (daily.streak >= tier && !wallet.hasPaid(id)) {
                bill += CoinGrant(id, amount, CoinSource.STREAK, detail = tier)
            }
        }

        for ((mode, cleared) in chaptersCleared) {
            for (chapter in 0 until cleared) {
                val id = chapterId(mode, chapter)
                if (!wallet.hasPaid(id)) {
                    bill += CoinGrant(id, CHAPTER, CoinSource.CHAPTER, detail = chapter + 1)
                }
            }
        }

        return bill
    }
}
