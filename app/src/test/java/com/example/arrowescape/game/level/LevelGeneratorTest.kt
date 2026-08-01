package com.example.arrowescape.game.level

import com.example.arrowescape.game.engine.GameSolver
import com.example.arrowescape.game.model.Cell
import org.junit.Assert.*
import org.junit.Test

class LevelGeneratorTest {

    @Test
    fun `generated levels are solvable`() {
        for (n in listOf(1, 5, 10, 25, 50, 75, 100, 150)) {
            val level = LevelGenerator.generateLevel(n)
            assertTrue(
                "Level $n should be solvable",
                GameSolver.isSolvable(level.arrows, level.gridRows, level.gridCols)
            )
        }
    }

    @Test
    fun `same level number is deterministic`() {
        val a = LevelGenerator.generateLevel(42)
        val b = LevelGenerator.generateLevel(42)
        assertEquals(a.shape, b.shape)
        assertEquals(a.arrows.size, b.arrows.size)
        a.arrows.forEachIndexed { i, arrow ->
            assertEquals(arrow.cells, b.arrows[i].cells)
            assertEquals(arrow.direction, b.arrows[i].direction)
        }
    }

    @Test
    fun `snakes cover every shape cell exactly once`() {
        for (n in listOf(4, 12, 25, 45, 70, 95)) {
            val level = LevelGenerator.generateLevel(n)
            val shapeCells = level.shape
                .validCells(level.gridRows, level.gridCols)
                .map { Cell(it.first, it.second) }
                .toSet()

            val covered = mutableListOf<Cell>()
            level.arrows.forEach { covered.addAll(it.cells) }

            // Every shape cell covered
            assertEquals(
                "Level $n (${level.shape}) should cover all shape cells",
                shapeCells, covered.toSet()
            )
            // No overlaps (each cell exactly once)
            assertEquals(
                "Level $n should have no overlapping cells",
                covered.size, covered.toSet().size
            )
        }
    }

    @Test
    fun `snakes respect max length`() {
        val level = LevelGenerator.generateLevel(95) // maxSnakeLength = 6
        assertTrue(level.arrows.all { it.cells.size in 1..6 })
    }

    @Test
    fun `cell entering head is aligned with arrow direction`() {
        // The neck cell (second from head) must be directly opposite the
        // arrow direction - never perpendicular.
        for (n in listOf(20, 45, 70, 95)) {
            val level = LevelGenerator.generateLevel(n)
            level.arrows.filter { it.cells.size >= 2 }.forEach { arrow ->
                val head = arrow.cells.last()
                val neck = arrow.cells[arrow.cells.size - 2]
                val expected = com.example.arrowescape.game.model.Cell(
                    head.row - arrow.direction.deltaRow(),
                    head.col - arrow.direction.deltaCol()
                )
                assertEquals(
                    "Neck must be directly behind head (level $n)",
                    expected, neck
                )
            }
        }
    }

    @Test
    fun `snake cells are connected`() {
        val level = LevelGenerator.generateLevel(50)
        level.arrows.forEach { arrow ->
            for (i in 1 until arrow.cells.size) {
                val a = arrow.cells[i - 1]
                val b = arrow.cells[i]
                val manhattan = kotlin.math.abs(a.row - b.row) + kotlin.math.abs(a.col - b.col)
                assertEquals("Snake cells must be adjacent", 1, manhattan)
            }
        }
    }

    @Test
    fun `tutorial level is solvable`() {
        val level = LevelGenerator.generateTutorialLevel()
        assertEquals(LevelShape.SQUARE, level.shape)
        assertTrue(GameSolver.isSolvable(level.arrows, level.gridRows, level.gridCols))
    }

    @Test
    fun `all arrows have unique ids`() {
        val level = LevelGenerator.generateLevel(30)
        val ids = level.arrows.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
