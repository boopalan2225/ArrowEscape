package com.clevercorner.arrowescape.game.engine

import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.GameState
import com.clevercorner.arrowescape.game.model.GameStatus

/**
 * The core game engine - source of truth for all game rules.
 * Independent of UI, fully testable.
 */
class GameEngine {

    companion object {
        const val MAX_LIVES = 3
        const val DEFAULT_GRID_ROWS = 8
        const val DEFAULT_GRID_COLS = 8
    }

    private var gridRows = DEFAULT_GRID_ROWS
    private var gridCols = DEFAULT_GRID_COLS

    fun createLevel(
        level: Int,
        arrows: List<Arrow>,
        rows: Int = DEFAULT_GRID_ROWS,
        cols: Int = DEFAULT_GRID_COLS,
        difficultyLabel: String = "Easy"
    ): GameState {
        gridRows = rows
        gridCols = cols
        return GameState(
            level = level,
            arrows = arrows,
            lives = MAX_LIVES,
            status = GameStatus.PLAYING,
            startTimeMillis = System.currentTimeMillis(),
            mistakes = 0,
            difficultyLabel = difficultyLabel
        )
    }

    /**
     * Attempts to tap/remove the specified arrow.
     * @return Pair of (newState, wasValidMove)
     */
    fun tapArrow(state: GameState, arrowId: Int): Pair<GameState, Boolean> {
        if (state.status != GameStatus.PLAYING) return state to false

        val arrow = state.arrows.find { it.id == arrowId && it.isActive }
            ?: return state to false

        val canEscape = CollisionChecker.canEscape(
            arrow, state.activeArrows, gridRows, gridCols
        )

        return if (canEscape) handleValidMove(state, arrowId)
        else handleInvalidMove(state)
    }

    private fun handleValidMove(state: GameState, arrowId: Int): Pair<GameState, Boolean> {
        val updated = state.arrows.map {
            if (it.id == arrowId) it.copy(isActive = false) else it
        }
        var newState = state.copy(arrows = updated)
        if (newState.remainingCount == 0) {
            newState = newState.copy(status = GameStatus.LEVEL_COMPLETE)
        }
        return newState to true
    }

    private fun handleInvalidMove(state: GameState): Pair<GameState, Boolean> {
        val newLives = state.lives - 1
        var newState = state.copy(lives = newLives, mistakes = state.mistakes + 1)
        if (newLives <= 0) newState = newState.copy(status = GameStatus.OUT_OF_LIVES)
        return newState to false
    }

    fun restoreLives(state: GameState): GameState =
        state.copy(lives = MAX_LIVES, status = GameStatus.PLAYING)

    fun findHintArrow(state: GameState): Int? =
        CollisionChecker.getEscapableArrows(state.activeArrows, gridRows, gridCols)
            .firstOrNull()?.id

    fun getCompletionTimeSeconds(state: GameState): Int =
        ((System.currentTimeMillis() - state.startTimeMillis) / 1000).toInt()

    fun getGridRows(): Int = gridRows
    fun getGridCols(): Int = gridCols
}
