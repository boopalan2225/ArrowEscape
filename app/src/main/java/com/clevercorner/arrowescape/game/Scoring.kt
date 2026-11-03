package com.clevercorner.arrowescape.game

/**
 * How a finished board is scored.
 *
 * One rule, one place. This arithmetic lived in three copies - the curved
 * ViewModel, the classic one, and the win card that *displays* the result -
 * which meant the number a player saw and the number saved to disk were
 * only equal by luck and vigilance.
 */
object Scoring {

    /** Flawless earns three; a slip or two earns two; anything more earns one. */
    fun starsFor(mistakes: Int): Int = when {
        mistakes == 0 -> 3
        mistakes <= 2 -> 2
        else -> 1
    }
}
