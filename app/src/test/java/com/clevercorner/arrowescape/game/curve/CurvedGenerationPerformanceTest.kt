package com.clevercorner.arrowescape.game.curve

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Level generation happens on a background thread while the player waits on a
 * loading state, so it has a real latency budget.
 */
class CurvedGenerationPerformanceTest {

    @Test
    fun `a level generates fast enough to feel instant`() {
        // Warm up the JIT so we measure the algorithm, not class loading.
        repeat(3) { CurvedLevelGenerator.generateLevel(it + 1, 400f, 600f) }

        val worstCase = listOf(105, 110, 118, 123).maxOf { level ->
            val start = System.nanoTime()
            CurvedLevelGenerator.generateLevel(level, 400f, 600f)
            (System.nanoTime() - start) / 1_000_000
        }

        println("PERF worst-case level generation = ${worstCase}ms")
        assertTrue(
            "Worst-case level generation took ${worstCase}ms, budget is 400ms",
            worstCase < 400
        )
    }
}
