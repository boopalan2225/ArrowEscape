package com.clevercorner.arrowescape.game.daily

/**
 * The daily challenge's twist: every 15 seconds the board re-arranges itself.
 *
 * The arrows never change in number - the same set is still there, pointing
 * new ways - so a shuffle is a fresh puzzle rather than a punishment. What it
 * costs is your plan, which is exactly the point: the journey rewards reading
 * a board carefully, and the daily rewards reading it *fast*.
 *
 * A fixed interval rather than a random gap, on purpose. A rhythm the player
 * can feel is a rhythm they can play against - "time for one more move" is a
 * decision, where an unpredictable timer is just an interruption. Fifteen
 * seconds is short enough that reading the board IS the puzzle - there is no
 * time to plan a long clear-out, only to grab whatever is free right now.
 */
object DailyShuffle {

    /** Seconds between re-arrangements. */
    const val INTERVAL_SECONDS = 15

    /** When the countdown should start looking nervous - the last third. */
    const val WARN_SECONDS = 5
}
