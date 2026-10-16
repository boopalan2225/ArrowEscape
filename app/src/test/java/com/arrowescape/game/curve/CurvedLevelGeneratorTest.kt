package com.arrowescape.game.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guard rails for the curved level generator.
 *
 * These are the invariants that actually matter to a player: every level must
 * be finishable, must look full, and must get harder as the number goes up.
 */
class CurvedLevelGeneratorTest {

    private val width = 400f
    private val height = 600f

    private fun levels(range: IntProgression) =
        range.map { CurvedLevelGenerator.generateLevel(it, width, height) }

    @Test
    fun `every level is fully solvable`() {
        for (level in levels(1..60 step 3)) {
            val result = CurvedGameEngine.analyze(level.arrows, CurvedGameEngine.Board(width, height))
            assertTrue(
                "Level ${level.levelNumber} (${level.shapeName}) is not solvable",
                result.solved
            )
            assertEquals(
                "Level ${level.levelNumber} solution must clear every arrow",
                level.arrows.size,
                result.order.size
            )
        }
    }

    @Test
    fun `levels are densely populated`() {
        for (level in levels(1..40 step 4)) {
            assertTrue(
                "Level ${level.levelNumber} only had ${level.arrows.size} arrows",
                level.arrows.size >= 10
            )
        }
    }

    @Test
    fun `later levels pack in more arrows`() {
        val early = levels(1..10).map { it.arrows.size }.average()
        val late = levels(100..110).map { it.arrows.size }.average()
        assertTrue("Late ($late) should out-pack early ($early)", late > early)
    }

    @Test
    fun `difficulty increases with level number`() {
        val easy = levels(1..10).map { it.challenge }.average()
        val hard = levels(45..55).map { it.challenge }.average()
        val master = levels(105..115).map { it.challenge }.average()

        assertTrue("Hard ($hard) should beat easy ($easy)", hard > easy)
        assertTrue("Master ($master) should beat hard ($hard)", master > hard)
    }

    @Test
    fun `early levels stay approachable`() {
        for (level in levels(1..6)) {
            assertTrue(
                "Level ${level.levelNumber} is too punishing for a beginner",
                level.challenge < 0.75f
            )
        }
    }

    @Test
    fun `late levels are genuinely constrained`() {
        for (level in levels(100..112 step 4)) {
            val result = CurvedGameEngine.analyze(level.arrows, CurvedGameEngine.Board(width, height))
            assertTrue(
                "Level ${level.levelNumber} had no forced moves - too easy",
                result.forcedMoves > 0 || result.averageBranching < level.arrows.size * 0.5f
            )
        }
    }

    @Test
    fun `generation is deterministic for a given level`() {
        val a = CurvedLevelGenerator.generateLevel(23, width, height)
        val b = CurvedLevelGenerator.generateLevel(23, width, height)

        assertEquals(a.shapeName, b.shapeName)
        assertEquals(a.arrows.size, b.arrows.size)
        assertEquals(a.solutionOrder, b.solutionOrder)
    }

    @Test
    fun `arrows stay inside the board`() {
        for (level in levels(1..30 step 5)) {
            for (arrow in level.arrows) {
                val (min, max) = arrow.getBounds()
                assertTrue(
                    "Arrow ${arrow.id} on level ${level.levelNumber} escaped the board",
                    min.x >= -5f && min.y >= -5f && max.x <= width + 5f && max.y <= height + 5f
                )
            }
        }
    }

    @Test
    fun `reversing an arrow preserves its geometry`() {
        val arrow = CurvedArrow.throughPoints(
            id = 0,
            points = listOf(
                Point2D(10f, 10f),
                Point2D(50f, 40f),
                Point2D(90f, 20f)
            )
        )
        val flipped = arrow.reversed()

        assertEquals(arrow.startPoint.x, flipped.endPoint.x, 0.01f)
        assertEquals(arrow.startPoint.y, flipped.endPoint.y, 0.01f)
        assertEquals(arrow.endPoint.x, flipped.startPoint.x, 0.01f)
        assertEquals(arrow.length, flipped.length, 0.5f)
    }

    @Test
    fun `a lone arrow can always escape`() {
        val arrow = CurvedArrow.simple(0, Point2D(100f, 300f), Point2D(300f, 300f))
        assertTrue(CurvedGameEngine.canEscape(arrow, listOf(arrow), width, height))
    }

    @Test
    fun `an arrow pointing into another is blocked`() {
        val blocked = CurvedArrow.simple(0, Point2D(50f, 300f), Point2D(150f, 300f), bendStrength = 0f)
        val wall = CurvedArrow.simple(1, Point2D(200f, 200f), Point2D(200f, 400f), bendStrength = 0f)

        assertTrue(
            "Arrow aimed straight at a wall should be blocked",
            !CurvedGameEngine.canEscape(blocked, listOf(blocked, wall), width, height)
        )
        assertTrue(
            "The wall itself runs vertically and should be free",
            CurvedGameEngine.canEscape(wall, listOf(blocked, wall), width, height)
        )
    }
}
