package com.example.arrowescape.game.model

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
    val lives: Int = 3,
    val status: GameStatus = GameStatus.PLAYING,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val mistakes: Int = 0,
    val difficultyLabel: String = "Easy",
    val hintArrowId: Int? = null
) {
    val activeArrows: List<Arrow>
        get() = arrows.filter { it.isActive }

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
