package com.clevercorner.arrowescape.game

/**
 * Everything a level's header says about it, derived from the level number
 * alone.
 *
 * A board takes real work to build - curve packing, an orientation search,
 * a solvability sweep - and that work cannot be done on the main thread. But
 * the *name* of a level is not the product of that work: it is a pure
 * function of the number the player tapped, known with certainty before the
 * screen even exists.
 *
 * Keeping the two apart is what stops the header from claiming "Level 1"
 * while level 37 is being built behind it. Each generator exposes a
 * `headerFor(level)` that reads the same sources its builder does, so the
 * instant answer and the finished board can never disagree.
 */
data class LevelHeader(
    val shapeName: String,
    val difficultyLabel: String
)
