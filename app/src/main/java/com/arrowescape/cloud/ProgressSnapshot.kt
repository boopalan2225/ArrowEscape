package com.arrowescape.cloud

import com.arrowescape.data.GameMode
import com.arrowescape.data.GamePreferences.ModeProgress

/**
 * The wire format for cloud-saved progress, and the rule for combining two
 * copies of it.
 *
 * Deliberately has no Android dependencies - it is pure data transformation,
 * so it can be tested without a device or a Play Games account.
 */
object ProgressSnapshot {

    /**
     * Encodes every mode's progress into one compact string, safe to hand to
     * Play Games Snapshots as a byte blob.
     *
     * Format: one mode per line, "id:current:highest:completed:starsCsv".
     * Stars already have their own "level:stars,level:stars" shape (see
     * [GamePreferences]); nesting it unescaped works because neither a mode id
     * nor an int can contain the outer separators (':' and newline).
     */
    fun encode(progress: Map<GameMode, ModeProgress>): String =
        progress.entries.joinToString("\n") { (mode, p) ->
            val stars = p.levelStars.entries.joinToString(",") { "${it.key}:${it.value}" }
            "${mode.id}:${p.currentLevel}:${p.highestLevel}:${p.totalLevelsCompleted}:$stars"
        }

    /** The inverse of [encode]. Unparseable lines are skipped, never crash. */
    fun decode(raw: String): Map<GameMode, ModeProgress> =
        raw.lineSequence()
            .mapNotNull { line -> decodeLine(line) }
            .toMap()

    private fun decodeLine(line: String): Pair<GameMode, ModeProgress>? {
        val parts = line.split(":", limit = 5)
        if (parts.size < 4) return null
        val mode = GameMode.entries.firstOrNull { it.id == parts[0] } ?: return null
        val current = parts[1].toIntOrNull() ?: return null
        val highest = parts[2].toIntOrNull() ?: return null
        val completed = parts[3].toIntOrNull() ?: return null
        val stars = parts.getOrNull(4).orEmpty().split(",")
            .mapNotNull { entry ->
                val kv = entry.split(":")
                val level = kv.getOrNull(0)?.toIntOrNull()
                val starCount = kv.getOrNull(1)?.toIntOrNull()
                if (level != null && starCount != null) level to starCount else null
            }
            .toMap()
        return mode to ModeProgress(current, highest, completed, stars)
    }

    /**
     * Combines two copies of progress - typically "what's on this device" and
     * "what's in the cloud" - by taking the better number in every field, per
     * mode. Never regresses either side: a player can never lose progress by
     * signing in, on this device or any other.
     */
    fun merge(
        a: Map<GameMode, ModeProgress>,
        b: Map<GameMode, ModeProgress>
    ): Map<GameMode, ModeProgress> =
        GameMode.entries.associateWith { mode ->
            mergeOne(a[mode] ?: ModeProgress(), b[mode] ?: ModeProgress())
        }

    private fun mergeOne(a: ModeProgress, b: ModeProgress): ModeProgress = ModeProgress(
        currentLevel = maxOf(a.currentLevel, b.currentLevel),
        highestLevel = maxOf(a.highestLevel, b.highestLevel),
        totalLevelsCompleted = maxOf(a.totalLevelsCompleted, b.totalLevelsCompleted),
        levelStars = mergeStars(a.levelStars, b.levelStars)
    )

    private fun mergeStars(a: Map<Int, Int>, b: Map<Int, Int>): Map<Int, Int> =
        (a.keys + b.keys).associateWith { level ->
            maxOf(a[level] ?: 0, b[level] ?: 0)
        }
}
