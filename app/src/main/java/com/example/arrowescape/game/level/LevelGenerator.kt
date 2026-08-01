package com.example.arrowescape.game.level

import com.example.arrowescape.game.engine.CollisionChecker
import com.example.arrowescape.game.engine.GameSolver
import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction
import kotlin.random.Random

/**
 * Generates solvable levels using reverse generation.
 *
 * The key insight: instead of generating a random board and checking solvability,
 * we BUILD the board backwards from an empty state, ensuring at each step
 * that at least one arrow can escape.
 */
object LevelGenerator {

    /**
     * Generates a level for the given level number.
     * The same level number always produces the same board (deterministic seeding).
     *
     * @param levelNumber The level to generate (1-indexed)
     * @return Triple of (arrows, gridRows, gridCols)
     */
    fun generateLevel(levelNumber: Int): Triple<List<ArrowTile>, Int, Int> {
        val config = DifficultyConfig.forLevel(levelNumber)
        val seed = levelNumber.toLong() * 31337 + 42 // Deterministic seed
        val random = Random(seed)

        val arrows = generateSolvableBoard(
            targetCount = config.arrowCount,
            gridRows = config.gridRows,
            gridCols = config.gridCols,
            densityFactor = config.densityFactor,
            random = random
        )

        // Verify solvability (should always pass due to reverse generation)
        require(GameSolver.isSolvable(arrows, config.gridRows, config.gridCols)) {
            "Generated level $levelNumber is not solvable - this is a bug!"
        }

        return Triple(arrows, config.gridRows, config.gridCols)
    }

    /**
     * Reverse generation algorithm:
     * 1. Start with empty board
     * 2. Add arrows one by one, each pointing TOWARD the board from an edge
     * 3. Ensure each new arrow doesn't create an unsolvable situation
     */
    private fun generateSolvableBoard(
        targetCount: Int,
        gridRows: Int,
        gridCols: Int,
        densityFactor: Float,
        random: Random
    ): List<ArrowTile> {
        val arrows = mutableListOf<ArrowTile>()
        var nextId = 0
        val occupiedPositions = mutableSetOf<Pair<Int, Int>>()
        var attempts = 0
        val maxAttempts = targetCount * 20

        while (arrows.size < targetCount && attempts < maxAttempts) {
            attempts++

            // Pick a random empty position
            val row = random.nextInt(gridRows)
            val col = random.nextInt(gridCols)
            val pos = row to col

            if (pos in occupiedPositions) continue

            // Pick a direction - bias toward edges for clearer initial solutions
            val direction = pickDirection(row, col, gridRows, gridCols, densityFactor, random)

            val newArrow = ArrowTile(
                id = nextId,
                row = row,
                col = col,
                direction = direction,
                isActive = true
            )

            // Test if adding this arrow keeps the board solvable
            val testBoard = arrows + newArrow
            if (GameSolver.isSolvable(testBoard, gridRows, gridCols)) {
                arrows.add(newArrow)
                occupiedPositions.add(pos)
                nextId++
            }
        }

        return shuffleArrowIds(arrows, random)
    }

    /**
     * Pick a direction with some intelligence to create interesting puzzles.
     */
    private fun pickDirection(
        row: Int,
        col: Int,
        gridRows: Int,
        gridCols: Int,
        densityFactor: Float,
        random: Random
    ): Direction {
        // Arrows near edges often point away from that edge (easier to escape)
        // Higher density = more random directions = harder puzzles
        val biasThreshold = 1.0f - densityFactor

        return if (random.nextFloat() < biasThreshold) {
            // Bias toward escaping direction based on position
            when {
                row == 0 -> Direction.UP
                row == gridRows - 1 -> Direction.DOWN
                col == 0 -> Direction.LEFT
                col == gridCols - 1 -> Direction.RIGHT
                else -> Direction.entries[random.nextInt(4)]
            }
        } else {
            // Random direction
            Direction.entries[random.nextInt(4)]
        }
    }

    /**
     * Shuffles arrow IDs so the solution order isn't predictable from ID sequence.
     */
    private fun shuffleArrowIds(arrows: List<ArrowTile>, random: Random): List<ArrowTile> {
        val shuffled = arrows.shuffled(random)
        return shuffled.mapIndexed { index, arrow ->
            arrow.copy(id = index)
        }
    }

    /**
     * Generates a simple tutorial level (level 1) that's trivially solvable.
     */
    fun generateTutorialLevel(): Triple<List<ArrowTile>, Int, Int> {
        // Hand-crafted simple level for learning
        val arrows = listOf(
            ArrowTile(0, 2, 1, Direction.UP),
            ArrowTile(1, 1, 2, Direction.RIGHT),
            ArrowTile(2, 2, 2, Direction.DOWN),
            ArrowTile(3, 2, 0, Direction.LEFT),
            ArrowTile(4, 0, 2, Direction.UP)
        )
        return Triple(arrows, 4, 4)
    }
}
