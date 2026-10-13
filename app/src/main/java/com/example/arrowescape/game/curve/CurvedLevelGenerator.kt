package com.example.arrowescape.game.curve

import kotlin.random.Random

/**
 * Level data for curved arrow puzzles.
 */
data class CurvedLevel(
    val levelNumber: Int,
    val shapeName: String,
    val shape: ShapePath,
    val arrows: List<CurvedArrow>,
    val difficulty: String,
    val colorPalette: List<Long>,
    /** 0..1 measure of how constrained the solution is. */
    val tightness: Float,
    /** Known-good clear order, used for hints and auto-solve. */
    val solutionOrder: List<Int>
) {
    /**
     * Overall player-facing challenge.
     *
     * Tightness alone saturates around 0.6 and cannot separate a tense
     * 12-arrow board from a tense 40-arrow one - but the bigger board is
     * plainly harder, because the player must find the same constrained moves
     * among far more candidates. Scale is therefore a first-class term.
     */
    val challenge: Float
        get() {
            val scale = (arrows.size / 40f).coerceIn(0f, 1f)
            return (tightness * 0.65f + scale * 0.35f).coerceIn(0f, 1f)
        }
}

/**
 * Generates complete curved arrow puzzle levels.
 *
 * Difficulty comes from *dependency structure*, not from cranking the arrow
 * count. After laying out the art we search arrow orientations for the variant
 * that is still solvable but maximally constrained - fewer legal moves per
 * turn, more forced moves. Flipping an arrow leaves the picture untouched, so
 * hard levels look exactly as pretty as easy ones.
 */
object CurvedLevelGenerator {

    private const val STEPS_PER_ARROW = 40

    // Shape generators for different level ranges (expanded set)
    private val SHAPE_GENERATORS: List<Pair<String, (Float, Float) -> ShapePath>> = listOf(
        "Circle" to { w, h -> ShapePath.circle(w / 2, h / 2, minOf(w, h) * 0.4f) },
        "Heart" to { w, h -> ShapePath.heart(w * 0.8f, h * 0.8f) },
        "Star" to { w, h -> ShapePath.star(w / 2, h / 2, minOf(w, h) * 0.4f, minOf(w, h) * 0.2f, 5) },
        "Cat" to { w, h -> ShapePath.cat(w * 0.8f, h * 0.9f) },
        "Owl" to { w, h -> ShapePath.owl(w * 0.7f, h * 0.9f) },
        "Fish" to { w, h -> ShapePath.fish(w * 0.9f, h * 0.6f) },
        "Butterfly" to { w, h -> ShapePath.butterfly(w * 0.85f, h * 0.85f) },
        "Frog" to { w, h -> ShapePath.frog(w * 0.85f, h * 0.8f) },
        "Deer" to { w, h -> ShapePath.deer(w * 0.9f, h * 0.9f) },
        "Snail" to { w, h -> ShapePath.snail(w * 0.9f, h * 0.7f) },
        "Moon" to { w, h -> ShapePath.moon(w * 0.7f, h * 0.85f) },
        "Leaf" to { w, h -> ShapePath.leaf(w * 0.7f, h * 0.9f) },
        "Star6" to { w, h -> ShapePath.star(w / 2, h / 2, minOf(w, h) * 0.45f, minOf(w, h) * 0.18f, 6) },
        "Star8" to { w, h -> ShapePath.star(w / 2, h / 2, minOf(w, h) * 0.42f, minOf(w, h) * 0.22f, 8) }
    )

    /**
     * Generate a complete level.
     */
    fun generateLevel(levelNumber: Int, boardWidth: Float = 400f, boardHeight: Float = 600f): CurvedLevel {
        val random = Random(levelNumber.toLong() * 31337 + 42)
        val tuning = DifficultyTuning.forLevel(levelNumber, random)

        val shapeIndex = levelNumber % SHAPE_GENERATORS.size
        val (shapeName, shapeGenerator) = SHAPE_GENERATORS[shapeIndex]

        val padding = 40f
        val baseShape = shapeGenerator(boardWidth - padding * 2, boardHeight - padding * 2)
        val centeredShape = baseShape.offsetBy(
            (boardWidth - baseShape.width) / 2,
            (boardHeight - baseShape.height) / 2
        )

        // Solvability must be judged against the BOARD, not the shape. Arrows
        // escape off-screen; the shape is only an art guide. Validating against
        // shape bounds produced levels the generator believed were solvable but
        // the running game did not.
        val board = CurvedGameEngine.Board(boardWidth, boardHeight)

        val laidOut = layOutArrows(centeredShape, tuning.config, levelNumber, random)
        val tuned = searchOrientations(laidOut, board, tuning.targetTightness, random)

        return CurvedLevel(
            levelNumber = levelNumber,
            shapeName = shapeName,
            shape = centeredShape,
            arrows = tuned.arrows,
            difficulty = tuning.label,
            colorPalette = tuning.config.colorPalette,
            tightness = tuned.result.tightness,
            solutionOrder = tuned.result.order
        )
    }

    /**
     * Lay out the artwork, tightening spacing until the shape reads full.
     *
     * Thin shapes (leaves, crescents) starve at the default spacing because
     * few seeds survive the containment test, so a single fixed retry was not
     * enough - we step down until the silhouette is actually populated.
     */
    private fun layOutArrows(
        shape: ShapePath,
        config: CurveGenerator.GenerationConfig,
        levelNumber: Int,
        random: Random
    ): List<CurvedArrow> {
        val minimumArrows = 12
        var best: List<CurvedArrow> = emptyList()

        for ((attempt, factor) in listOf(1f, 0.75f, 0.55f, 0.4f).withIndex()) {
            val attemptConfig = config.copy(
                arrowSpacing = config.arrowSpacing * factor,
                strokeWidth = config.strokeWidth * maxOf(factor, 0.6f),
                arrowMinLength = config.arrowMinLength * maxOf(factor, 0.5f)
            )
            val seed = if (attempt == 0) random else Random(levelNumber * 7919L + attempt)
            val arrows = CurveGenerator.generateArrows(shape, attemptConfig, seed)

            if (arrows.size > best.size) best = arrows
            if (best.size >= minimumArrows) break
        }

        return best
    }

    /**
     * Search arrow orientations for the hardest solvable arrangement.
     *
     * Reversing an arrow is visually free but rewires the blocking graph, so
     * this is where difficulty comes from. Two phases over one precomputed
     * graph: first climb toward solvable, then climb toward tight.
     */
    private fun searchOrientations(
        arrows: List<CurvedArrow>,
        board: CurvedGameEngine.Board,
        targetTightness: Float,
        random: Random
    ): TunedLevel {
        if (arrows.isEmpty()) {
            return TunedLevel(arrows, CurvedGameEngine.SolveResult(true, emptyList(), 0f, 0))
        }

        val graph = CurvedGameEngine.OrientationGraph(arrows, board)
        var flips = BooleanArray(arrows.size)
        var best = graph.analyze(flips)

        val budget = (arrows.size * STEPS_PER_ARROW).coerceIn(200, 4000)
        var sinceImprovement = 0

        for (step in 0 until budget) {
            if (best.solved && best.tightness >= targetTightness) break

            // Widen the jump after a dry spell to escape local maxima.
            val jump = if (sinceImprovement > 40) (2..5).random(random) else 1
            val candidate = flips.copyOf()
            repeat(jump) {
                val slot = candidate.indices.random(random)
                candidate[slot] = !candidate[slot]
            }

            val result = graph.analyze(candidate)
            if (isBetter(result, best)) {
                flips = candidate
                best = result
                sinceImprovement = 0
            } else {
                sinceImprovement++
            }
        }

        val oriented = arrows.mapIndexed { slot, arrow ->
            if (flips[slot]) arrow.reversed() else arrow
        }
        return guaranteeSolvable(TunedLevel(oriented, best), board)
    }

    /**
     * Solvability dominates; tightness only breaks ties between solvable
     * boards. While unsolvable, clearing more arrows is the gradient to climb.
     */
    private fun isBetter(
        candidate: CurvedGameEngine.SolveResult,
        best: CurvedGameEngine.SolveResult
    ): Boolean = when {
        candidate.solved && !best.solved -> true
        !candidate.solved && best.solved -> false
        candidate.solved -> candidate.tightness > best.tightness
        else -> candidate.order.size > best.order.size
    }

    private data class TunedLevel(
        val arrows: List<CurvedArrow>,
        val result: CurvedGameEngine.SolveResult
    )

    /**
     * Final safety net: never hand the player a board they cannot finish.
     * Dropping deadlocked arrows is always safe - removing an arrow can never
     * block another one.
     */
    private fun guaranteeSolvable(
        level: TunedLevel,
        board: CurvedGameEngine.Board
    ): TunedLevel {
        if (level.result.solved) return level

        val survivors = level.arrows.filter { it.id in level.result.order.toSet() }
        return TunedLevel(survivors, CurvedGameEngine.analyze(survivors, board))
    }
}
