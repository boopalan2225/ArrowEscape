package com.example.arrowescape.game.curve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every shape in the rotation must be playable. A shape with a typo in its
 * path would otherwise only show up as a blank board for whichever player hit
 * that level number.
 */
class ShapeCatalogTest {

    private val board = CurvedGameEngine.Board(400f, 600f)

    @Test
    fun `catalog includes the ported silhouette pool`() {
        val names = ShapeCatalog.ALL.map { it.name }
        listOf("Plus", "Hexagon", "Octagon", "House", "Crown", "Shield", "Gem", "Rocket", "Tree")
            .forEach { assertTrue("missing $it", it in names) }
    }

    @Test
    fun `no design is listed twice`() {
        assertEquals(ShapeCatalog.ALL.size, ShapeCatalog.ALL.distinct().size)
    }

    @Test
    fun `consecutive levels never repeat a shape within one full rotation`() {
        val size = ShapeCatalog.ALL.size
        val seen = (1..size).map { ShapeCatalog.ALL[it % size] }
        assertEquals(size, seen.distinct().size)
    }

    @Test
    fun `every shape produces a solvable, populated level`() {
        val size = ShapeCatalog.ALL.size
        // Levels 1..size hit every catalogue entry exactly once.
        for (level in 1..size) {
            val generated = CurvedLevelGenerator.generateLevel(level, board.width, board.height)
            val result = CurvedGameEngine.analyze(generated.arrows, board)
            assertTrue("Level $level (${generated.shapeName}) is not solvable", result.solved)
            assertTrue(
                "Level $level (${generated.shapeName}) only had ${generated.arrows.size} arrows",
                generated.arrows.size >= 8
            )
        }
    }

    @Test
    fun `every illustration fills the board on its tighter axis`() {
        val size = ShapeCatalog.ALL.size
        // Matches the generator's padding: the art may use all but the margin.
        val availableWidth = board.width - 56f
        val availableHeight = board.height - 56f
        for (level in 1..size) {
            val generated = CurvedLevelGenerator.generateLevel(level, board.width, board.height)
            val bounds = generated.regionOutlines.map { it.bounds }
            val spanX = bounds.maxOf { it.second.x } - bounds.minOf { it.first.x }
            val spanY = bounds.maxOf { it.second.y } - bounds.minOf { it.first.y }

            val fill = maxOf(spanX / availableWidth, spanY / availableHeight)
            assertTrue(
                "Level $level (${generated.shapeName}) only fills ${(fill * 100).toInt()}% of the board",
                fill >= 0.95f && fill <= 1.05f
            )
        }
    }

    @Test
    fun `every illustration is centred on the board`() {
        val size = ShapeCatalog.ALL.size
        for (level in 1..size) {
            val generated = CurvedLevelGenerator.generateLevel(level, board.width, board.height)

            // Centring is of the whole illustration, not any one region - the
            // Panda's first region is just the grass band along the bottom.
            val bounds = generated.regionOutlines.map { it.bounds }
            val centreX = (bounds.minOf { it.first.x } + bounds.maxOf { it.second.x }) / 2f
            val centreY = (bounds.minOf { it.first.y } + bounds.maxOf { it.second.y }) / 2f

            assertEquals("Level $level (${generated.shapeName}) x", board.width / 2, centreX, 5f)
            assertEquals("Level $level (${generated.shapeName}) y", board.height / 2, centreY, 5f)
        }
    }
}
