package com.clevercorner.arrowescape.game.curve

import kotlin.random.Random

/**
 * Maps a level number onto generation parameters and a target difficulty.
 *
 * Three knobs:
 *  - [config] controls the *art*: density, length, curviness.
 *  - [targetTightness] controls the *puzzle*: how constrained the solution is.
 *  - [flowWeight] sets the exchange rate between them.
 *
 * Keeping art and puzzle separate is deliberate. Cranking density to make a
 * level hard just makes it a soup; tightness is what actually makes a player
 * think. But the two are not fully independent - the orientation search buys
 * tightness with arrow directions, and directions are also what the picture
 * is drawn with. [flowWeight] is where that bargain is struck out loud
 * instead of being lost by default.
 */
data class DifficultyTuning(
    val label: String,
    val config: CurveGenerator.GenerationConfig,
    val targetTightness: Float,
    /**
     * How much the orientation search values neighbouring arrows agreeing on
     * a direction, relative to tightness. 0 restores the old behaviour of
     * spending coherence for free; higher keeps the flow field's drawing
     * intact at some cost to how forced the solution is.
     *
     * Ramps down as levels get harder: early boards should be handsome, late
     * boards should bite.
     */
    val flowWeight: Float = 0.30f,
    /**
     * How many layer-lock tiers this level may use; under 2 means none.
     *
     * Locks are the one mechanic here that adds *planning* rather than
     * making the board harder to read, so they are held back rather than
     * handed out: meeting "the eyes must go before the face" on an early
     * level is a rule nobody explained, while meeting it later is a shape
     * that has started fighting back. The planner may also decline - a shape
     * whose regions all block each other cannot be tiered safely, and gets
     * no locks whatever this says.
     */
    val lockTiers: Int = 0,
    /**
     * How many arrows to number, forcing them to leave in ascending order.
     *
     * Unlike [lockTiers] this fires on essentially every board, because the
     * arrows are chosen rather than discovered - so it is ramped carefully
     * and starts at zero.
     */
    val sequencedArrows: Int = 0
) {

    /**
     * The same tier, wound tighter as a chapter wears on.
     *
     * A chapter is now five levels of one shape, and those five are meant to
     * be an arc rather than five deals of the same hand: the first spacious
     * and long-stroked so the picture is obvious and the arrows are easy to
     * hit, the last tight and crowded so the puzzle bites.
     *
     * These steps used to be deliberately small, which was the right call for
     * ten levels that only needed to not feel *worse* - and the wrong one for
     * the complaint that consecutive levels look identical. The dominant
     * visual dial is [CurveGenerator.GenerationConfig.arrowSpacing], because
     * under the meander fill it sets the lattice pitch directly: wide lanes
     * with background showing through, or narrow ones packed close.
     *
     * [progress] runs 0 at a chapter's first level to 1 at its last.
     */
    fun rampedBy(progress: Float): DifficultyTuning {
        val t = progress.coerceIn(0f, 1f)
        return copy(
            targetTightness = (targetTightness + TIGHTNESS_RAMP * t).coerceAtMost(MAX_TIGHTNESS),
            config = config.copy(
                arrowSpacing = config.arrowSpacing * (1f - SPACING_RAMP * t),
                arrowMaxLength = config.arrowMaxLength * (1f - LENGTH_RAMP * t),
                curl = config.curl * (1f + CURL_RAMP * t),
                // More arrows allowed to wait on others, so a chapter's last
                // board has real dependency chains where its first had almost
                // none. This is the part a player experiences as "harder"
                // rather than merely "busier".
                maxDependencies = config.maxDependencies +
                    (DEPENDENCY_RAMP * t).toInt()
            )
        )
    }
    companion object {

        /** How much a chapter's last level out-tightens its first. */
        private const val TIGHTNESS_RAMP = 0.16f

        /**
         * How much closer together the strokes pack by a chapter's end.
         *
         * The big one. Under the meander fill this is the lattice pitch, so
         * it is the difference between "spacious, obviously a crocodile, easy
         * to tap" and "densely ruled".
         */
        private const val SPACING_RAMP = 0.35f

        /** How much shorter the strokes get by a chapter's end. */
        private const val LENGTH_RAMP = 0.25f

        /** How much curlier the strokes get by a chapter's end. */
        private const val CURL_RAMP = 0.40f

        /** Extra waiting-on-others allowed by a chapter's end. */
        private const val DEPENDENCY_RAMP = 3f

        /**
         * Tightness the ramp will never push past. Beyond this the climb
         * cannot reach its target, stops improving, and burns its whole
         * budget discovering that.
         */
        private const val MAX_TIGHTNESS = 0.95f

        // How many *chapters* each tier covers, rather than which level it
        // ends on. The two differ by LEVELS_PER_CHAPTER, and writing the
        // level numbers down meant that changing chapter length silently
        // re-pointed every tier at the wrong shapes - Easy covering four
        // shapes or eight depending on a constant in another file. Derived,
        // so the two cannot drift.
        private const val EASY_CHAPTERS = 4
        private const val NORMAL_CHAPTERS = 7
        private const val HARD_CHAPTERS = 8
        private const val EXPERT_CHAPTERS = 9

        private const val PER_CHAPTER = CurvedJourney.LEVELS_PER_CHAPTER

        /** Last level of each difficulty tier; Master is everything after. */
        val EASY_MAX = EASY_CHAPTERS * PER_CHAPTER
        val NORMAL_MAX = EASY_MAX + NORMAL_CHAPTERS * PER_CHAPTER
        val HARD_MAX = NORMAL_MAX + HARD_CHAPTERS * PER_CHAPTER
        val EXPERT_MAX = HARD_MAX + EXPERT_CHAPTERS * PER_CHAPTER

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
         * The reference art is meander almost everywhere: long switchback
         * serpentines ruled at an even pitch, with visible background between
         * the lanes. That is the look, so it is the default rather than an
         * option, and the other fields appear only as occasional variety once
         * a player has seen enough boards to want some.
         *
         * CONTOUR remains the runner-up because flowing along the silhouette
         * is what makes fur and feathers legible; a region can still ask for
         * either by name via its SVG layer id.
         */
        private fun flowFor(level: Int, random: Random): CurveGenerator.FlowType = when {
            level <= 20 -> CurveGenerator.FlowType.MEANDER
            else -> when {
                random.nextFloat() < 0.70f -> CurveGenerator.FlowType.MEANDER
                random.nextFloat() < 0.70f -> CurveGenerator.FlowType.CONTOUR
                else -> CurveGenerator.FlowType.ORGANIC
            }
        }

        fun forLevel(level: Int, random: Random): DifficultyTuning {
            val flow = flowFor(level, random)

            return when {
                level <= EASY_MAX -> DifficultyTuning(
                    label = "Easy",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 40f,
                        arrowMaxLength = 170f,
                        // Pitch is strokeWidth + spacing = 27px under the
                        // meander fill, which is wide enough that the board
                        // reads as a drawing with background showing through
                        // rather than as a solid mass of ink.
                        arrowSpacing = 18f,
                        // Fat on purpose. A thin stroke is both harder to see
                        // and harder to hit, and Easy is where neither should
                        // be the thing making the level hard.
                        strokeWidth = 9f,
                        bendiness = 0.35f,
                        swirl = 0.1f,
                        curl = 0.25f,
                        maxDependencies = 1,
                        flowType = flow
                    ),
                    targetTightness = 0.30f,
                    flowWeight = 0.40f
                )

                level <= NORMAL_MAX -> DifficultyTuning(
                    label = "Normal",
                    config = CurveGenerator.GenerationConfig(
                        arrowMinLength = 32f,
                        arrowMaxLength = 145f,
                        arrowSpacing = 14f,
                        strokeWidth = 7f,
                        bendiness = 0.45f,
                        swirl = 0.2f,
                        curl = 0.4f,
                        maxDependencies = 2,
                        flowType = flow
                    ),
                    targetTightness = 0.48f,
                    flowWeight = 0.32f
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
                    targetTightness = 0.64f,
                    flowWeight = 0.26f,
                    // Locks start here, at two tiers: one rule to learn, and
                    // it arrives on a board the player can already read.
                    lockTiers = 2,
                    sequencedArrows = 3
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
                    targetTightness = 0.78f,
                    flowWeight = 0.20f,
                    lockTiers = 3,
                    sequencedArrows = 4
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
                    targetTightness = 0.88f,
                    flowWeight = 0.14f,
                    lockTiers = 4,
                    sequencedArrows = 5
                )
            }
        }
    }
}
