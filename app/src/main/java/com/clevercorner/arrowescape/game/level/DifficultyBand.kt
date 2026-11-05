package com.clevercorner.arrowescape.game.level

import com.clevercorner.arrowescape.game.engine.GameSolver

/**
 * The difficulty a level is *aiming* at, and the controller that steers
 * toward it.
 *
 * The generator used to compute knobs from the level number, build up to five
 * boards, and keep whichever scored highest - stopping early at a "good
 * enough" threshold. Two things were wrong with that, and they compound:
 *
 *  1. **Maximising is the wrong objective.** There is no such thing as too
 *     hard under a maximise rule, so level 3 and level 300 were both handed
 *     the same instruction: be as hard as you can. The only reason early
 *     levels came out easy is that their knobs could not express anything
 *     harder - difficulty was a side effect of the recipe's limits rather
 *     than something anybody chose.
 *  2. **A formula cannot self-correct.** When a shape's fill ratio changes,
 *     or a new fill mode lands, or a mechanic shifts the baseline, every
 *     level's actual difficulty moves and nothing notices.
 *
 * A band fixes both. The generator now has a target to hit and a direction to
 * move when it misses, so it converges on the intended difficulty rather than
 * on the hardest board it happened to roll. Misses are reported rather than
 * hidden, because a band the generator cannot reach is information about the
 * recipe, not a failure to paper over.
 */
data class DifficultyBand(val low: Float, val high: Float) {

    val centre: Float get() = (low + high) / 2f

    operator fun contains(challenge: Float): Boolean = challenge in low..high

    /**
     * Which way the knobs need to move, or null when the board has landed.
     * Positive means "make it harder".
     */
    fun correctionFor(challenge: Float): Float? = when {
        challenge < low -> low - challenge
        challenge > high -> high - challenge
        else -> null
    }

    companion object {

        /** Half-width of every band. Narrow enough to mean something, wide enough to hit. */
        private const val HALF_WIDTH = 0.05f

        /**
         * Where difficulty should sit at a given level.
         *
         * These numbers look low, and they are calibrated rather than
         * chosen: measured across levels 2-300, [GameSolver.Solution.challenge]
         * actually lands between about 0.25 and 0.33. The first version of
         * this curve ran to 0.68 and missed its band on seven levels out of
         * ten, which says nothing about those levels and everything about a
         * target invented without looking.
         *
         * The ceiling is low because the ruleset is monotonic - no move can
         * be wrong - so the honest components (false affordances, visual
         * search) can only climb so far. Raising this curve is Stage 11's
         * job, by making the game harder, not by relabelling the scale.
         */
        fun forLevel(level: Int): DifficultyBand {
            val centre = when {
                level <= 1 -> 0.20f
                level <= 10 -> 0.25f
                level <= 25 -> 0.27f
                level <= 50 -> 0.29f
                level <= 100 -> 0.31f
                level <= 200 -> 0.33f
                else -> 0.35f
            }
            return DifficultyBand(centre - HALF_WIDTH, centre + HALF_WIDTH)
        }
    }
}

/**
 * How a candidate board scored against its band, kept so that a persistent
 * miss is visible instead of silently shipped.
 */
data class BandOutcome(
    val band: DifficultyBand,
    val achieved: Float,
    val attempts: Int
) {
    val hit: Boolean get() = achieved in band

    /** Signed distance outside the band; 0 when inside it. */
    val miss: Float get() = band.correctionFor(achieved)?.let { kotlin.math.abs(it) } ?: 0f

    override fun toString(): String =
        "band ${band.low}..${band.high} got ${"%.2f".format(achieved)} " +
            "in $attempts ${if (hit) "(hit)" else "(MISS ${"%.2f".format(miss)})"}"
}

/**
 * Nudges a recipe toward a harder or easier board.
 *
 * Steps are proportional to the miss but floored at something that actually
 * registers: the first version scaled purely with the correction, and since
 * corrections are small by construction, so were the nudges - the controller
 * spent all seven attempts shuffling tangle by two percent and converged on
 * nothing. A feedback loop whose steps are below the system's noise is just
 * an expensive way to pick the first candidate.
 */
object RecipeNudge {

    /** Hardest sensible tangle; past this the carve starts failing outright. */
    private const val MAX_TANGLE = 0.95f

    /** Smallest tangle change worth making. Below this, nothing measurable happens. */
    private const val MIN_STEP = 0.08f

    private fun step(strength: Float) = (strength * 2f).coerceAtLeast(MIN_STEP)

    fun harder(recipe: LevelRecipe, strength: Float): LevelRecipe = recipe.copy(
        tangle = (recipe.tangle + step(strength)).coerceAtMost(MAX_TANGLE),
        // The strongest single lever on false affordances, which is the
        // heaviest term in the challenge score.
        deflectors = recipe.deflectors + 1
    )

    fun easier(recipe: LevelRecipe, strength: Float): LevelRecipe = recipe.copy(
        tangle = (recipe.tangle - step(strength)).coerceAtLeast(0f),
        deflectors = (recipe.deflectors - 1).coerceAtLeast(0)
    )

    /** Apply whichever direction [correction] asks for. */
    fun toward(recipe: LevelRecipe, correction: Float): LevelRecipe =
        if (correction > 0f) harder(recipe, correction) else easier(recipe, -correction)
}

/** Picks the candidate closest to the band, preferring one inside it. */
internal fun <T> Iterable<Pair<T, GameSolver.Solution>>.closestTo(
    band: DifficultyBand
): Pair<T, GameSolver.Solution>? = minByOrNull { (_, solution) ->
    band.correctionFor(solution.challenge)?.let { kotlin.math.abs(it) } ?: 0f
}
