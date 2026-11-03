package com.clevercorner.arrowescape.game.model

/**
 * Represents the current status of the game.
 */
enum class GameStatus {
    PLAYING,
    OUT_OF_LIVES,
    LEVEL_COMPLETE,
    PAUSED
}

/**
 * Immutable representation of the complete game state.
 */
data class GameState(
    val level: Int = 1,
    val arrows: List<Arrow> = emptyList(),
    /** Bounds plus deflectors - everything the escape rules need. */
    val board: Board = Board(8, 8),
    val lives: Int = 3,
    val status: GameStatus = GameStatus.PLAYING,
    val mistakes: Int = 0,
    val difficultyLabel: String = "Easy",
    val hintArrowId: Int? = null
) {
    /**
     * Computed once per state, not once per read.
     *
     * This used to be a `get()` property that filtered the whole arrow list,
     * and so did everything derived from it - so a single recomposition
     * could walk a dense board several times over for an answer that cannot
     * change, because the state is immutable.
     */
    val activeArrows: List<Arrow> = arrows.filter { it.isActive }

    /** Number of arrows still on the board. */
    val remainingCount: Int
        get() = activeArrows.size

    val totalArrows: Int
        get() = arrows.size

    val clearedPercentage: Int
        get() = if (totalArrows == 0) 100 else ((totalArrows - remainingCount) * 100) / totalArrows

    val isLevelComplete: Boolean
        get() = remainingCount == 0 && status == GameStatus.PLAYING

    val isOutOfLives: Boolean
        get() = lives <= 0
}
