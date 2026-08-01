package com.example.arrowescape.game.level

/**
 * Configuration for level difficulty based on level number.
 */
data class DifficultyConfig(
    val arrowCount: Int,
    val gridRows: Int,
    val gridCols: Int,
    val densityFactor: Float, // 0.0 to 1.0, higher = more blocking situations
    val shape: LevelShape,
    val difficultyLabel: String
) {
    companion object {
        /**
         * Gets the difficulty configuration for a given level.
         *
         * Grids are larger than a plain rectangle would need so that shapes
         * (triangle, circle, heart, etc.) are clearly recognizable. Arrow
         * count is derived from the number of cells the shape actually fills.
         */
        fun forLevel(level: Int): DifficultyConfig {
            val shape = LevelShape.forLevel(level)

            val (rows, cols, density, label) = when {
                level <= 5 -> Grid(6, 6, 0.2f, "Easy")
                level <= 15 -> Grid(8, 7, 0.35f, "Easy")
                level <= 30 -> Grid(9, 8, 0.5f, "Normal")
                level <= 60 -> Grid(10, 9, 0.6f, "Normal")
                level <= 100 -> Grid(11, 9, 0.7f, "Hard")
                else -> Grid(12, 10, 0.8f, "Expert")
            }

            // Count how many cells this shape fills, then use a fraction of them
            val availableCells = shape.validCells(rows, cols).size
            val fillRatio = when {
                level <= 5 -> 0.35f
                level <= 15 -> 0.45f
                level <= 40 -> 0.55f
                level <= 80 -> 0.65f
                else -> 0.72f
            }
            val arrowCount = (availableCells * fillRatio).toInt().coerceIn(4, 60)

            return DifficultyConfig(
                arrowCount = arrowCount,
                gridRows = rows,
                gridCols = cols,
                densityFactor = density,
                shape = shape,
                difficultyLabel = label
            )
        }

        private data class Grid(
            val rows: Int,
            val cols: Int,
            val density: Float,
            val label: String
        )
    }
}
