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
 * Generates dense, fully-filled shape levels that are guaranteed solvable.
 *
 * Strategy - "reverse peeling":
 *  1. Fill EVERY cell of the shape mask with an arrow (crisp, dense shapes).
 *  2. Repeatedly find an arrow that currently has a clear escape path and
 *     assign it that direction, then remove it from the working set.
 *  3. Because arrows are assigned in a real removal order, replaying that
 *     exact order is always a valid solution -> the board is solvable.
 *
 * The same level number always produces the same board (deterministic seed).
 */
object LevelGenerator {

    fun generateLevel(levelNumber: Int): GeneratedLevel {
        val config = DifficultyConfig.forLevel(levelNumber)
        val random = Random(levelNumber.toLong() * 31337 + 42)

        val cells = config.shape.validCells(config.gridRows, config.gridCols)
        val arrows = fillAndAssign(cells, config.gridRows, config.gridCols, random)

        // Safety net (should always pass thanks to the peel construction).
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
     * Fills every provided cell with an arrow and assigns each a direction
     * via the reverse-peeling method so the whole board is solvable.
     */
    private fun fillAndAssign(
        cells: List<Pair<Int, Int>>,
        rows: Int,
        cols: Int,
        random: Random
    ): List<ArrowTile> {
        val occupied = cells.toMutableSet()
        val direction = HashMap<Pair<Int, Int>, Direction>()

        while (occupied.isNotEmpty()) {
            // Collect every (cell, direction) that can currently escape.
            val escapableCells = mutableListOf<Pair<Int, Int>>()
            for (cell in occupied) {
                if (escapeDirections(cell, occupied, rows, cols).isNotEmpty()) {
                    escapableCells.add(cell)
                }
            }

            // A filled shape always has at least one escapable outer cell.
            val chosen = escapableCells.random(random)
            val dirs = escapeDirections(chosen, occupied, rows, cols)
            direction[chosen] = dirs.random(random)
            occupied.remove(chosen)
        }

        // Build arrow tiles from the assigned directions, shuffled ids.
        return cells.shuffled(random).mapIndexed { index, (r, c) ->
            ArrowTile(
                id = index,
                row = r,
                col = c,
                direction = direction[r to c] ?: Direction.UP,
                isActive = true
            )
        }
    }

    /**
     * Returns which directions let [cell] reach the grid edge without
     * crossing any currently-occupied cell.
     */
    private fun escapeDirections(
        cell: Pair<Int, Int>,
        occupied: Set<Pair<Int, Int>>,
        rows: Int,
        cols: Int
    ): List<Direction> {
        return Direction.entries.filter { dir ->
            pathClear(cell, dir, occupied, rows, cols)
        }
    }

    private fun pathClear(
        cell: Pair<Int, Int>,
        dir: Direction,
        occupied: Set<Pair<Int, Int>>,
        rows: Int,
        cols: Int
    ): Boolean {
        var r = cell.first + dir.deltaRow()
        var c = cell.second + dir.deltaCol()
        while (r in 0 until rows && c in 0 until cols) {
            if ((r to c) in occupied) return false
            r += dir.deltaRow()
            c += dir.deltaCol()
        }
        return true
    }

    /**
     * Simple hand-crafted tutorial-friendly first level: a small full square.
     */
    fun generateTutorialLevel(): GeneratedLevel {
        val rows = 4
        val cols = 4
        val cells = LevelShape.SQUARE.validCells(rows, cols)
        val arrows = fillAndAssign(cells, rows, cols, Random(7))
        return GeneratedLevel(
            arrows = arrows,
            gridRows = rows,
            gridCols = cols,
            shape = LevelShape.SQUARE,
            difficultyLabel = "Easy"
        )
    }
}
