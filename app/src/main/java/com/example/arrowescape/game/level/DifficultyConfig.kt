package com.example.arrowescape.game.level

/**
 * Configuration for level difficulty based on level number.
 */
data class DifficultyConfig(
    val arrowCount: Int,
    val gridRows: Int,
    val gridCols: Int,
    val densityFactor: Float // 0.0 to 1.0, higher = more blocking situations
) {
    companion object {
        /**
         * Gets the difficulty configuration for a given level.
         */
        fun forLevel(level: Int): DifficultyConfig {
            return when {
                level <= 5 -> DifficultyConfig(
                    arrowCount = 4 + level,           // 5-9 arrows
                    gridRows = 5,
                    gridCols = 4,
                    densityFactor = 0.2f
                )
                level <= 15 -> DifficultyConfig(
                    arrowCount = 8 + (level - 5),     // 9-18 arrows
                    gridRows = 6,
                    gridCols = 5,
                    densityFactor = 0.35f
                )
                level <= 30 -> DifficultyConfig(
                    arrowCount = 15 + (level - 15) / 2, // 15-22 arrows
                    gridRows = 7,
                    gridCols = 5,
                    densityFactor = 0.5f
                )
                level <= 60 -> DifficultyConfig(
                    arrowCount = 20 + (level - 30) / 3, // 20-30 arrows
                    gridRows = 8,
                    gridCols = 6,
                    densityFactor = 0.6f
                )
                level <= 100 -> DifficultyConfig(
                    arrowCount = 28 + (level - 60) / 4, // 28-38 arrows
                    gridRows = 8,
                    gridCols = 6,
                    densityFactor = 0.7f
                )
                else -> DifficultyConfig(
                    arrowCount = minOf(45, 35 + (level - 100) / 10), // 35-45 arrows, capped
                    gridRows = 9,
                    gridCols = 7,
                    densityFactor = 0.8f
                )
            }
        }
    }
}
