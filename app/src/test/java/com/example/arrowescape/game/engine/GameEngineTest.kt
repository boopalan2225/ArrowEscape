package com.example.arrowescape.game.engine

import com.example.arrowescape.game.model.ArrowTile
import com.example.arrowescape.game.model.Direction
import com.example.arrowescape.game.model.GameStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GameEngine.
 */
class GameEngineTest {

    private lateinit var engine: GameEngine

    @Before
    fun setup() {
        engine = GameEngine()
    }

    @Test
    fun `createLevel initializes correct state`() {
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.UP),
            ArrowTile(1, 1, 1, Direction.DOWN)
        )

        val state = engine.createLevel(5, arrows)

        assertEquals(5, state.level)
        assertEquals(2, state.arrows.size)
        assertEquals(3, state.lives)
        assertEquals(GameStatus.PLAYING, state.status)
        assertEquals(0, state.mistakes)
    }

    @Test
    fun `tapArrow with valid move removes arrow`() {
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.UP) // At top, pointing up - clear path
        )
        val state = engine.createLevel(1, arrows, 3, 3)

        val (newState, wasValid) = engine.tapArrow(state, 0)

        assertTrue(wasValid)
        assertEquals(0, newState.remainingCount)
        assertFalse(newState.arrows.first().isActive)
    }

    @Test
    fun `tapArrow with invalid move reduces lives`() {
        val arrows = listOf(
            ArrowTile(0, 1, 0, Direction.RIGHT), // Blocked by arrow at (1,2)
            ArrowTile(1, 1, 2, Direction.UP)
        )
        val state = engine.createLevel(1, arrows, 3, 3)

        val (newState, wasValid) = engine.tapArrow(state, 0)

        assertFalse(wasValid)
        assertEquals(2, newState.lives)
        assertEquals(1, newState.mistakes)
        assertEquals(2, newState.remainingCount) // Neither removed
    }

    @Test
    fun `game becomes OUT_OF_LIVES when lives reach zero`() {
        val arrows = listOf(
            ArrowTile(0, 1, 0, Direction.RIGHT),
            ArrowTile(1, 1, 2, Direction.UP)
        )
        var state = engine.createLevel(1, arrows, 3, 3)

        // Make 3 invalid moves
        repeat(3) {
            val (newState, _) = engine.tapArrow(state, 0)
            state = newState
        }

        assertEquals(0, state.lives)
        assertEquals(GameStatus.OUT_OF_LIVES, state.status)
    }

    @Test
    fun `game becomes LEVEL_COMPLETE when all arrows removed`() {
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.UP)
        )
        val state = engine.createLevel(1, arrows, 3, 3)

        val (newState, _) = engine.tapArrow(state, 0)

        assertEquals(GameStatus.LEVEL_COMPLETE, newState.status)
        assertEquals(0, newState.remainingCount)
    }

    @Test
    fun `restoreLives sets lives back to max`() {
        val arrows = listOf(
            ArrowTile(0, 1, 0, Direction.RIGHT),
            ArrowTile(1, 1, 2, Direction.UP)
        )
        var state = engine.createLevel(1, arrows, 3, 3)

        // Lose all lives
        repeat(3) {
            val (newState, _) = engine.tapArrow(state, 0)
            state = newState
        }

        assertEquals(GameStatus.OUT_OF_LIVES, state.status)

        val restored = engine.restoreLives(state)

        assertEquals(3, restored.lives)
        assertEquals(GameStatus.PLAYING, restored.status)
    }

    @Test
    fun `cannot tap arrow when not PLAYING`() {
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.UP)
        )
        var state = engine.createLevel(1, arrows, 3, 3)
        state = state.copy(status = GameStatus.PAUSED)

        val (newState, wasValid) = engine.tapArrow(state, 0)

        assertFalse(wasValid)
        assertEquals(1, newState.remainingCount) // Arrow not removed
    }

    @Test
    fun `pause and resume work correctly`() {
        val state = engine.createLevel(1, emptyList())

        val paused = engine.pause(state)
        assertEquals(GameStatus.PAUSED, paused.status)

        val resumed = engine.resume(paused)
        assertEquals(GameStatus.PLAYING, resumed.status)
    }

    @Test
    fun `clearedPercentage calculates correctly`() {
        val arrows = listOf(
            ArrowTile(0, 0, 0, Direction.UP),
            ArrowTile(1, 0, 1, Direction.UP),
            ArrowTile(2, 0, 2, Direction.UP),
            ArrowTile(3, 1, 0, Direction.DOWN)
        )
        var state = engine.createLevel(1, arrows, 3, 3)

        assertEquals(0, state.clearedPercentage)

        // Remove first arrow
        val (afterOne, _) = engine.tapArrow(state, 0)
        assertEquals(25, afterOne.clearedPercentage)

        // Remove second arrow
        val (afterTwo, _) = engine.tapArrow(afterOne, 1)
        assertEquals(50, afterTwo.clearedPercentage)
    }
}
