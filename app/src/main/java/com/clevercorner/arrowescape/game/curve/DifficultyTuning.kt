package com.clevercorner.arrowescape.game.curve

import kotlin.random.Random

/**
 * Maps a level number onto generation parameters and a target difficulty.
 *
 * Two independent knobs:
 *  - [config] controls the *art*: density, length, curviness.
 *  - [targetTightness] controls the *puzzle*: how constrained the solution is.
 *
 * Keeping them separate is deliberate. Cranking density to make a level hard
 * just makes it a soup; tightness is what actually makes a player think.
 */
data class DifficultyTuning(
    val label: String,
    val config: CurveGenerator.GenerationConfig,
    val targetTightness: Float
) {
    companion object {

        // Last level of each difficulty tier; Master is everything after. Sized for
        // the curved journey (one 10-level chapter per shape): 4 / 7 / 8 / 9 chapters,
        // then Master for the rest, so every shape is met at a fitting difficulty.
        const val EASY_MAX = 40
        const val NORMAL_MAX = 110
        const val HARD_MAX = 190
        const val EXPERT_MAX = 280

        /** First level of each tier, in order - for tools that jump between tiers. */
        val TIER_STARTS: List<Pair<String, Int>> = listOf(
            "Easy" to 1,
            "Normal" to EASY_MAX + 1,
            "Hard" to NORMAL_MAX + 1,
            "Expert" to HARD_MAX + 1,
            "Master" to EXPERT_MAX + 1
        )

        /** Name of the tier a level falls in. */
        fun tierOf(level: Int): String =
            TIER_STARTS.last { (_, start) -> level >= start }.first

        /**
         * Reference artwork always flows along the silhouette - that is what
         * makes fur, feathers and antlers legible. Random flow types made
         * shapes unrecognisable, so CONTOUR is the default and other fields
         * only appear as occasional variety on later levels.
         */
        private fun flowFor(level: Int, random: Random): CurveGenerator.FlowType = when {
            level <= 20 -> CurveGenerator.FlowType.CONTOUR
            else -> if (random.nextFloat() < 0.75f) {
                CurveGenerator.FlowType.CONTOUR
            } else {
                CurveGenerator.FlowType.ORGANIC
            }
        }

        fun forLevel(level: Int, random: Random): DifficultyTuning {
            val flow = flowFor(level, random)

            return when {
                level <= EASY_MAX -> DifficultyTuning(
                    label = "Easy",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 34f,
                        arrowMaxLength = 140f,
                        arrowSpacing = 15f,
                        strokeWidth = 7f,
                        bendiness = 0.35f,
                        swirl = 0.1f,
                        curl = 0.25f,
                        maxDependencies = 1,
                        flowType = flow
                    ),
                    targetTightness = 0.30f
                )

                level <= NORMAL_MAX -> DifficultyTuning(
                    label = "Normal",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 28f,
                        arrowMaxLength = 120f,
                        arrowSpacing = 12f,
                        strokeWidth = 5.5f,
                        bendiness = 0.45f,
                        swirl = 0.2f,
                        curl = 0.4f,
                        maxDependencies = 2,
                        flowType = flow
                    ),
                    targetTightness = 0.48f
                )

                level <= HARD_MAX -> DifficultyTuning(
                    label = "Hard",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 22f,
                        arrowMaxLength = 100f,
                        arrowSpacing = 10f,
                        strokeWidth = 4.5f,
                        bendiness = 0.55f,
                        swirl = 0.35f,
                        curl = 0.55f,
                        maxDependencies = 4,
                        flowType = flow
                    ),
                    targetTightness = 0.64f
                )

                level <= EXPERT_MAX -> DifficultyTuning(
                    label = "Expert",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 18f,
                        arrowMaxLength = 90f,
                        arrowSpacing = 8.5f,
                        strokeWidth = 3.8f,
                        bendiness = 0.65f,
                        swirl = 0.5f,
                        curl = 0.7f,
                        maxDependencies = 6,
                        flowType = flow
                    ),
                    targetTightness = 0.78f
                )

                else -> DifficultyTuning(
                    label = "Master",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 15f,
                        arrowMaxLength = 80f,
                        arrowSpacing = 7.5f,
                        strokeWidth = 3.2f,
                        bendiness = 0.75f,
                        swirl = 0.65f,
                        curl = 0.85f,
                        maxDependencies = 8,
                        flowType = flow
                    ),
                    targetTightness = 0.88f
                )
            }
        }
    }
}
