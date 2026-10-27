package com.clevercorner.arrowescape.ui.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A stack of board snapshots to rewind to, newest last.
 *
 * The rule lives here - newest comes out first, and empty means there is
 * nothing to undo. The ViewModel only decides *when* to remember and when to
 * forget, so the stack itself cannot drift out of step with the board.
 */
class UndoHistory<T> {

    private val snapshots = ArrayDeque<T>()

    private val _canUndo = MutableStateFlow(false)

    /** Whether there is anything to rewind to. Drives the Undo control. */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    fun remember(snapshot: T) {
        snapshots.addLast(snapshot)
        _canUndo.value = true
    }

    /** Removes and returns the most recent snapshot, or null if there is none. */
    fun pop(): T? {
        if (snapshots.isEmpty()) return null
        val latest = snapshots.removeLast()
        _canUndo.value = snapshots.isNotEmpty()
        return latest
    }

    fun clear() {
        snapshots.clear()
        _canUndo.value = false
    }
}
