package com.example.arrowescape.game.level

/**
 * Configuration for level difficulty based on level number.
 *
 * Difficulty is driven by grid size (more cells = more arrows) and by
 * maximum snake length (longer bendable arrows = denser, more strategic
 * boards). Grids are square so shapes render accurately.
 */
data class DifficultyConfig(
    val gridRows: Int,
    val gridCols: Int,
    val maxSnakeLength: Int,
    val shape: LevelShape,
    val difficultyLabel: String
) {
    companion object {
        /** Total number of designed levels in the game. */
        const val TOTAL_LEVELS = 100

        fun forLevel(level: Int): DifficultyConfig {
            val shape = LevelShape.HEART

            val (size, maxLen, label) = when {
                level <= 3 -> Triple(4, 2, "Easy")
                level <= 8 -> Triple(5, 3, "Easy")
                level <= 15 -> Triple(6, 3, "Easy")
                level <= 25 -> Triple(7, 4, "Normal")
                level <= 40 -> Triple(8, 4, "Normal")
                level <= 60 -> Triple(9, 5, "Hard")
                level <= 85 -> Triple(10, 5, "Hard")
                else -> Triple(11, 6, "Expert")
            }

            return DifficultyConfig(
                gridRows = size,
                gridCols = size,
                maxSnakeLength = maxLen,
                shape = shape,
                difficultyLabel = label
            )
        }
    }
}
