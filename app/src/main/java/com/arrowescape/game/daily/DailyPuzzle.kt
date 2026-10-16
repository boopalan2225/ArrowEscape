package com.arrowescape.game.daily

import com.arrowescape.game.curve.DifficultyTuning
import java.time.LocalDate
import kotlin.random.Random

/**
 * One day's puzzle: which board, and how mean it is allowed to be.
 *
 * Everything here is derived from the date alone, so two players on opposite
 * sides of the planet - both offline - get the same board on the same day
 * without a single byte of server.
 */
data class DailyPuzzle(
    val date: LocalDate,
    /** Seed handed to the generator; also picks the artwork. */
    val levelSeed: Int,
    val tier: String
) {
    val stamp: Int get() = DailyCalendar.stampOf(date)
}

/**
 * Turns dates into puzzles and back.
 *
 * Pure Kotlin, no Android: the interesting part is the arithmetic, and that
 * deserves to be readable (and testable) without an emulator.
 */
object DailyCalendar {

    /**
     * The week has a shape: gentle start, a weekend that bites.
     *
     * Values are the *last* level of each tier from [DifficultyTuning], so
     * the daily difficulty curve and the journey's never drift apart.
     */
    private val TIER_BANDS: Map<java.time.DayOfWeek, IntRange> = mapOf(
        java.time.DayOfWeek.MONDAY to 1..DifficultyTuning.EASY_MAX,
        java.time.DayOfWeek.TUESDAY to 1..DifficultyTuning.EASY_MAX,
        java.time.DayOfWeek.WEDNESDAY to (DifficultyTuning.EASY_MAX + 1)..DifficultyTuning.NORMAL_MAX,
        java.time.DayOfWeek.THURSDAY to (DifficultyTuning.EASY_MAX + 1)..DifficultyTuning.NORMAL_MAX,
        java.time.DayOfWeek.FRIDAY to (DifficultyTuning.NORMAL_MAX + 1)..DifficultyTuning.HARD_MAX,
        java.time.DayOfWeek.SATURDAY to (DifficultyTuning.NORMAL_MAX + 1)..DifficultyTuning.HARD_MAX,
        java.time.DayOfWeek.SUNDAY to (DifficultyTuning.HARD_MAX + 1)..DifficultyTuning.EXPERT_MAX
    )

    /** `yyyyMMdd` - sortable, comparable, and readable in a log. */
    fun stampOf(date: LocalDate): Int =
        date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth

    fun dateOf(stamp: Int): LocalDate =
        LocalDate.of(stamp / 10_000, (stamp / 100) % 100, stamp % 100)

    /** The puzzle for [date]. Same date in, same puzzle out, always. */
    fun puzzleFor(date: LocalDate): DailyPuzzle {
        val band = TIER_BANDS.getValue(date.dayOfWeek)
        // Seeded by the stamp rather than by position in the band, so
        // consecutive days do not march through consecutive artwork.
        val offset = Random(stampOf(date).toLong()).nextInt(band.count())
        val seed = band.first + offset
        return DailyPuzzle(date = date, levelSeed = seed, tier = DifficultyTuning.tierOf(seed))
    }
}

/**
 * Which days have been solved, and how long the current run is.
 *
 * Backfilling an old day counts as solved but never extends the streak -
 * a streak that can be bought retroactively is not a streak.
 */
data class DailyProgress(
    val cleared: Set<Int> = emptySet(),
    val streak: Int = 0,
    val lastCleared: Int? = null
) {
    fun isCleared(date: LocalDate): Boolean = DailyCalendar.stampOf(date) in cleared

    fun withCleared(date: LocalDate): DailyProgress {
        val stamp = DailyCalendar.stampOf(date)
        if (stamp in cleared) return this

        val backfill = lastCleared != null && stamp < lastCleared
        if (backfill) return copy(cleared = cleared + stamp)

        val continues = lastCleared == DailyCalendar.stampOf(date.minusDays(1))
        return DailyProgress(
            cleared = cleared + stamp,
            streak = if (continues) streak + 1 else 1,
            lastCleared = stamp
        )
    }
}
