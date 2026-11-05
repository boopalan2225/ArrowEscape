package com.clevercorner.arrowescape.game.level

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How many arrows a level should contain, and the grid size that yields it.
 *
 * Grid size used to be set directly per design, which quietly made "same
 * level" mean different amounts of work depending on what was being drawn. A
 * hollow ring and a solid square at 10x10 differ by a factor of three in how
 * many arrows they hold, so two levels nominally at the same difficulty could
 * be a thirty-second board and a two-minute one. Measured across the current
 * game the count is not even monotonic - level 60 had 88 arrows and level 90
 * had 69 - so the number went *down* while the difficulty label went up.
 *
 * Arrow count is the honest unit because it is the one the player spends
 * time in. Picking a target and solving backwards for the grid makes a ring
 * and a square at the same level cost the same, which is the only definition
 * of "same difficulty" that survives contact with an actual player.
 */
object ArrowBudget {

    /** Below this a board is a warm-up; above it, a slog regardless of cleverness. */
    private const val MIN_ARROWS = 10
    private const val MAX_ARROWS = 140

    /** Grids outside this are either unreadable or absurd on a phone. */
    private const val MIN_GRID = 5
    private const val MAX_GRID = 26

    /**
     * Target arrow count for a level.
     *
     * Grows quickly early, where each extra arrow is a real increase in
     * substance, then flattens - past a point more arrows is more minutes,
     * not more game, and the interesting difficulty has to come from the
     * board being harder to read rather than longer to clear.
     */
    fun forLevel(level: Int): Int {
        val target = when {
            level <= 1 -> 12
            level <= 10 -> 14 + level
            level <= 30 -> 24 + (level - 10)
            level <= 80 -> 44 + (level - 30) / 2
            level <= 160 -> 69 + (level - 80) / 4
            else -> 89 + (level - 160) / 10
        }
        return target.coerceIn(MIN_ARROWS, MAX_ARROWS)
    }

    /**
     * The grid that should hold [targetArrows] of this shape.
     *
     * Inverts `arrows = cells * fillRatio / averageSnakeLength`. The fill
     * ratio is the shape's own - a ring wastes most of its bounding box and
     * therefore needs a bigger grid to hold the same number of arrows, which
     * is exactly the correction this whole object exists to make.
     */
    fun gridFor(targetArrows: Int, fillRatio: Float, averageSnakeLength: Float): Int {
        val usableFill = fillRatio.coerceIn(0.05f, 1f)
        val length = averageSnakeLength.coerceAtLeast(1f)
        val cellsNeeded = targetArrows * length / usableFill
        return sqrt(cellsNeeded).roundToInt().coerceIn(MIN_GRID, MAX_GRID)
    }

    /**
     * What one arrow costs in cells, given a length mix.
     *
     * The representative lengths are measured, not nominal. A mix asking for
     * "very long" snakes does not get 5-cell snakes: the carver is working
     * inside a silhouette and constantly runs out of room, so across levels
     * 2-300 the real average runs from 1.3 cells to 3.4. Sizing grids from
     * the nominal mix overshot the arrow budget by nearly 3x on early
     * levels - a level 5 asking for 19 arrows and getting 53.
     */
    fun averageSnakeLength(mix: LengthMix): Float {
        val total = mix.veryLong + mix.long + mix.medium + mix.short
        if (total <= 0f) return 1f
        return (mix.veryLong * 3.6f + mix.long * 2.9f + mix.medium * 2.1f + mix.short * 1.2f) / total
    }
}
