package com.clevercorner.arrowescape.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.clevercorner.arrowescape.cloud.CloudState
import com.clevercorner.arrowescape.economy.CoinGrant
import com.clevercorner.arrowescape.economy.Wallet
import com.clevercorner.arrowescape.game.daily.DailyProgress
import com.clevercorner.arrowescape.review.ReviewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "arrow_escape_prefs")

/**
 * Handles all persistent game data using DataStore.
 *
 * Progress is stored *per [GameMode]*: each mode has its own level, best level,
 * completion count and stars, so a player can hop between modes and always pick
 * up where they left off. Settings (sound, haptics, theme, dev mode) stay global.
 */
class GamePreferences(
    private val context: Context,
    /**
     * Scope the shared read lives in. App-lifetime by nature - preferences
     * outlive every screen that reads them - so it is never cancelled.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    companion object {
        // ── Global settings ──────────────────────────────────────────────
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_LEVELS_SINCE_INTERSTITIAL = intPreferencesKey("levels_since_interstitial")
        private val KEY_DEV_MODE = booleanPreferencesKey("dev_mode") // Open any level, no progress changes
        private val KEY_THEME = stringPreferencesKey("theme") // AppTheme.id; absent = match the phone
        private val KEY_GAME_MODE = stringPreferencesKey("game_mode") // GameMode.id the player last chose
        private val KEY_MIGRATED = booleanPreferencesKey("progress_migrated_to_per_mode")
        private val KEY_TUTORIAL_DONE = booleanPreferencesKey("tutorial_done") // Absent = never onboarded
        private val KEY_GAME_TOUR_DONE = booleanPreferencesKey("game_tour_done") // The in-game walkthrough

        // ── Notifications (see notifications.NotificationTokenProvider) ───
        private val KEY_NOTIFICATION_TOKEN = stringPreferencesKey("notification_token")
        private val KEY_LAST_APP_OPEN_TIME = longPreferencesKey("last_app_open_time")
        private val KEY_LAST_NOTIFICATION_TIME = longPreferencesKey("last_notification_time")

        // ── Store review prompt (see review.ReviewPolicy) ────────────────
        private val KEY_REVIEW_STREAK = intPreferencesKey("review_win_streak")
        private val KEY_REVIEW_ASKS = intPreferencesKey("review_times_asked")
        private val KEY_REVIEW_LAST_ASK = intPreferencesKey("review_levels_at_last_ask")
        private val KEY_REVIEW_RATED = booleanPreferencesKey("review_rated")

        // ── Daily puzzle (one board a day, see game.daily) ───────────────
        private val KEY_DAILY_CLEARED = stringPreferencesKey("daily_cleared") // "20251016,20251017"
        private val KEY_DAILY_STREAK = intPreferencesKey("daily_streak")
        private val KEY_DAILY_LAST = intPreferencesKey("daily_last_cleared")
        private val KEY_DAILY_BEST_RUN = intPreferencesKey("daily_best_run")
        private val KEY_DAILY_STARS = stringPreferencesKey("daily_stars") // "20251016:3,20251017:2"

        // ── Coins (see economy.CoinRules for what they are worth) ───────
        //
        // Two monotone totals rather than a balance: see economy.Wallet for
        // why a number both devices would have edited is not a number two
        // devices can merge.
        private val KEY_COIN_EARNED = intPreferencesKey("coin_lifetime")
        private val KEY_COIN_SPENT = intPreferencesKey("coin_spent")
        private val KEY_COIN_GRANTS = stringPreferencesKey("coin_grants") // "welcome,d20251016,s7"
        private val KEY_THEMES_UNLOCKED = stringPreferencesKey("themes_unlocked") // "sunset,pirate"
        private val KEY_REFERRAL_CODE = stringPreferencesKey("referral_code")

        // ── Per-mode progress (suffixed with GameMode.id) ────────────────
        private fun keyCurrentLevel(mode: GameMode) = intPreferencesKey("current_level_${mode.id}")
        private fun keyHighestLevel(mode: GameMode) = intPreferencesKey("highest_level_${mode.id}")
        private fun keyTotalCompleted(mode: GameMode) = intPreferencesKey("total_completed_${mode.id}")
        private fun keyLevelStars(mode: GameMode) = stringPreferencesKey("level_stars_${mode.id}")

        /**
         * Pre-per-mode keys. Progress used to be global - one set of numbers
         * shared by whichever mode was switched on - so on upgrade it is
         * inherited by the mode the player was last in, and only until that
         * mode's own keys get written. Read-only: nothing writes these now.
         */
        private val LEGACY_CURRENT_LEVEL = intPreferencesKey("current_level")
        private val LEGACY_HIGHEST_LEVEL = intPreferencesKey("highest_level")
        private val LEGACY_TOTAL_COMPLETED = intPreferencesKey("total_levels_completed")
        private val LEGACY_LEVEL_STARS = stringPreferencesKey("level_stars")
        private val LEGACY_CURVED_MODE = booleanPreferencesKey("curved_mode")

        // Saved game state
        private val KEY_SAVED_LEVEL = intPreferencesKey("saved_level")
        private val KEY_SAVED_LIVES = intPreferencesKey("saved_lives")
        private val KEY_SAVED_ARROWS = stringPreferencesKey("saved_arrows") // JSON encoded
    }

    /** One mode's standing: where the player is, and how well they did. */
    data class ModeProgress(
        val currentLevel: Int = 1,
        val highestLevel: Int = 1,
        val totalLevelsCompleted: Int = 0,
        val levelStars: Map<Int, Int> = emptyMap() // level -> stars (1-3)
    ) {
        /**
         * Levels cleared, never more than [totalLevels]. A journey can be
         * shorter than an old save (shapes get reworked, levels wrap), and
         * "370 of 150 cleared" helps nobody.
         */
        fun clearedOutOf(totalLevels: Int): Int =
            totalLevelsCompleted.coerceAtMost(totalLevels)

        /**
         * Best level ever reached, never more than [totalLevels].
         *
         * A stray value above a mode's own ceiling can happen - a historical
         * global save migrated into the wrong mode, a debug unlock, a journey
         * that got shorter - and showing it verbatim ("Best: 370" in a game
         * that only has 150 levels) is just confusing. Clamp the display
         * without touching the stored number, so a later legitimate fix to the
         * data is not fighting a UI that already lied about it.
         */
        fun bestOutOf(totalLevels: Int): Int =
            highestLevel.coerceAtMost(totalLevels)
    }

    /**
     * Data class representing all preferences.
     *
     * [currentLevel] and friends are conveniences that read the *active* mode's
     * progress, so callers that do not care about modes need not know they exist.
     */
    data class Prefs(
        val mode: GameMode = GameMode.DEFAULT,
        val progress: Map<GameMode, ModeProgress> = emptyMap(),
        val soundEnabled: Boolean = true,
        val hapticsEnabled: Boolean = true,
        val notificationsEnabled: Boolean = true,
        val levelsSinceInterstitial: Int = 0,
        /** Developer mode: level picker, prev/next, and play never touches saved progress. */
        val devMode: Boolean = false,
        /** [com.clevercorner.arrowescape.ui.theme.AppTheme.id] the player picked; null = never chose. */
        val themeId: String? = null,
        /** False until the player has walked through (or skipped) the tutorial. */
        val tutorialDone: Boolean = false,
        /** False until the guided first moves inside a level are done with. */
        val gameTourDone: Boolean = false,
        /** Everything we remember about asking for a store review. */
        val review: ReviewState = ReviewState(),
        /** Solved days and the current run. Shared by every mode - there is
         *  only ever one puzzle a day. */
        val daily: DailyProgress = DailyProgress(),
        /** Coins earned, coins spent, and the looks they bought. */
        val wallet: Wallet = Wallet()
    ) {
        fun progressOf(mode: GameMode): ModeProgress = progress[mode] ?: ModeProgress()

        val activeProgress: ModeProgress get() = progressOf(mode)
        val currentLevel: Int get() = activeProgress.currentLevel
        val highestLevel: Int get() = activeProgress.highestLevel
        val totalLevelsCompleted: Int get() = activeProgress.totalLevelsCompleted
        val levelStars: Map<Int, Int> get() = activeProgress.levelStars
    }

    /**
     * Decoding of the stored file into a [Prefs].
     *
     * Private because decoding is not free - it rebuilds the per-mode
     * progress map and parses two compact "k:v" strings - and there is no
     * reason for every screen and ViewModel to each do it on every write.
     * They share [prefs] instead.
     */
    private val decoded: Flow<Prefs> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { stored ->
            Prefs(
                mode = resolveMode(stored),
                progress = GameMode.entries.associateWith { stored.progressOf(it) },
                soundEnabled = stored[KEY_SOUND_ENABLED] ?: true,
                hapticsEnabled = stored[KEY_HAPTICS_ENABLED] ?: true,
                notificationsEnabled = stored[KEY_NOTIFICATIONS_ENABLED] ?: true,
                levelsSinceInterstitial = stored[KEY_LEVELS_SINCE_INTERSTITIAL] ?: 0,
                devMode = stored[KEY_DEV_MODE] ?: false,
                themeId = stored[KEY_THEME],
                tutorialDone = stored[KEY_TUTORIAL_DONE] ?: false,
                gameTourDone = stored[KEY_GAME_TOUR_DONE] ?: false,
                review = ReviewState(
                    winStreak = stored[KEY_REVIEW_STREAK] ?: 0,
                    timesAsked = stored[KEY_REVIEW_ASKS] ?: 0,
                    levelsAtLastAsk = stored[KEY_REVIEW_LAST_ASK] ?: 0,
                    rated = stored[KEY_REVIEW_RATED] ?: false
                ),
                daily = DailyProgress(
                    cleared = stored[KEY_DAILY_CLEARED].orEmpty()
                        .split(",")
                        .mapNotNull(String::toIntOrNull)
                        .toSet(),
                    streak = stored[KEY_DAILY_STREAK] ?: 0,
                    lastCleared = stored[KEY_DAILY_LAST],
                    bestRun = stored[KEY_DAILY_BEST_RUN] ?: 0,
                    // Same "key:value" encoding the journey's stars use.
                    stars = parseLevelMap(stored[KEY_DAILY_STARS])
                ),
                wallet = Wallet(
                    lifetimeEarned = stored[KEY_COIN_EARNED] ?: 0,
                    lifetimeSpent = stored[KEY_COIN_SPENT] ?: 0,
                    paidGrants = parseCsv(stored[KEY_COIN_GRANTS]),
                    unlockedThemes = parseCsv(stored[KEY_THEMES_UNLOCKED]),
                    referralCode = stored[KEY_REFERRAL_CODE]
                )
            )
        }

    /**
     * The one decoded copy of the player's preferences, shared by everyone.
     *
     * Null until the first disk read lands, which is a fact worth telling
     * the truth about: a default [Prefs] claims the tutorial was never seen
     * and no theme was ever chosen, and a UI that believes it flashes coach
     * marks at a veteran. Callers that genuinely cannot wait for disk use
     * [prefsOrDefault]; callers that can, wait.
     *
     * Started eagerly: the read begins the moment this object is built,
     * which on launch is well before anything asks for the answer.
     */
    val prefs: StateFlow<Prefs?> = decoded.stateIn(scope, SharingStarted.Eagerly, null)

    /** The best available answer, defaulted until the first read lands. */
    val prefsOrDefault: StateFlow<Prefs> =
        prefs.map { it ?: Prefs() }.stateIn(scope, SharingStarted.Eagerly, Prefs())

    /** The chosen mode, honouring the legacy `curved_mode` flag for upgraders. */
    private fun resolveMode(stored: Preferences): GameMode {
        stored[KEY_GAME_MODE]?.let { return GameMode.fromId(it) }
        val legacyCurved = stored[LEGACY_CURVED_MODE] ?: return GameMode.DEFAULT
        return if (legacyCurved) GameMode.CURVED else GameMode.CLASSIC
    }

    private fun Preferences.progressOf(mode: GameMode): ModeProgress = ModeProgress(
        currentLevel = this[keyCurrentLevel(mode)] ?: 1,
        highestLevel = this[keyHighestLevel(mode)] ?: 1,
        totalLevelsCompleted = this[keyTotalCompleted(mode)] ?: 0,
        levelStars = parseLevelMap(this[keyLevelStars(mode)])
    )

    /**
     * Moves a pre-per-mode save into its owning mode, exactly once.
     *
     * Progress used to be global - one set of numbers shared by whichever mode
     * was switched on - so it belongs to the mode the player was last in.
     * Deciding this lazily at read time would be wrong: the answer would change
     * the moment they switched modes, dragging the old save along with them.
     */
    suspend fun migrateLegacyProgress() {
        // Read before writing. This runs on every launch, and `edit` takes
        // the write lock even when it changes nothing - contending with the
        // first read, which is the one thing the splash is waiting on.
        if (context.dataStore.data.first()[KEY_MIGRATED] == true) return

        context.dataStore.edit { prefs ->
            if (prefs[KEY_MIGRATED] == true) return@edit
            prefs[KEY_MIGRATED] = true

            val owner = resolveMode(prefs)
            prefs[keyCurrentLevel(owner)] = prefs[LEGACY_CURRENT_LEVEL] ?: return@edit
            prefs[keyHighestLevel(owner)] = prefs[LEGACY_HIGHEST_LEVEL] ?: 1
            prefs[keyTotalCompleted(owner)] = prefs[LEGACY_TOTAL_COMPLETED] ?: 0
            prefs[keyLevelStars(owner)] = prefs[LEGACY_LEVEL_STARS] ?: ""
            prefs[KEY_GAME_MODE] = owner.id
        }
    }

    /** Compact "level:value" maps - used for stars and for best times alike. */
    private fun parseLevelMap(encoded: String?): Map<Int, Int> =
        encoded.orEmpty().split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                val level = parts.getOrNull(0)?.toIntOrNull()
                val value = parts.getOrNull(1)?.toIntOrNull()
                if (level != null && value != null) level to value else null
            }
            .toMap()

    private fun encodeLevelMap(values: Map<Int, Int>): String =
        values.entries.joinToString(",") { "${it.key}:${it.value}" }

    /** Plain comma-separated sets - grant ids, theme ids, anything stringy. */
    private fun parseCsv(encoded: String?): Set<String> =
        encoded.orEmpty().split(",").filter { it.isNotBlank() }.toSet()

    // ── Coins ──────────────────────────────────────────────────────

    /**
     * Pays [credited] and writes off [acknowledged], in one transaction.
     *
     * Two lists rather than two methods because they are two halves of one
     * decision and must land together: [acknowledged] grants are marked paid
     * *without* paying, which is how a player who already had forty chapters
     * and a hundred daily solves before coins existed does not wake up rich
     * enough to buy the shop. See `economy.CoinBank`.
     *
     * Balance and ledger move inside the same `edit`, so a grant can never be
     * recorded without its coins or paid twice by two racing writers.
     */
    suspend fun creditCoins(credited: List<CoinGrant>, acknowledged: List<CoinGrant> = emptyList()) {
        if (credited.isEmpty() && acknowledged.isEmpty()) return
        context.dataStore.edit { prefs ->
            val ledger = parseCsv(prefs[KEY_COIN_GRANTS]).toMutableSet()
            // Re-checked against the stored ledger, not the caller's snapshot:
            // the caller read it some milliseconds ago, and a daily solve may
            // have landed since.
            val fresh = credited.filter { ledger.add(it.id) }
            acknowledged.forEach { ledger.add(it.id) }

            val earned = fresh.sumOf { it.amount }
            prefs[KEY_COIN_EARNED] = (prefs[KEY_COIN_EARNED] ?: 0) + earned
            prefs[KEY_COIN_GRANTS] = ledger.joinToString(",")
        }
    }

    /**
     * Buys [themeId], or refuses. Returns the price paid, or null if the
     * purse came up short or the theme was already owned.
     *
     * The till names the price, not the caller: it depends on how many
     * themes are already owned, and only this transaction knows that for
     * certain. A UI that worked out the price, then spent, could be
     * double-tapped into buying the second theme at the first one's price.
     */
    suspend fun purchaseTheme(themeId: String, priceFor: (owned: Int) -> Int): Int? {
        var paid: Int? = null
        context.dataStore.edit { prefs ->
            val owned = parseCsv(prefs[KEY_THEMES_UNLOCKED])
            if (themeId in owned) return@edit

            val price = priceFor(owned.size)
            val spent = prefs[KEY_COIN_SPENT] ?: 0
            if ((prefs[KEY_COIN_EARNED] ?: 0) - spent < price) return@edit

            prefs[KEY_COIN_SPENT] = spent + price
            prefs[KEY_THEMES_UNLOCKED] = (owned + themeId).joinToString(",")
            paid = price
        }
        return paid
    }

    /**
     * Empties the purse and tears up the ledger - debug builds only.
     *
     * Exists because the *first* settle is a one-way door: it writes off a
     * player's pre-coin history instead of paying it, and that is exactly
     * the branch hardest to test twice. Without this, checking it meant
     * uninstalling the app.
     */
    suspend fun resetWallet() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_COIN_EARNED)
            prefs.remove(KEY_COIN_SPENT)
            prefs.remove(KEY_COIN_GRANTS)
            prefs.remove(KEY_THEMES_UNLOCKED)
            prefs.remove(KEY_REFERRAL_CODE)
        }
    }

    /**
     * Returns this player's invite code, minting it on first ask.
     *
     * Write-once: a code that changed would strand every friend holding the
     * old one, so an existing value always wins over [candidate].
     */
    suspend fun referralCode(candidate: String): String {
        var code = candidate
        context.dataStore.edit { prefs ->
            code = prefs[KEY_REFERRAL_CODE] ?: candidate.also { prefs[KEY_REFERRAL_CODE] = it }
        }
        return code
    }

    /**
     * This install's push-registration token, minted on first ask and kept
     * forever - the same write-once shape as [referralCode], for the same
     * reason: whatever eventually reads this needs one stable answer, not a
     * fresh one every call. [candidate] is normally [NotificationToken.generate]
     * - see that object for why it is not a real token yet.
     */
    suspend fun notificationToken(candidate: String): String {
        var token = candidate
        context.dataStore.edit { prefs ->
            token = prefs[KEY_NOTIFICATION_TOKEN] ?: candidate.also { prefs[KEY_NOTIFICATION_TOKEN] = it }
        }
        return token
    }

    suspend fun setCurrentLevel(mode: GameMode, level: Int) {
        context.dataStore.edit { prefs ->
            val best = prefs.progressOf(mode).highestLevel
            prefs[keyCurrentLevel(mode)] = level
            prefs[keyHighestLevel(mode)] = maxOf(level, best)
        }
    }

    suspend fun setGameMode(mode: GameMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GAME_MODE] = mode.id
        }
    }

    /**
     * Overwrites this device's journey progress, daily record and purse
     * verbatim with [state].
     *
     * Used only by cloud sync, which has already decided what the right
     * answers are (see `cloud.ProgressSnapshot.merge`) - this is a plain
     * writer, not a merge, so GamePreferences does not need to know cloud
     * save exists. One transaction, because a device that restored its
     * levels but not its coins is a device that has been robbed.
     *
     * Notice what it does *not* write: sound, haptics, theme, dev mode. A
     * setting is a property of a phone, not of a player.
     */
    suspend fun applyCloudState(state: CloudState) {
        context.dataStore.edit { prefs ->
            for (mode in GameMode.entries) {
                val p = state.progress[mode] ?: continue
                prefs[keyCurrentLevel(mode)] = p.currentLevel
                prefs[keyHighestLevel(mode)] = p.highestLevel
                prefs[keyTotalCompleted(mode)] = p.totalLevelsCompleted
                prefs[keyLevelStars(mode)] = encodeLevelMap(p.levelStars)
            }

            with(state.daily) {
                prefs[KEY_DAILY_CLEARED] = cleared.sorted().joinToString(",")
                prefs[KEY_DAILY_STREAK] = streak
                prefs[KEY_DAILY_BEST_RUN] = bestRun
                prefs[KEY_DAILY_STARS] = encodeLevelMap(stars)
                lastCleared?.let { prefs[KEY_DAILY_LAST] = it }
            }

            with(state.wallet) {
                prefs[KEY_COIN_EARNED] = lifetimeEarned
                prefs[KEY_COIN_SPENT] = lifetimeSpent
                prefs[KEY_COIN_GRANTS] = paidGrants.joinToString(",")
                prefs[KEY_THEMES_UNLOCKED] = unlockedThemes.joinToString(",")
                referralCode?.let { prefs[KEY_REFERRAL_CODE] = it }
            }
        }
    }

    suspend fun incrementLevelsSinceInterstitial() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LEVELS_SINCE_INTERSTITIAL] = (prefs[KEY_LEVELS_SINCE_INTERSTITIAL] ?: 0) + 1
        }
    }

    suspend fun resetLevelsSinceInterstitial() {
        context.dataStore.edit { prefs ->
            prefs[KEY_LEVELS_SINCE_INTERSTITIAL] = 0
        }
    }

    suspend fun incrementTotalLevelsCompleted(mode: GameMode) {
        context.dataStore.edit { prefs ->
            prefs[keyTotalCompleted(mode)] = prefs.progressOf(mode).totalLevelsCompleted + 1
        }
    }

    suspend fun saveLevelStars(mode: GameMode, level: Int, stars: Int) {
        context.dataStore.edit { prefs ->
            val starsMap = prefs.progressOf(mode).levelStars.toMutableMap()
            // Only update if new stars are better than existing
            if (stars > (starsMap[level] ?: 0)) {
                starsMap[level] = stars
            }
            prefs[keyLevelStars(mode)] = encodeLevelMap(starsMap)
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun recordAppOpen(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_APP_OPEN_TIME] = timestamp
        }
    }

    suspend fun recordNotificationShown(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_NOTIFICATION_TIME] = timestamp
        }
    }

    suspend fun getLastAppOpenTime(): Long =
        context.dataStore.data.first()[KEY_LAST_APP_OPEN_TIME] ?: 0L

    suspend fun getLastNotificationTime(): Long =
        context.dataStore.data.first()[KEY_LAST_NOTIFICATION_TIME] ?: 0L

    suspend fun setTheme(themeId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = themeId
        }
    }

    /** Both walkthroughs at once: finished after onboarding, reset to replay. */
    suspend fun setWalkthroughsDone(done: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TUTORIAL_DONE] = done
            prefs[KEY_GAME_TOUR_DONE] = done
        }
    }

    suspend fun setTutorialDone(done: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TUTORIAL_DONE] = done
        }
    }

    suspend fun setGameTourDone(done: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GAME_TOUR_DONE] = done
        }
    }

    /**
     * Stores the review bookkeeping as one value.
     *
     * The four numbers only ever change together (see
     * [com.clevercorner.arrowescape.review.ReviewPolicy]), so one writer beats four
     * setters that could leave half a decision on disk.
     */
    suspend fun saveReviewState(state: ReviewState) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REVIEW_STREAK] = state.winStreak
            prefs[KEY_REVIEW_ASKS] = state.timesAsked
            prefs[KEY_REVIEW_LAST_ASK] = state.levelsAtLastAsk
            prefs[KEY_REVIEW_RATED] = state.rated
        }
    }

    /**
     * Keeps the longest daily run ever. Compared inside the edit, so two
     * writers can never overwrite a record with a smaller one.
     */
    suspend fun recordDailyRun(run: Int) {
        context.dataStore.edit { prefs ->
            if (run > (prefs[KEY_DAILY_BEST_RUN] ?: 0)) prefs[KEY_DAILY_BEST_RUN] = run
        }
    }

    /**
     * Marks [date]'s puzzle solved, letting [DailyProgress] decide what that
     * does to the streak - the rule lives with the data, not with the writer.
     */
    suspend fun markDailyCleared(date: java.time.LocalDate, stars: Int = 0) {
        context.dataStore.edit { prefs ->
            val current = DailyProgress(
                cleared = prefs[KEY_DAILY_CLEARED].orEmpty()
                    .split(",")
                    .mapNotNull(String::toIntOrNull)
                    .toSet(),
                streak = prefs[KEY_DAILY_STREAK] ?: 0,
                lastCleared = prefs[KEY_DAILY_LAST],
                bestRun = prefs[KEY_DAILY_BEST_RUN] ?: 0,
                stars = parseLevelMap(prefs[KEY_DAILY_STARS])
            )
            val updated = current.withCleared(date, stars)

            prefs[KEY_DAILY_CLEARED] = updated.cleared.sorted().joinToString(",")
            prefs[KEY_DAILY_STREAK] = updated.streak
            prefs[KEY_DAILY_STARS] = updated.stars.entries
                .sortedBy { it.key }
                .joinToString(",") { "${it.key}:${it.value}" }
            updated.lastCleared?.let { prefs[KEY_DAILY_LAST] = it }
        }
    }

    suspend fun setDevMode(enabled: Boolean) {        context.dataStore.edit { prefs ->
            prefs[KEY_DEV_MODE] = enabled
        }
    }

    /**
     * DEBUG: Unlock all levels of one mode for testing.
     */
    suspend fun unlockAllLevels(mode: GameMode, maxLevel: Int) {
        context.dataStore.edit { prefs ->
            prefs[keyHighestLevel(mode)] = maxLevel
            prefs[keyCurrentLevel(mode)] = maxLevel
            prefs[keyTotalCompleted(mode)] = maxLevel - 1
            // Give 2 stars to all levels for testing
            prefs[keyLevelStars(mode)] = encodeLevelMap((1 until maxLevel).associateWith { 2 })
        }
    }

    /**
     * DEBUG: Reset one mode's progress to level 1.
     */
    suspend fun resetProgress(mode: GameMode) {
        context.dataStore.edit { prefs ->
            prefs[keyHighestLevel(mode)] = 1
            prefs[keyCurrentLevel(mode)] = 1
            prefs[keyTotalCompleted(mode)] = 0
            prefs[keyLevelStars(mode)] = ""
        }
    }

    // Saved game state for resuming after app kill
    data class SavedGameState(
        val level: Int,
        val lives: Int,
        val activeArrowIds: Set<Int> // Which arrows are still active
    )

    val savedGameFlow: Flow<SavedGameState?> = context.dataStore.data
        .map { prefs ->
            val level = prefs[KEY_SAVED_LEVEL] ?: return@map null
            val lives = prefs[KEY_SAVED_LIVES] ?: return@map null
            val arrowsJson = prefs[KEY_SAVED_ARROWS] ?: return@map null

            // Parse simple comma-separated IDs
            val activeIds = arrowsJson.split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { it.toIntOrNull() }
                .toSet()

            SavedGameState(level, lives, activeIds)
        }

    suspend fun saveGameState(level: Int, lives: Int, activeArrowIds: Set<Int>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SAVED_LEVEL] = level
            prefs[KEY_SAVED_LIVES] = lives
            prefs[KEY_SAVED_ARROWS] = activeArrowIds.joinToString(",")
        }
    }

    suspend fun clearSavedGame() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SAVED_LEVEL)
            prefs.remove(KEY_SAVED_LIVES)
            prefs.remove(KEY_SAVED_ARROWS)
        }
    }
}
