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
 *
 * @param level Current level number (1-indexed)
 * @param arrows All arrows on the board (active and removed)
 * @param lives Remaining lives (0-3)
 * @param status Current game status
 * @param startTimeMillis When this level started (for completion time tracking)
 * @param mistakes Number of invalid moves made this level
 */
data class GameState(
    val level: Int = 1,
    val arrows: List<ArrowTile> = emptyList(),
    val lives: Int = 3,
    val status: GameStatus = GameStatus.PLAYING,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val mistakes: Int = 0
) {
    /**
     * Returns only the active (not yet removed) arrows.
     */
    val activeArrows: List<ArrowTile>
        get() = arrows.filter { it.isActive }

    /**
     * Returns the count of remaining arrows to clear.
     */
    val remainingCount: Int
        get() = activeArrows.size

    /**
     * Returns the total number of arrows this level started with.
     */
    val totalArrows: Int
        get() = arrows.size

    /**
     * Returns the percentage of arrows cleared (0-100).
     */
    val clearedPercentage: Int
        get() = if (totalArrows == 0) 100 else ((totalArrows - remainingCount) * 100) / totalArrows

    /**
     * Returns true if the level is complete (all arrows removed).
     */
    val isLevelComplete: Boolean
        get() = remainingCount == 0 && status == GameStatus.PLAYING

    /**
     * Returns true if the player has run out of lives.
     */
    val isOutOfLives: Boolean
        get() = lives <= 0
}
