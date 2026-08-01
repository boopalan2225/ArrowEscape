package com.example.arrowescape.game.level

import com.example.arrowescape.game.engine.GameSolver
import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction
import kotlin.random.Random

/**
 * The full result of generating a level.
 */
data class GeneratedLevel(
    val arrows: List<ArrowTile>,
    val gridRows: Int,
    val gridCols: Int,
    val shape: LevelShape,
    val difficultyLabel: String
)

/**
 * Generates solvable levels using reverse generation, with arrows arranged
 * into recognizable shapes (triangle, circle, heart, diamond, etc.).
 *
 * Key ideas:
 * - Arrows are only placed in cells that fall inside the level's shape mask.
 * - Boards are built so at least one valid solution always exists.
 * - The same level number always produces the same board (deterministic seed).
 */
object LevelGenerator {

    /**
     * Generates a level for the given level number.
     */
    fun generateLevel(levelNumber: Int): GeneratedLevel {
        val config = DifficultyConfig.forLevel(levelNumber)
        val seed = levelNumber.toLong() * 31337 + 42
        val random = Random(seed)

        val validCells = config.shape.validCells(config.gridRows, config.gridCols)

        val arrows = generateSolvableBoard(
            targetCount = config.arrowCount.coerceAtMost(validCells.size),
            gridRows = config.gridRows,
            gridCols = config.gridCols,
            validCells = validCells,
            densityFactor = config.densityFactor,
            random = random
        )

        require(GameSolver.isSolvable(arrows, config.gridRows, config.gridCols)) {
            "Generated level $levelNumber is not solvable - this is a bug!"
        }

        return GeneratedLevel(
            arrows = arrows,
            gridRows = config.gridRows,
            gridCols = config.gridCols,
            shape = config.shape,
            difficultyLabel = config.difficultyLabel
        )
    }

    /**
     * Reverse generation: add arrows one at a time, only keeping a new arrow
     * if the board remains solvable. Positions are restricted to shape cells.
     */
    private fun generateSolvableBoard(
        targetCount: Int,
        gridRows: Int,
        gridCols: Int,
        validCells: List<Pair<Int, Int>>,
        densityFactor: Float,
        random: Random
    ): List<ArrowTile> {
        val arrows = mutableListOf<ArrowTile>()
        var nextId = 0
        val occupied = mutableSetOf<Pair<Int, Int>>()

        // Shuffle valid cells once so placement follows the shape but is varied
        val shuffledCells = validCells.shuffled(random)
        var cellIndex = 0
        var attempts = 0
        val maxAttempts = targetCount * 30

        while (arrows.size < targetCount && attempts < maxAttempts) {
            attempts++

            // Cycle through shuffled shape cells
            val pos = shuffledCells[cellIndex % shuffledCells.size]
            cellIndex++

            if (pos in occupied) continue

            val (row, col) = pos
            val direction = pickDirection(row, col, gridRows, gridCols, densityFactor, random)

            val newArrow = ArrowTile(
                id = nextId,
                row = row,
                col = col,
                direction = direction,
                isActive = true
            )

            if (GameSolver.isSolvable(arrows + newArrow, gridRows, gridCols)) {
                arrows.add(newArrow)
                occupied.add(pos)
                nextId++
            }
        }

        return shuffleArrowIds(arrows, random)
    }

    private fun pickDirection(
        row: Int,
        col: Int,
        gridRows: Int,
        gridCols: Int,
        densityFactor: Float,
        random: Random
    ): Direction {
        val biasThreshold = 1.0f - densityFactor
        return if (random.nextFloat() < biasThreshold) {
            when {
                row <= gridRows / 4 -> Direction.UP
                row >= gridRows * 3 / 4 -> Direction.DOWN
                col <= gridCols / 4 -> Direction.LEFT
                col >= gridCols * 3 / 4 -> Direction.RIGHT
                else -> Direction.entries[random.nextInt(4)]
            }
        } else {
            Direction.entries[random.nextInt(4)]
        }
    }

    private fun shuffleArrowIds(arrows: List<ArrowTile>, random: Random): List<ArrowTile> {
        return arrows.shuffled(random).mapIndexed { index, arrow ->
            arrow.copy(id = index)
        }
    }

    /**
     * Simple hand-crafted tutorial level (level 1).
     */
    fun generateTutorialLevel(): GeneratedLevel {
        val arrows = listOf(
            ArrowTile(0, 2, 2, Direction.UP),
            ArrowTile(1, 1, 3, Direction.RIGHT),
            ArrowTile(2, 3, 2, Direction.DOWN),
            ArrowTile(3, 2, 1, Direction.LEFT),
            ArrowTile(4, 0, 3, Direction.UP)
        )
        return GeneratedLevel(
            arrows = arrows,
            gridRows = 6,
            gridCols = 6,
            shape = LevelShape.SQUARE,
            difficultyLabel = "Easy"
        )
    }
}
