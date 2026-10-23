package com.clevercorner.arrowescape.game.engine

import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.Direction
import com.clevercorner.arrowescape.game.model.GameStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameEngineTest {

    private lateinit var engine: GameEngine

    @Before
    fun setup() { engine = GameEngine() }

    private fun arrow(id: Int, dir: Direction, vararg cells: Pair<Int, Int>) =
        Arrow(id, cells.map { Cell(it.first, it.second) }, dir)

    @Test
    fun `createLevel initializes state`() {
        val arrows = listOf(arrow(0, Direction.UP, 0 to 0), arrow(1, Direction.DOWN, 1 to 1))
        val state = engine.createLevel(5, arrows, 3, 3)
        assertEquals(5, state.level)
        assertEquals(2, state.arrows.size)
        assertEquals(3, state.lives)
        assertEquals(GameStatus.PLAYING, state.status)
    }

    @Test
    fun `valid move removes arrow`() {
        val arrows = listOf(arrow(0, Direction.UP, 0 to 0))
        val state = engine.createLevel(1, arrows, 3, 3)
        val (newState, valid) = engine.tapArrow(state, 0)
        assertTrue(valid)
        assertEquals(0, newState.remainingCount)
    }

    @Test
    fun `invalid move reduces lives`() {
        val arrows = listOf(
            arrow(0, Direction.RIGHT, 1 to 0),
            arrow(1, Direction.UP, 1 to 2)
        )
        val state = engine.createLevel(1, arrows, 3, 3)
        val (newState, valid) = engine.tapArrow(state, 0)
        assertFalse(valid)
        assertEquals(2, newState.lives)
        assertEquals(2, newState.remainingCount)
    }

    @Test
    fun `out of lives after three mistakes`() {
        val arrows = listOf(
            arrow(0, Direction.RIGHT, 1 to 0),
            arrow(1, Direction.UP, 1 to 2)
        )
        var state = engine.createLevel(1, arrows, 3, 3)
        repeat(3) { state = engine.tapArrow(state, 0).first }
        assertEquals(0, state.lives)
        assertEquals(GameStatus.OUT_OF_LIVES, state.status)
    }

    @Test
    fun `level complete when all removed`() {
        val arrows = listOf(arrow(0, Direction.UP, 0 to 0))
        val state = engine.createLevel(1, arrows, 3, 3)
        val (newState, _) = engine.tapArrow(state, 0)
        assertEquals(GameStatus.LEVEL_COMPLETE, newState.status)
    }

    @Test
    fun `restoreLives resets`() {
        val arrows = listOf(
            arrow(0, Direction.RIGHT, 1 to 0),
            arrow(1, Direction.UP, 1 to 2)
        )
        var state = engine.createLevel(1, arrows, 3, 3)
        repeat(3) { state = engine.tapArrow(state, 0).first }
        val restored = engine.restoreLives(state)
        assertEquals(3, restored.lives)
        assertEquals(GameStatus.PLAYING, restored.status)
    }

    @Test
    fun `cannot tap when not playing`() {
        val arrows = listOf(arrow(0, Direction.UP, 0 to 0))
        var state = engine.createLevel(1, arrows, 3, 3).copy(status = GameStatus.PAUSED)
        val (newState, valid) = engine.tapArrow(state, 0)
        assertFalse(valid)
        assertEquals(1, newState.remainingCount)
    }

    @Test
    fun `clearedPercentage calculates`() {
        val arrows = listOf(
            arrow(0, Direction.UP, 0 to 0),
            arrow(1, Direction.UP, 0 to 1),
            arrow(2, Direction.UP, 0 to 2),
            arrow(3, Direction.DOWN, 2 to 0)
        )
        var state = engine.createLevel(1, arrows, 3, 3)
        assertEquals(0, state.clearedPercentage)
        state = engine.tapArrow(state, 0).first
        assertEquals(25, state.clearedPercentage)
    }
}
