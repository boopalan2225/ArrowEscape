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
 * Mechanic (see CollisionChecker): a snake slithers out head-first, its body
 * following the head's trail. So only the HEAD needs a clear straight ray to
 * the edge - the body may bend freely. This lets us pack lots of bent snakes.
 *
 * Reverse-peeling:
 *  1. Fill every cell of the shape mask.
 *  2. Repeatedly pick a cell whose head-ray (in some direction) is clear of the
 *     other remaining cells; make it a snake head and grow a bendy body through
 *     still-occupied cells. Remove and record it.
 *  3. Replaying that removal order is always a valid solution -> solvable.
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
            // Heads = cells whose forward ray (in some dir) is clear of the
            // OTHER occupied cells.
            val candidates = occupied.filter { headDirs(it, occupied, rows, cols).isNotEmpty() }
            val head = candidates.random(random)
            val dir = headDirs(head, occupied, rows, cols).random(random)

            val path = ArrayDeque<Cell>()
            path.addLast(head)
            val snake = hashSetOf(head)

            val targetLen = if (maxLen <= 1) 1
            else (maxLen - random.nextInt(0, 2)).coerceAtLeast(2)

            // Neck sits directly behind the head so the arrowhead reads cleanly.
            // It only needs to be occupied now - NO ray check (body slithers out).
            if (targetLen >= 2) {
                val neck = Cell(head.row - dir.deltaRow(), head.col - dir.deltaCol())
                if (neck in occupied && neck !in snake) {
                    path.addFirst(neck); snake.add(neck)

                    // Grow the rest with a strong preference for BENDS.
                    while (path.size < targetLen) {
                        val tail = path.first()
                        val prev = path[1]
                        val incoming = Cell(tail.row - prev.row, tail.col - prev.col)
                        val options = neighbors(tail).filter {
                            it in occupied && it !in snake &&
                                    !isInFrontOfHead(it, head, dir)
                        }
                        if (options.isEmpty()) break
                        val bends = options.filter {
                            Cell(tail.row - it.row, tail.col - it.col) != incoming
                        }
                        val pool = if (bends.isNotEmpty() && random.nextFloat() < 0.8f)
                            bends else options
                        val next = pool.random(random)
                        path.addFirst(next); snake.add(next)
                    }
                }
            }

            occupied.removeAll(snake)
            arrows.add(Arrow(id = nextId++, cells = path.toList(), direction = dir))
        }

        return arrows.shuffled(random).mapIndexed { i, a -> a.copy(id = i) }
    }

    /** Directions in which [cell]'s forward ray is clear of other occupied cells. */
    private fun headDirs(
        cell: Cell,
        occupied: Set<Cell>,
        rows: Int,
        cols: Int
    ): List<Direction> = Direction.entries.filter { dir ->
        var r = cell.row + dir.deltaRow()
        var c = cell.col + dir.deltaCol()
        while (r in 0 until rows && c in 0 until cols) {
            if (Cell(r, c) in occupied) return@filter false
            r += dir.deltaRow()
            c += dir.deltaCol()
        }
        true
    }

    /** True if [cell] lies on the head's forward ray (directly in front of it). */
    private fun isInFrontOfHead(cell: Cell, head: Cell, dir: Direction): Boolean {
        val dr = dir.deltaRow(); val dc = dir.deltaCol()
        if (dr != 0) {
            if (cell.col != head.col) return false
            val steps = (cell.row - head.row) * dr
            return steps > 0
        } else {
            if (cell.row != head.row) return false
            val steps = (cell.col - head.col) * dc
            return steps > 0
        }
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
