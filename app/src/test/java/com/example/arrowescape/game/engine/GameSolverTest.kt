package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.Arrow
import com.example.arrowescape.game.model.Cell
import com.example.arrowescape.game.model.Direction
import org.junit.Assert.*
import org.junit.Test

class GameSolverTest {

    private fun arrow(id: Int, dir: Direction, vararg cells: Pair<Int, Int>) =
        Arrow(id, cells.map { Cell(it.first, it.second) }, dir)

    @Test
    fun `simple solvable board`() {
        val arrows = listOf(
            arrow(0, Direction.UP, 0 to 0),
            arrow(1, Direction.DOWN, 2 to 2)
        )
        assertTrue(GameSolver.isSolvable(arrows, 3, 3))
    }

    @Test
    fun `empty board solvable`() {
        assertTrue(GameSolver.isSolvable(emptyList(), 3, 3))
    }

    @Test
    fun `deadlocked board not solvable`() {
        val arrows = listOf(
            arrow(0, Direction.RIGHT, 1 to 0),
            arrow(1, Direction.LEFT, 1 to 2)
        )
        assertFalse(GameSolver.isSolvable(arrows, 3, 3))
    }

    @Test
    fun `dependent removal order solvable`() {
        val arrows = listOf(
            arrow(0, Direction.UP, 0 to 0),
            arrow(1, Direction.UP, 0 to 1),
            arrow(2, Direction.DOWN, 2 to 0)
        )
        assertTrue(GameSolver.isSolvable(arrows, 3, 3))
    }
}
