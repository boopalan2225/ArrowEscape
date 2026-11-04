package com.clevercorner.arrowescape.cloud

import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.data.GamePreferences.ModeProgress
import com.clevercorner.arrowescape.economy.Wallet
import com.clevercorner.arrowescape.game.daily.DailyProgress

/**
 * The wire format for cloud-saved state, and the rule for combining two
 * copies of it.
 *
 * Deliberately has no Android dependencies - it is pure data transformation,
 * so it can be tested without a device or a Play Games account.
 *
 * **One line, one subject.** Journey progress has a line per mode; the daily
 * record and the purse have one each. Unknown lines are skipped rather than
 * fatal, which is what makes the format grow safely in both directions: an
 * old build reading a new save ignores what it does not recognise, and a new
 * build reading an old save (one with nothing but mode lines, written before
 * coins existed) simply finds an empty wallet and an empty daily record.
 */
object ProgressSnapshot {

    private const val DAILY_TAG = "daily"
    private const val WALLET_TAG = "wallet"

    fun encode(state: CloudState): String = buildList {
        state.progress.forEach { (mode, p) ->
            // Stars already have their own "level:stars,level:stars" shape;
            // nesting it unescaped works because it is last on the line and
            // neither a mode id nor an int can contain a newline.
            add("${mode.id}:${p.currentLevel}:${p.highestLevel}:${p.totalLevelsCompleted}:${encodePairs(p.levelStars)}")
        }
        with(state.daily) {
            add("$DAILY_TAG:$streak:${lastCleared ?: 0}:$bestRun:${cleared.sorted().joinToString(",")}:${encodePairs(stars)}")
        }
        with(state.wallet) {
            add("$WALLET_TAG:$lifetimeEarned:$lifetimeSpent:${referralCode.orEmpty()}:${unlockedThemes.sorted().joinToString(",")}:${paidGrants.sorted().joinToString(",")}")
        }
    }.joinToString("\n")

    /** The inverse of [encode]. Unparseable lines are skipped, never crash. */
    fun decode(raw: String): CloudState {
        val progress = mutableMapOf<GameMode, ModeProgress>()
        var daily = DailyProgress()
        var wallet = Wallet()

        for (line in raw.lineSequence()) {
            when (line.substringBefore(':')) {
                DAILY_TAG -> daily = decodeDaily(line) ?: daily
                WALLET_TAG -> wallet = decodeWallet(line) ?: wallet
                else -> decodeMode(line)?.let { (mode, p) -> progress[mode] = p }
            }
        }
        return CloudState(progress, daily, wallet)
    }

    private fun decodeMode(line: String): Pair<GameMode, ModeProgress>? {
        val parts = line.split(":", limit = 5)
        if (parts.size < 4) return null
        val mode = GameMode.entries.firstOrNull { it.id == parts[0] } ?: return null
        val current = parts[1].toIntOrNull() ?: return null
        val highest = parts[2].toIntOrNull() ?: return null
        val completed = parts[3].toIntOrNull() ?: return null
        return mode to ModeProgress(current, highest, completed, decodePairs(parts.getOrNull(4)))
    }

    private fun decodeDaily(line: String): DailyProgress? {
        val parts = line.split(":", limit = 6)
        if (parts.size < 6) return null
        return DailyProgress(
            cleared = parts[4].split(",").mapNotNull(String::toIntOrNull).toSet(),
            streak = parts[1].toIntOrNull() ?: 0,
            lastCleared = parts[2].toIntOrNull()?.takeIf { it > 0 },
            bestRun = parts[3].toIntOrNull() ?: 0,
            stars = decodePairs(parts[5])
        )
    }

    private fun decodeWallet(line: String): Wallet? {
        val parts = line.split(":", limit = 6)
        if (parts.size < 6) return null
        return Wallet(
            lifetimeEarned = parts[1].toIntOrNull() ?: 0,
            lifetimeSpent = parts[2].toIntOrNull() ?: 0,
            paidGrants = splitCsv(parts[5]),
            unlockedThemes = splitCsv(parts[4]),
            referralCode = parts[3].takeIf { it.isNotBlank() }
        )
    }

    private fun encodePairs(values: Map<Int, Int>): String =
        values.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    private fun decodePairs(encoded: String?): Map<Int, Int> =
        encoded.orEmpty().split(",")
            .mapNotNull { entry ->
                val kv = entry.split(":")
                val key = kv.getOrNull(0)?.toIntOrNull()
                val value = kv.getOrNull(1)?.toIntOrNull()
                if (key != null && value != null) key to value else null
            }
            .toMap()

    private fun splitCsv(encoded: String): Set<String> =
        encoded.split(",").filter { it.isNotBlank() }.toSet()

    /**
     * Combines two copies of a player - typically "what's on this device"
     * and "what's in the cloud" - by taking the better of every field.
     *
     * The invariant, everywhere, is **never regress**: signing in cannot
     * cost a player a level, a solved day, a coin or a theme, on this device
     * or any other. Every field is therefore either a maximum, a union, or
     * recomputed from a union.
     */
    fun merge(a: CloudState, b: CloudState): CloudState = CloudState(
        progress = GameMode.entries.associateWith {
            mergeMode(a.progress[it] ?: ModeProgress(), b.progress[it] ?: ModeProgress())
        },
        daily = mergeDaily(a.daily, b.daily),
        wallet = mergeWallet(a.wallet, b.wallet)
    )

    private fun mergeMode(a: ModeProgress, b: ModeProgress): ModeProgress = ModeProgress(
        currentLevel = maxOf(a.currentLevel, b.currentLevel),
        highestLevel = maxOf(a.highestLevel, b.highestLevel),
        totalLevelsCompleted = maxOf(a.totalLevelsCompleted, b.totalLevelsCompleted),
        levelStars = mergeBest(a.levelStars, b.levelStars)
    )

    private fun mergeDaily(a: DailyProgress, b: DailyProgress): DailyProgress = DailyProgress(
        cleared = a.cleared + b.cleared,
        lastCleared = maxOfNullable(a.lastCleared, b.lastCleared),
        bestRun = maxOf(a.bestRun, b.bestRun),
        stars = mergeBest(a.stars, b.stars)
        // streak left at its default and recounted below: the union of days
        // is the only honest input, and the larger of two half-streaks is
        // still a wrong answer.
    ).withStreakRecounted()

    private fun mergeWallet(a: Wallet, b: Wallet): Wallet = Wallet(
        // Both totals only ever climb, which is exactly why the balance is
        // derived from them rather than stored: there is no version of "the
        // larger balance" that does not either rob a player or mint coins.
        lifetimeEarned = maxOf(a.lifetimeEarned, b.lifetimeEarned),
        lifetimeSpent = maxOf(a.lifetimeSpent, b.lifetimeSpent),
        paidGrants = a.paidGrants + b.paidGrants,
        unlockedThemes = a.unlockedThemes + b.unlockedThemes,
        // First one wins and then never changes: a code that moved would
        // strand every friend still holding the old one.
        referralCode = a.referralCode ?: b.referralCode
    )

    private fun mergeBest(a: Map<Int, Int>, b: Map<Int, Int>): Map<Int, Int> =
        (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0, b[it] ?: 0) }

    private fun maxOfNullable(a: Int?, b: Int?): Int? =
        listOfNotNull(a, b).maxOrNull()
}
