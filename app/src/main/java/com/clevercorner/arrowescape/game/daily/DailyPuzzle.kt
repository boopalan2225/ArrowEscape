package com.clevercorner.arrowescape.game.daily

import com.clevercorner.arrowescape.game.curve.DifficultyTuning
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
 * One puzzle per day, and only today's: a day you skipped is gone for good.
 * That is what makes a streak worth anything - if yesterday could be played
 * today, "50 days in a row" would mean nothing more than "50 days of
 * catching up eventually".
 */
data class DailyProgress(
    val cleared: Set<Int> = emptySet(),
    val streak: Int = 0,
    val lastCleared: Int? = null,
    /** Longest run of escapes in a row, ever - the player's best winning streak. */
    val bestRun: Int = 0
) {
    fun isCleared(date: LocalDate): Boolean = DailyCalendar.stampOf(date) in cleared

    /**
     * What [date] looks like to the player right now.
     *
     * [today] is a parameter rather than a call to the clock so the rule can
     * be reasoned about (and tested) without time travel.
     */
    fun statusOf(date: LocalDate, today: LocalDate = LocalDate.now()): DailyStatus = when {
        isCleared(date) -> DailyStatus.SOLVED
        date.isAfter(today) -> DailyStatus.LOCKED
        date.isBefore(today) -> DailyStatus.EXPIRED
        else -> DailyStatus.OPEN
    }

    /** The single gate every entry point should ask before dealing a board. */
    fun canPlay(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean =
        statusOf(date, today) == DailyStatus.OPEN

    /**
     * Records a solve. A day that does not directly follow the last one
     * restarts the streak at 1 - skipping is allowed, it just costs you the
     * run you had going.
     */
    fun withCleared(date: LocalDate): DailyProgress {
        val stamp = DailyCalendar.stampOf(date)
        if (stamp in cleared) return this

        val continues = lastCleared == DailyCalendar.stampOf(date.minusDays(1))
        return copy(
            cleared = cleared + stamp,
            streak = if (continues) streak + 1 else 1,
            lastCleared = stamp
        )
    }
}

/** The four states a date can be in, from the player's point of view. */
enum class DailyStatus {
    /** Today, and not yet solved. The only state you can play in. */
    OPEN,

    /** Solved. Come back tomorrow. */
    SOLVED,

    /** Missed. Gone for good - that is the deal. */
    EXPIRED,

    /** Tomorrow's, or later. Not yours yet. */
    LOCKED
}
