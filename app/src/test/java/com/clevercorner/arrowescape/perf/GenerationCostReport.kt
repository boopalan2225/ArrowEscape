package com.clevercorner.arrowescape.perf

import com.clevercorner.arrowescape.game.curve.CurvedLevelGenerator
import com.clevercorner.arrowescape.game.level.LevelGenerator
import org.junit.BeforeClass
import org.junit.Test

/**
 * What a level costs to build, in time **and in garbage**.
 *
 * Reports rather than asserts, on purpose. The budgets that belong here have
 * to be calibrated against a real device first, and an uncalibrated threshold
 * is worse than none - it either fails honest work or passes everything.
 * Until then this prints a table, and a reviewer reads it.
 *
 * Why allocation is the headline number and wall clock is the footnote: this
 * runs on a desktop JVM with a warm JIT, a generational collector and a
 * multi-gigabyte heap. A low-end phone has none of those. Allocated bytes per
 * level is the one measurement that survives the journey from here to there,
 * because a 90 MB level is 90 MB of collector work on any machine - and on a
 * 128 MB heap, that is several collections the player waits through.
 *
 * Run it with:
 * `gradle :app:testDebugUnitTest --tests "*GenerationCostReport" --rerun-tasks`
 */
class GenerationCostReport {

    companion object {
        /** Mid-journey, dense late-journey, authored SVG and synthesised shapes. */
        private val CURVED_LEVELS = listOf(1, 76, 176, 201, 226, 301, 500, 501, 502)

        /** Classic's bands, including the recipe that used to cost half a gigabyte. */
        private val CLASSIC_LEVELS = listOf(1, 25, 60, 120, 200)

        @BeforeClass
        @JvmStatic
        fun registerShapes() {
            BoardFingerprint.registerAssetShapes()
        }
    }

    @Test
    fun `report what a level costs`() {
        warmUp()

        println("COST mode level shape arrows timeMs allocMB")
        for (level in CURVED_LEVELS) {
            val measured = measure { CurvedLevelGenerator.generateLevel(level, 400f, 600f) }
            val built = measured.value
            report("curved", level, built.shapeName, built.arrows.size, measured)
        }
        for (level in CLASSIC_LEVELS) {
            val measured = measure { LevelGenerator.generateLevel(level) }
            val built = measured.value
            report("classic", level, built.shapeName, built.arrows.size, measured)
        }
    }

    /**
     * Measures the *uncached* path.
     *
     * The old performance test called `generateLevel`, which goes through the
     * LRU - so a level another test had already built measured as a cache
     * hit and the suite happily reported a few microseconds. Every level here
     * is used exactly once, which is the only way to keep that honest without
     * reaching into the generators.
     */
    private fun <T> measure(build: () -> T): Measured<T> {
        val startBytes = allocatedBytes()
        val startNanos = System.nanoTime()
        val value = build()
        val nanos = System.nanoTime() - startNanos
        val bytes = allocatedBytes() - startBytes
        return Measured(value, nanos / 1_000_000, bytes / (1024.0 * 1024.0))
    }

    private class Measured<T>(val value: T, val millis: Long, val megabytes: Double)

    private fun report(mode: String, level: Int, shape: String, arrows: Int, measured: Measured<*>) {
        println(
            "COST $mode $level $shape arrows=$arrows " +
                "time=${measured.millis}ms alloc=${"%.1f".format(measured.megabytes)}MB"
        )
    }

    /** Class loading and JIT are a launch cost, not a generation cost. */
    private fun warmUp() {
        repeat(3) { CurvedLevelGenerator.generateLevel(900 + it, 400f, 600f) }
        repeat(3) { LevelGenerator.generateLevel(900 + it) }
    }

    /**
     * Bytes allocated by this thread, read reflectively.
     *
     * `com.sun.management.ThreadMXBean` is not on the Android unit-test
     * *compile* classpath, but it is very much there at runtime - these tests
     * run on a desktop JVM. Reflection is the seam between those two facts.
     */
    private fun allocatedBytes(): Long = ALLOCATED
        .invoke(THREAD_BEAN, Thread.currentThread().id) as Long

    private val THREAD_BEAN by lazy {
        Class.forName("java.lang.management.ManagementFactory")
            .getMethod("getThreadMXBean")
            .invoke(null)
    }

    private val ALLOCATED by lazy {
        Class.forName("com.sun.management.ThreadMXBean")
            .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    }
}
