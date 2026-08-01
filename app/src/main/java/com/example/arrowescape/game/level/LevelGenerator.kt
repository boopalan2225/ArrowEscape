package com.example.arrowescape.game.level

import com.example.arrowescape.game.engine.GameSolver
import com.example.arrowescape.game.model.Arrow
import com.example.arrowescape.game.model.Cell
import com.example.arrowescape.game.model.Direction
import kotlin.random.Random

data class GeneratedLevel(
    val arrows: List<Arrow>,
    val gridRows: Int,
    val gridCols: Int,
    val shape: LevelShape,
    val difficultyLabel: String
)

/**
 * Generates dense shape levels made of BENDABLE snake-arrows, guaranteed
 * solvable via reverse-peeling.
 *
 * Strategy:
 *  1. Fill every cell of the shape mask (crisp, dense silhouettes).
 *  2. Repeatedly carve out a "snake": pick a cell whose straight ray in some
 *     direction is clear of remaining cells, then grow a connected body
 *     (with bends) where EVERY body cell also has a clear ray in that
 *     direction. Remove it and record it.
 *  3. Because snakes are carved in a real removal order, replaying that order
 *     is always a valid solution -> solvable.
 */
object LevelGenerator {

    fun generateLevel(levelNumber: Int): GeneratedLevel {
        val config = DifficultyConfig.forLevel(levelNumber)
        val random = Random(levelNumber.toLong() * 31337 + 42)

        val cells = config.shape.validCells(config.gridRows, config.gridCols)
        val arrows = carveSnakes(
            cells, config.gridRows, config.gridCols, config.maxSnakeLength, random
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

    private fun carveSnakes(
        shapeCells: List<Pair<Int, Int>>,
        rows: Int,
        cols: Int,
        maxLen: Int,
        random: Random
    ): List<Arrow> {
        val occupied = shapeCells.mapTo(HashSet()) { Cell(it.first, it.second) }
        val arrows = mutableListOf<Arrow>()
        var nextId = 0

        while (occupied.isNotEmpty()) {
            // Head candidates: cells with at least one clear escape direction.
            val candidates = occupied.filter { escapeDirs(it, occupied, rows, cols).isNotEmpty() }
            val head = candidates.random(random)
            val dir = escapeDirs(head, occupied, rows, cols).random(random)

            val path = ArrayDeque<Cell>()
            path.addLast(head)
            val snake = hashSetOf(head)

            val targetLen = random.nextInt(1, maxLen + 1)

            // First tail cell sits directly behind the head so the tip aligns.
            val behind = Cell(head.row - dir.deltaRow(), head.col - dir.deltaCol())
            if (path.size < targetLen && canJoin(behind, dir, occupied, snake, rows, cols)) {
                path.addFirst(behind); snake.add(behind)
            }

            // Grow the body (bends allowed) from the tail end.
            while (path.size < targetLen) {
                val tail = path.first()
                val options = neighbors(tail).filter {
                    canJoin(it, dir, occupied, snake, rows, cols)
                }
                if (options.isEmpty()) break
                val next = options.random(random)
                path.addFirst(next); snake.add(next)
            }

            occupied.removeAll(snake)
            arrows.add(Arrow(id = nextId++, cells = path.toList(), direction = dir))
        }

        // Shuffle ids so solution order isn't guessable from id sequence.
        return arrows.shuffled(random).mapIndexed { i, a -> a.copy(id = i) }
    }

    /** A candidate cell can join if it's occupied, unused, and its ray is clear. */
    private fun canJoin(
        cell: Cell,
        dir: Direction,
        occupied: Set<Cell>,
        snake: Set<Cell>,
        rows: Int,
        cols: Int
    ): Boolean {
        if (cell !in occupied || cell in snake) return false
        // Blockers = everything still occupied except this snake (incl. the new cell).
        return rayClear(cell, dir, occupied, snake + cell, rows, cols)
    }

    /** Directions in which [cell]'s ray to the edge is clear of other occupied cells. */
    private fun escapeDirs(
        cell: Cell,
        occupied: Set<Cell>,
        rows: Int,
        cols: Int
    ): List<Direction> = Direction.entries.filter {
        rayClear(cell, it, occupied, setOf(cell), rows, cols)
    }

    private fun rayClear(
        cell: Cell,
        dir: Direction,
        occupied: Set<Cell>,
        exclude: Set<Cell>,
        rows: Int,
        cols: Int
    ): Boolean {
        var r = cell.row + dir.deltaRow()
        var c = cell.col + dir.deltaCol()
        while (r in 0 until rows && c in 0 until cols) {
            val here = Cell(r, c)
            if (here in occupied && here !in exclude) return false
            r += dir.deltaRow()
            c += dir.deltaCol()
        }
        return true
    }

    private fun neighbors(cell: Cell): List<Cell> = listOf(
        Cell(cell.row - 1, cell.col),
        Cell(cell.row + 1, cell.col),
        Cell(cell.row, cell.col - 1),
        Cell(cell.row, cell.col + 1)
    )

    fun generateTutorialLevel(): GeneratedLevel {
        val size = 4
        val cells = LevelShape.SQUARE.validCells(size, size)
        val arrows = carveSnakes(cells, size, size, 2, Random(7))
        return GeneratedLevel(arrows, size, size, LevelShape.SQUARE, "Easy")
    }
}
