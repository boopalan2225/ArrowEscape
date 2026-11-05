package com.clevercorner.arrowescape.perf

import org.junit.BeforeClass
import org.junit.Test

/**
 * Prints a fingerprint of every sampled board. See [BoardFingerprint].
 *
 * This is the safety net the geometry work needs: run it, keep the output,
 * make the change, run it again, diff. Identical output means the refactor
 * was genuinely a refactor. Different output means the game now deals
 * different puzzles - which is sometimes exactly what was wanted, and should
 * always be a decision rather than a discovery.
 *
 * Deliberately a report, not an assertion. Pinning golden hashes in source
 * would be the stronger gate, and it is the right next step - but only once
 * the geometry kernel has settled, otherwise the pinned values would be
 * rewritten in every commit and would stop meaning anything.
 *
 * Run it with:
 * `gradle :app:testDebugUnitTest --tests "*BoardFingerprintReport" --rerun-tasks`
 */
class BoardFingerprintReport {

    companion object {
        private val CURVED_LEVELS = listOf(1, 2, 37, 76, 150, 176, 201, 226, 301, 500)
        private val CLASSIC_LEVELS = listOf(1, 2, 25, 37, 60, 100, 120, 150, 200)

        @BeforeClass
        @JvmStatic
        fun registerShapes() {
            BoardFingerprint.registerAssetShapes()
        }
    }

    @Test
    fun `fingerprint every sampled board`() {
        for (level in CURVED_LEVELS) println("FINGERPRINT ${BoardFingerprint.ofCurved(level)}")
        for (level in CLASSIC_LEVELS) println("FINGERPRINT ${BoardFingerprint.ofClassic(level)}")
    }
}
