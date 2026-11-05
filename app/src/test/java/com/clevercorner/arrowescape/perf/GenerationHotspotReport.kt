package com.clevercorner.arrowescape.perf

import com.clevercorner.arrowescape.game.curve.CurvedLevelGenerator
import org.junit.BeforeClass
import org.junit.Test
import kotlin.concurrent.thread

/**
 * A sampling profiler for level generation, in forty lines.
 *
 * Earns its keep: it is what found both of the big wins so far. Reasoning
 * about which code "looks expensive" produced a confident, wrong answer two
 * rounds running - the hill-climb everyone suspected turned out to cost
 * almost nothing, while a linear scan nobody had thought about was over half
 * the time. A thousand stack samples settles the argument in fifteen
 * seconds.
 *
 * Self time is the one to read first: it is where the CPU actually was.
 * Total time says which call chain led there.
 *
 * Run it with:
 * `gradle :app:testDebugUnitTest --tests "*GenerationHotspotReport" --rerun-tasks`
 */
class GenerationHotspotReport {

    companion object {
        private val LEVELS = 600..640

        private const val OUR_CODE = "com.clevercorner"
        private const val TOP_FRAMES = 18
        private const val SAMPLER_SHUTDOWN_MS = 500L

        @BeforeClass
        @JvmStatic
        fun registerShapes() {
            BoardFingerprint.registerAssetShapes()
        }
    }

    @Test
    fun `where does curved generation spend its time`() {
        repeat(3) { CurvedLevelGenerator.generateLevel(900 + it, 400f, 600f) }

        val selfSamples = HashMap<String, Int>()
        val stackSamples = HashMap<String, Int>()
        val sampling = java.util.concurrent.atomic.AtomicBoolean(true)

        val generator = thread(name = "generator") {
            for (level in LEVELS) CurvedLevelGenerator.generateLevel(level, 400f, 600f)
        }
        val sampler = thread(name = "sampler", isDaemon = true) {
            while (sampling.get()) {
                val ours = generator.stackTrace.filter { it.className.startsWith(OUR_CODE) }
                ours.firstOrNull()?.let { selfSamples.merge(frameOf(it), 1, Int::plus) }
                ours.map { frameOf(it) }.distinct().forEach { stackSamples.merge(it, 1, Int::plus) }
                Thread.sleep(1)
            }
        }

        generator.join()
        sampling.set(false)
        sampler.join(SAMPLER_SHUTDOWN_MS)

        report("self time - where the CPU was", selfSamples, TOP_FRAMES)
        report("total time - what called it", stackSamples, TOP_FRAMES)
    }

    private fun report(title: String, samples: Map<String, Int>, limit: Int) {
        println("HOTSPOT === $title ===")
        samples.entries.sortedByDescending { it.value }.take(limit)
            .forEach { println("HOTSPOT ${it.value} ${it.key}") }
    }

    private fun frameOf(element: StackTraceElement): String =
        "${element.className.substringAfterLast('.')}.${element.methodName}"
}
