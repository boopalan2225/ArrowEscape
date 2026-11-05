package com.clevercorner.arrowescape.game.level

import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Where speculative level building runs.
 *
 * One thread, at minimum priority, for the whole app. Prefetch used to go to
 * `Dispatchers.Default`, which sizes itself to the number of cores - so on a
 * four-core phone two speculative builds could take half the machine away
 * from the board the player is actually waiting for, at exactly the moment
 * it is composing a few hundred curves.
 *
 * Speculation is a bet. A bet is allowed to be slow; it is not allowed to
 * charge the player for losing.
 */
object GenerationDispatchers {

    /** Single-threaded and low priority, on purpose. See above. */
    val prefetch: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "level-prefetch").apply {
            priority = Thread.MIN_PRIORITY
            isDaemon = true
        }
    }.asCoroutineDispatcher()
}
