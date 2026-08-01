package com.example.arrowescape.game.level

/**
 * Configuration for level difficulty based on level number.
 *
 * Shapes are now FULLY filled, so difficulty is driven by grid size
 * (bigger grid = more arrows = harder). Grids are square so shapes
 * render accurately (round circles, proportioned hearts, etc.).
 */
data class DifficultyConfig(
    val gridRows: Int,
    val gridCols: Int,
    val shape: LevelShape,
    val difficultyLabel: String
) {
    companion object {
        /** Total number of designed levels in the game. */
        const val TOTAL_LEVELS = 100

        fun forLevel(level: Int): DifficultyConfig {
            val shape = LevelShape.forLevel(level)

            val (size, label) = when {
                level <= 3 -> 4 to "Easy"
                level <= 8 -> 5 to "Easy"
                level <= 15 -> 6 to "Easy"
                level <= 25 -> 7 to "Normal"
                level <= 40 -> 8 to "Normal"
                level <= 60 -> 9 to "Hard"
                level <= 85 -> 10 to "Hard"
                else -> 11 to "Expert"
            }

            return DifficultyConfig(
                gridRows = size,
                gridCols = size,
                shape = shape,
                difficultyLabel = label
            )
        }
    }
}
