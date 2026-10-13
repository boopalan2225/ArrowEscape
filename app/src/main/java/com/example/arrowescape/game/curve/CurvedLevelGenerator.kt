package com.example.arrowescape.game.curve

import kotlin.random.Random

/**
 * Level data for curved arrow puzzles.
 */
data class CurvedLevel(
    val levelNumber: Int,
    val shapeName: String,
    val shape: ShapePath,
    /** Every region's outline, back to front. Used for previews and tinting. */
    val regionOutlines: List<ShapePath>,
    val arrows: List<CurvedArrow>,
    val difficulty: String,
    val colorPalette: List<Long>,
    /** 0..1 measure of how constrained the solution is. */
    val tightness: Float,
    /** Known-good clear order, used for hints and auto-solve. */
    val solutionOrder: List<Int>,
    /** Arrows the generator laid out but had to discard to keep the level solvable. */
    val droppedArrows: Int = 0
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

    /** Breathing room between the artwork and the board edge. */
    private const val BOARD_PADDING = 28f

    /**
     * Generate a complete level.
     */
    fun generateLevel(levelNumber: Int, boardWidth: Float = 400f, boardHeight: Float = 600f): CurvedLevel {
        val random = Random(levelNumber.toLong() * 31337 + 42)
        val tuning = DifficultyTuning.forLevel(levelNumber, random)
        val design = ShapeCatalog.ALL[levelNumber % ShapeCatalog.ALL.size]

        // Solvability must be judged against the BOARD, not the artwork.
        // Arrows escape off-screen; the shape is only a container.
        val board = CurvedGameEngine.Board(boardWidth, boardHeight)

        val regions = design.ordered
        val outlines = layOutRegions(design, boardWidth, boardHeight)

        // Fill FRONT-to-BACK: the smallest detail regions (eyes, then belly)
        // claim their space first, and broader layers underneath flow around
        // whatever is already there. Filling back-to-front did the opposite -
        // the body consumed the whole silhouette and every detail region was
        // excluded out of existence.
        val arrows = mutableListOf<CurvedArrow>()
        val claimed = mutableListOf<ShapePath>()
        val canvas = PackingCanvas(boardWidth, boardHeight)

        for (index in regions.indices.reversed()) {
            val region = regions[index]
            val config = tuning.config.copy(
                strokeWidth = tuning.config.strokeWidth * region.strokeScale,
                arrowSpacing = tuning.config.arrowSpacing * region.strokeScale,
                curl = tuning.config.curl * region.curl,
                flowType = region.flow ?: tuning.config.flowType,
                colorPalette = ArrowTones.of(region.color)
            )

            val regionArrows = CurveGenerator.generateArrows(
                shape = outlines[index],
                config = config,
                random = random,
                exclusions = claimed.toList(),
                startId = arrows.size,
                canvas = canvas
            )

            arrows.addAll(regionArrows)
            claimed.add(outlines[index])
        }

        val tuned = searchOrientations(arrows, board, tuning.targetTightness, random)

        return CurvedLevel(
            levelNumber = levelNumber,
            shapeName = design.name,
            shape = outlines.first(),
            regionOutlines = outlines,
            arrows = tuned.arrows,
            difficulty = tuning.label,
            colorPalette = regions.map { it.color }.distinct(),
            tightness = tuned.result.tightness,
            solutionOrder = tuned.result.order,
            droppedArrows = arrows.size - tuned.arrows.size
        )
    }

    /**
     * Uniformly scale a whole illustration so its overall bounding box just
     * fills [areaWidth] x [areaHeight] on its tighter axis. Uniform, so the
     * proportions - and the relative placement of every region - survive.
     */
    private fun fitToArea(
        regions: List<ShapePath>,
        areaWidth: Float,
        areaHeight: Float
    ): List<ShapePath> {
        val minX = regions.minOf { it.bounds.first.x }
        val minY = regions.minOf { it.bounds.first.y }
        val maxX = regions.maxOf { it.bounds.second.x }
        val maxY = regions.maxOf { it.bounds.second.y }
        val spanX = maxX - minX
        val spanY = maxY - minY
        if (spanX <= 0f || spanY <= 0f) return regions

        val factor = minOf(areaWidth / spanX, areaHeight / spanY)
        return regions.map { it.scaleTo(it.width * factor, it.height * factor) }
    }

    /**
     * Build every region outline and centre the whole illustration on the board.
     *
     * Centring uses the artwork's real bounding box. The old code offset by the
     * shape's *declared* size, which is only correct for shapes drawn from the
     * origin - the circle, star and others were drawn around their own centre
     * and so ended up visibly off-centre.
     */
    private fun layOutRegions(
        design: ShapeDesign,
        boardWidth: Float,
        boardHeight: Float
    ): List<ShapePath> {
        val availableWidth = boardWidth - BOARD_PADDING * 2
        val availableHeight = boardHeight - BOARD_PADDING * 2

        var artWidth = availableWidth
        var artHeight = availableHeight
        design.aspect?.let { aspect ->
            artHeight = artWidth / aspect
            if (artHeight > availableHeight) {
                artHeight = availableHeight
                artWidth = artHeight * aspect
            }
        }

        // Scale the finished illustration up to the space it is offered. The
        // legacy shapes size themselves as a fraction of the shorter side, so
        // on a tall board a star used to float in the middle third with the
        // rest wasted - fewer arrows, harder to read, harder to tap.
        val raw = fitToArea(
            design.ordered.map { it.build(artWidth, artHeight) },
            availableWidth,
            availableHeight
        )

        val minX = raw.minOf { it.bounds.first.x }
        val minY = raw.minOf { it.bounds.first.y }
        val maxX = raw.maxOf { it.bounds.second.x }
        val maxY = raw.maxOf { it.bounds.second.y }

        val dx = boardWidth / 2 - (minX + maxX) / 2
        val dy = boardHeight / 2 - (minY + maxY) / 2
        return raw.map { it.offsetBy(dx, dy) }
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
