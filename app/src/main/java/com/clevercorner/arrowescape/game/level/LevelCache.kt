package com.clevercorner.arrowescape.game.level

import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

/**
 * A tiny thread-safe LRU for generated levels, with single-flight builds.
 *
 * Level generation is deterministic in the level number, so the same board
 * can be rebuilt from scratch every time - and used to be. That is pure
 * waste the moment a player steps back a level, replays one, or bounces
 * between two with the prev/next buttons, and it is waste the player *sees*
 * as a stalled, empty board.
 *
 * Two rules, and the second one is the subtle half:
 *
 *  1. **One build per key.** Two callers asking for the same level queue on
 *     one build instead of racing to produce identical boards.
 *  2. **Builds of *different* keys never queue on each other.** This class
 *     used to be one `@Synchronized` method, which meant the lock was held
 *     for the whole build - so a speculative prefetch of level N+2 blocked a
 *     player-requested build of N+1 until it finished. The player waited on
 *     work nobody asked for. Locks here now cover map bookkeeping only; the
 *     build itself runs outside them.
 *
 * Safe only because generated levels are immutable: both arrow types hold
 * `isActive` as a `val` and the engines update state with `copy`. If that
 * ever changes, this cache would start handing out half-finished boards -
 * hence this paragraph.
 */
internal class LevelCache<K : Any, V : Any>(private val capacity: Int = 4) {

    private val entries = object : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>): Boolean =
            size > capacity
    }

    /** Builds running right now. The value is shared by owner and joiners alike. */
    private val inFlight = ConcurrentHashMap<K, FutureTask<V>>()

    /**
     * Returns the cached level for [key], building it if absent.
     *
     * [build] may abandon itself by throwing [CancellationException] - see
     * [GenerationGuard]. An abandoned build is not cached, and any caller
     * still waiting on it simply builds the level itself on the next lap,
     * because one coroutine giving up says nothing about what the others
     * still want.
     */
    fun getOrPut(key: K, build: () -> V): V {
        while (true) {
            cached(key)?.let { return it }

            var claimed: FutureTask<V>? = null
            val task = inFlight.computeIfAbsent(key) {
                FutureTask(Callable(build)).also { claimed = it }
            }
            val owned = task === claimed

            if (owned) {
                // The owner builds on its own thread - no pool, no handoff,
                // so generation keeps the caller's priority and its stack.
                try {
                    task.run()
                } finally {
                    inFlight.remove(key, task)
                }
            }

            try {
                return resultOf(task).also { cache(key, it) }
            } catch (abandoned: CancellationException) {
                if (owned) throw abandoned
                // Somebody else's build gave up. We have not.
            }
        }
    }

    @Synchronized
    private fun cached(key: K): V? = entries[key]

    @Synchronized
    private fun cache(key: K, value: V) {
        entries[key] = value
    }

    /** Unwraps the [ExecutionException] a [FutureTask] wraps every failure in. */
    private fun resultOf(task: FutureTask<V>): V = try {
        task.get()
    } catch (failed: ExecutionException) {
        throw failed.cause ?: failed
    }
}
