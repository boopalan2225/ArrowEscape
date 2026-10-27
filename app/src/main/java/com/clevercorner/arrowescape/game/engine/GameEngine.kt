package com.clevercorner.arrowescape.game.engine

import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board
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

    /**
     * Outcome of a tap. [escape] is null when the move was illegal; otherwise
     * it carries the route every departing arrow takes, which is exactly what
     * the renderer needs to animate a deflected exit.
     */
    data class TapResult(val state: GameState, val escape: CollisionChecker.Escape?) {
        val wasValid: Boolean get() = escape != null
    }

    fun createLevel(
        level: Int,
        arrows: List<Arrow>,
        board: Board,
        difficultyLabel: String = "Easy"
    ): GameState = GameState(
        level = level,
        arrows = arrows,
        board = board,
        lives = MAX_LIVES,
        status = GameStatus.PLAYING,
        mistakes = 0,
        difficultyLabel = difficultyLabel
    )

    fun createLevel(
        level: Int,
        arrows: List<Arrow>,
        rows: Int = DEFAULT_GRID_ROWS,
        cols: Int = DEFAULT_GRID_COLS,
        difficultyLabel: String = "Easy"
    ): GameState = createLevel(level, arrows, Board(rows, cols), difficultyLabel)

    /**
     * Attempts to tap/remove the specified arrow. A colour-locked arrow takes
     * its partner with it.
     */
    fun tapArrow(state: GameState, arrowId: Int): TapResult {
        if (state.status != GameStatus.PLAYING) return TapResult(state, null)

        val arrow = state.arrows.find { it.id == arrowId && it.isActive }
            ?: return TapResult(state, null)

        val escape = CollisionChecker.escapeFor(arrow, state.activeArrows, state.board)
            ?: return TapResult(penalise(state), null)

        return TapResult(clear(state, escape.arrowIds), escape)
    }

    private fun clear(state: GameState, departing: Set<Int>): GameState {
        val updated = state.arrows.map {
            if (it.id in departing) it.copy(isActive = false) else it
        }
        val newState = state.copy(arrows = updated)
        return if (newState.remainingCount == 0) {
            newState.copy(status = GameStatus.LEVEL_COMPLETE)
        } else {
            newState
        }
    }

    private fun penalise(state: GameState): GameState {
        val newLives = state.lives - 1
        val newState = state.copy(lives = newLives, mistakes = state.mistakes + 1)
        return if (newLives <= 0) newState.copy(status = GameStatus.OUT_OF_LIVES) else newState
    }

    fun restoreLives(state: GameState): GameState =
        state.copy(lives = MAX_LIVES, status = GameStatus.PLAYING)

    /**
     * Hints follow the solver, not just any open arrow: on a board laced with
     * deflectors and locked pairs, "a legal move" and "a move on the way to
     * the win" are worth telling apart.
     */
    fun findHintArrow(state: GameState): Int? =
        GameSolver.solve(state.activeArrows, state.board)?.order?.firstOrNull()
            ?: CollisionChecker.getEscapableArrows(state.activeArrows, state.board)
                .firstOrNull()?.id
}
