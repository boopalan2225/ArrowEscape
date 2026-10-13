package com.example.arrowescape.game.curve

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

        private val PALETTES = mapOf(
            "ocean" to listOf(0xFF4A90D9, 0xFF5BA3E0, 0xFF6BB6E8, 0xFF3D7DC6),
            "sunset" to listOf(0xFFE67E22, 0xFFE74C3C, 0xFFF39C12, 0xFFD35400),
            "forest" to listOf(0xFF27AE60, 0xFF2ECC71, 0xFF1ABC9C, 0xFF16A085),
            "purple" to listOf(0xFF9B59B6, 0xFF8E44AD, 0xFFA569BD, 0xFF7D3C98),
            "rainbow" to listOf(0xFFE74C3C, 0xFFF39C12, 0xFF2ECC71, 0xFF3498DB, 0xFF9B59B6, 0xFFE91E8C)
        )

        private fun paletteFor(level: Int): List<Long> = when {
            level <= 20 -> PALETTES.getValue("ocean")
            level <= 40 -> PALETTES.getValue("forest")
            level <= 60 -> PALETTES.getValue("sunset")
            level <= 80 -> PALETTES.getValue("purple")
            else -> PALETTES.getValue("rainbow")
        }

        private fun flowFor(level: Int, random: Random): CurveGenerator.FlowType = when {
            level <= 6 -> CurveGenerator.FlowType.CONTOUR
            level <= 20 -> listOf(
                CurveGenerator.FlowType.CONTOUR,
                CurveGenerator.FlowType.ORGANIC
            ).random(random)
            else -> CurveGenerator.FlowType.entries.toTypedArray().random(random)
        }

        fun forLevel(level: Int, random: Random): DifficultyTuning {
            val palette = paletteFor(level)
            val flow = flowFor(level, random)

            return when {
                level <= 10 -> DifficultyTuning(
                    label = "Easy",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 45f,
                        arrowMaxLength = 110f,
                        arrowSpacing = 17f,
                        strokeWidth = 8f,
                        bendiness = 0.35f,
                        swirl = 0.1f,
                        flowType = flow,
                        colorPalette = palette.take(2)
                    ),
                    targetTightness = 0.30f
                )

                level <= 30 -> DifficultyTuning(
                    label = "Normal",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 40f,
                        arrowMaxLength = 130f,
                        arrowSpacing = 15f,
                        strokeWidth = 7f,
                        bendiness = 0.45f,
                        swirl = 0.2f,
                        flowType = flow,
                        colorPalette = palette.take(3)
                    ),
                    targetTightness = 0.48f
                )

                level <= 60 -> DifficultyTuning(
                    label = "Hard",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 35f,
                        arrowMaxLength = 150f,
                        arrowSpacing = 13f,
                        strokeWidth = 6f,
                        bendiness = 0.55f,
                        swirl = 0.35f,
                        flowType = flow,
                        colorPalette = palette.take(4)
                    ),
                    targetTightness = 0.64f
                )

                level <= 100 -> DifficultyTuning(
                    label = "Expert",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 30f,
                        arrowMaxLength = 170f,
                        arrowSpacing = 11f,
                        strokeWidth = 5.5f,
                        bendiness = 0.65f,
                        swirl = 0.5f,
                        flowType = flow,
                        colorPalette = palette
                    ),
                    targetTightness = 0.78f
                )

                else -> DifficultyTuning(
                    label = "Master",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 26f,
                        arrowMaxLength = 190f,
                        arrowSpacing = 9.5f,
                        strokeWidth = 5f,
                        bendiness = 0.75f,
                        swirl = 0.65f,
                        flowType = flow,
                        colorPalette = palette
                    ),
                    targetTightness = 0.88f
                )
            }
        }
    }
}
