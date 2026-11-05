package com.clevercorner.arrowescape.game.level

import java.util.concurrent.CancellationException

/**
 * Lets a generator notice that nobody is waiting for its result any more.
 *
 * Level generation is a long stretch of plain, non-suspending arithmetic, so
 * `job.cancel()` has no effect on it whatsoever: a cancelled prefetch used to
 * run happily to completion, burning a core the player's own board needed.
 * Cancellation has to be *cooperative*, which means somebody has to ask.
 *
 * Deliberately coarse. Generators call [check] between whole units of work -
 * a region, a candidate board, a block of climb steps - not inside their
 * inner loops. The aim is to stop speculative work within a few hundred
 * milliseconds, not to make the hot path pay for a question whose answer is
 * nearly always "no".
 */
fun interface GenerationGuard {

    /** True once the result of the work in progress would be thrown away. */
    fun isAbandoned(): Boolean

    /**
     * Abandons the build if nobody is waiting for it.
     *
     * Throws [CancellationException] so a coroutine sees a plain, ordinary
     * cancellation rather than a failure - the build did not go wrong, it
     * stopped being wanted.
     */
    fun check() {
        if (isAbandoned()) throw CancellationException("level generation abandoned")
    }

    companion object {
        /** For callers that will use whatever is produced. */
        val NEVER = GenerationGuard { false }
    }
}
