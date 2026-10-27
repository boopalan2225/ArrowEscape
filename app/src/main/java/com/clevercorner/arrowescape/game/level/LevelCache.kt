package com.clevercorner.arrowescape.game.level

/**
 * A tiny thread-safe LRU for generated levels.
 *
 * Level generation is deterministic in the level number, so the same board
 * can be rebuilt from scratch every time - and used to be. That is pure
 * waste the moment a player steps back a level, replays one, or bounces
 * between two with the prev/next buttons, and it is waste the player *sees*
 * as a stalled, empty board.
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

    /**
     * Returns the cached level for [key], building it if absent.
     *
     * Synchronised across the whole build on purpose: two coroutines racing
     * for the same level should queue rather than both generate it.
     */
    @Synchronized
    fun getOrPut(key: K, build: () -> V): V = entries.getOrPut(key, build)
}
