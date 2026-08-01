package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.GameState
import com.example.arrowescape.game.model.GameStatus

/**
 * The core game engine - source of truth for all game rules.
 * Independent of UI, fully testable.
 */
class GameEngine {

    companion object {
        const val MAX_LIVES = 3
        const val DEFAULT_GRID_ROWS = 8
        const val DEFAULT_GRID_COLS = 6
    }

    private var gridRows = DEFAULT_GRID_ROWS
    private var gridCols = DEFAULT_GRID_COLS

    /**
     * Creates a new game state for the given level.
     */
    fun createLevel(level: Int, arrows: List<ArrowTile>, rows: Int = DEFAULT_GRID_ROWS, cols: Int = DEFAULT_GRID_COLS): GameState {
        gridRows = rows
        gridCols = cols
        return GameState(
            level = level,
            arrows = arrows,
            lives = MAX_LIVES,
            status = GameStatus.PLAYING,
            startTimeMillis = System.currentTimeMillis(),
            mistakes = 0
        )
    }

    /**
     * Attempts to tap/remove the specified arrow.
     *
     * @return Pair of (newState, wasValidMove)
     */
    fun tapArrow(state: GameState, arrowId: Int): Pair<GameState, Boolean> {
        if (state.status != GameStatus.PLAYING) {
            return state to false
        }

        val arrow = state.arrows.find { it.id == arrowId && it.isActive }
            ?: return state to false

        val canEscape = CollisionChecker.canEscape(
            arrow = arrow,
            activeArrows = state.activeArrows,
            gridRows = gridRows,
            gridCols = gridCols
        )

        return if (canEscape) {
            handleValidMove(state, arrowId)
        } else {
            handleInvalidMove(state)
        }
    }

    private fun handleValidMove(state: GameState, arrowId: Int): Pair<GameState, Boolean> {
        // Mark the arrow as inactive
        val updatedArrows = state.arrows.map { arrow ->
            if (arrow.id == arrowId) arrow.copy(isActive = false) else arrow
        }

        var newState = state.copy(arrows = updatedArrows)

        // Check for level completion
        if (newState.remainingCount == 0) {
            newState = newState.copy(status = GameStatus.LEVEL_COMPLETE)
        }

        return newState to true
    }

    private fun handleInvalidMove(state: GameState): Pair<GameState, Boolean> {
        val newLives = state.lives - 1
        val newMistakes = state.mistakes + 1

        var newState = state.copy(
            lives = newLives,
            mistakes = newMistakes
        )

        // Check for out of lives
        if (newLives <= 0) {
            newState = newState.copy(status = GameStatus.OUT_OF_LIVES)
        }

        return newState to false
    }

    /**
     * Restores lives after watching a rewarded ad.
     */
    fun restoreLives(state: GameState): GameState {
        return state.copy(
            lives = MAX_LIVES,
            status = GameStatus.PLAYING
        )
    }

    /**
     * Restarts the current level.
     */
    fun restartLevel(state: GameState, arrows: List<ArrowTile>): GameState {
        return createLevel(state.level, arrows, gridRows, gridCols)
    }

    /**
     * Pauses the game.
     */
    fun pause(state: GameState): GameState {
        return if (state.status == GameStatus.PLAYING) {
            state.copy(status = GameStatus.PAUSED)
        } else {
            state
        }
    }

    /**
     * Resumes the game.
     */
    fun resume(state: GameState): GameState {
        return if (state.status == GameStatus.PAUSED) {
            state.copy(status = GameStatus.PLAYING)
        } else {
            state
        }
    }

    /**
     * Checks if a specific arrow can currently escape.
     */
    fun canArrowEscape(state: GameState, arrowId: Int): Boolean {
        val arrow = state.arrows.find { it.id == arrowId && it.isActive }
            ?: return false

        return CollisionChecker.canEscape(
            arrow = arrow,
            activeArrows = state.activeArrows,
            gridRows = gridRows,
            gridCols = gridCols
        )
    }

    /**
     * Gets the completion time for the level in seconds.
     */
    fun getCompletionTimeSeconds(state: GameState): Int {
        return ((System.currentTimeMillis() - state.startTimeMillis) / 1000).toInt()
    }

    fun getGridRows(): Int = gridRows
    fun getGridCols(): Int = gridCols
}
