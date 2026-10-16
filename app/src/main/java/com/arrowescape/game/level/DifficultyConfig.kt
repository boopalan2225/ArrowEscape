package com.arrowescape.game.level

/**
 * Configuration for level difficulty based on level number.
 *
 * Difficulty scales through:
 * 1. Grid size: More cells = more arrows to clear
 * 2. Snake length: Longer bendable arrows = denser, more strategic boards
 * 3. Shape complexity: Later levels use more intricate shapes
 *
 * Grid sizes scale aggressively for hard levels:
 * - Level 1-10:   5x5 to 7x7   (Easy - learning the mechanics)
 * - Level 11-30:  8x8 to 10x10 (Normal - building skill)
 * - Level 31-60:  11x11 to 14x14 (Hard - serious challenge)
 * - Level 61-100: 15x15 to 18x18 (Expert - dense puzzles)
 * - Level 101-130: 19x19 to 22x22 (Master - very dense)
 * - Level 131-150: 23x23 to 26x26 (Grandmaster - maximum density)
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
        const val TOTAL_LEVELS = 150

        fun forLevel(level: Int): DifficultyConfig {
            val shape = LevelShape.forLevel(level)

            // Progressive grid size scaling - much larger for hard levels
            val (size, maxLen, label) = when {
                level <= 3 -> Triple(5, 2, "Easy")
                level <= 6 -> Triple(6, 2, "Easy")
                level <= 10 -> Triple(7, 3, "Easy")
                level <= 15 -> Triple(8, 3, "Normal")
                level <= 20 -> Triple(9, 3, "Normal")
                level <= 30 -> Triple(10, 4, "Normal")
                level <= 40 -> Triple(11, 4, "Hard")
                level <= 50 -> Triple(12, 4, "Hard")
                level <= 60 -> Triple(13, 5, "Hard")
                level <= 70 -> Triple(14, 5, "Expert")
                level <= 80 -> Triple(15, 5, "Expert")
                level <= 90 -> Triple(16, 6, "Expert")
                level <= 100 -> Triple(17, 6, "Expert")
                level <= 110 -> Triple(18, 6, "Master")
                level <= 120 -> Triple(19, 6, "Master")
                level <= 130 -> Triple(20, 7, "Master")
                level <= 140 -> Triple(22, 7, "Grandmaster")
                else -> Triple(24, 8, "Grandmaster")
            }

            return DifficultyConfig(
                gridRows = size,
                gridCols = size,
                maxSnakeLength = maxLen,
                shape = shape,
                difficultyLabel = label
            )
        }

        /**
         * Get difficulty tier name for UI display.
         */
        fun tierForLevel(level: Int): String = when {
            level <= 10 -> "Beginner"
            level <= 30 -> "Intermediate"
            level <= 60 -> "Advanced"
            level <= 100 -> "Expert"
            level <= 130 -> "Master"
            else -> "Grandmaster"
        }
    }
}
