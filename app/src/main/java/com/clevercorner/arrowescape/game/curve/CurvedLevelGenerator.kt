package com.clevercorner.arrowescape.game.curve

import com.clevercorner.arrowescape.game.level.LevelCache
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

    /**
     * Consecutive non-improving steps before the climb gives up.
     *
     * Only applies once a *solvable* board is in hand, so this can never
     * trade correctness for speed - it only stops chasing extra tightness
     * that the search has demonstrably stopped finding.
     */
    private const val STALL_LIMIT = 250

    /**
     * Tightness the daily shuffle aims for. Lower than a fresh level's
     * target: the shuffle has to land fast and reliably, and a board the
     * player has already half-solved is tight enough on its own.
     */
    private const val SHUFFLE_TIGHTNESS = 0.5f

    /** Breathing room between the artwork and the board edge. */
    private const val BOARD_PADDING = 28f

    /**
     * Generate a complete level.
     */
    fun generateLevel(levelNumber: Int, boardWidth: Float = 400f, boardHeight: Float = 600f): CurvedLevel =
        cache.getOrPut(CacheKey(levelNumber, boardWidth, boardHeight)) {
            buildLevel(levelNumber, boardWidth, boardHeight)
        }

    /**
     * Recently built boards. Curved generation runs a hill-climb of up to
     * four thousand solves, which is far too much to repeat just because a
     * player stepped back a level.
     */
    private val cache = LevelCache<CacheKey, CurvedLevel>()

    private data class CacheKey(val level: Int, val width: Float, val height: Float)

    private fun buildLevel(levelNumber: Int, boardWidth: Float, boardHeight: Float): CurvedLevel {
        val random = Random(levelNumber.toLong() * 31337 + 42)
        val tuning = DifficultyTuning.forLevel(levelNumber, random)
        val design = CurvedJourney.designFor(levelNumber)

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
     *
     * Public so previews (journey icons) show exactly the shapes the generator
     * fills. Returned back-to-front.
     */
    fun layOutRegions(
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
        val climbed = climb(graph, BooleanArray(arrows.size), targetTightness, random)
        return guaranteeSolvable(apply(arrows, climbed), board)
    }

    /**
     * Re-arranges an already-solvable set of arrows into a *different*
     * solvable arrangement, without adding or removing a single one.
     *
     * This is the daily challenge's shuffle, and it moves on two levels:
     *
     *  1. A random mirror/rotation of the WHOLE board ([BoardTransform]),
     *     which physically relocates every line on screen. This is the part
     *     that makes the shuffle actually look like a shuffle - flipping
     *     individual arrowheads alone left the drawn curves in exactly the
     *     same place, which read as "nothing happened".
     *  2. The usual per-arrow orientation hill-climb on top, for direction
     *     variety and to land on a solvable board.
     *
     * @return the re-arranged arrows, or null if the climb could not find a
     *         solvable arrangement. Null means "skip this shuffle": dropping
     *         arrows to force one through would break the promise that the
     *         count never changes.
     */
    fun reshuffle(
        arrows: List<CurvedArrow>,
        board: CurvedGameEngine.Board,
        random: Random,
        targetTightness: Float = SHUFFLE_TIGHTNESS
    ): List<CurvedArrow>? {
        if (arrows.size < 2) return null

        // Moved first: an isometry of the whole scene preserves every
        // pairwise blocking relationship exactly, so the orientation climb
        // below is searching the identical solution space it always does -
        // the transform costs nothing in solver terms, only in geometry.
        val relocated = arrows.transformedBy(BoardTransform.random(random), board.width, board.height)

        val graph = CurvedGameEngine.OrientationGraph(relocated, board)
        val start = BooleanArray(relocated.size) { random.nextBoolean() }
        val climbed = climb(graph, start, targetTightness, random)
        if (!climbed.result.solved) return null

        return apply(relocated, climbed).arrows
    }

    /**
     * Hill-climb from [start] towards a solvable, tight arrangement.
     *
     * Shared by first-time generation and by the daily shuffle - the search
     * is identical, only the starting point differs.
     */
    private fun climb(
        graph: CurvedGameEngine.OrientationGraph,
        start: BooleanArray,
        targetTightness: Float,
        random: Random
    ): Climbed {
        var flips = start
        var best = graph.analyze(flips)

        val budget = (flips.size * STEPS_PER_ARROW).coerceIn(200, 4000)
        var sinceImprovement = 0

        for (step in 0 until budget) {
            if (best.solved && best.tightness >= targetTightness) break

            // A hill-climb that has stopped climbing is just burning battery.
            // The high difficulty bands ask for a tightness the scoring
            // formula can barely reach, so without this the search ran its
            // full budget on *every* late level - the single biggest reason
            // the loading spinner used to sit there. Bailing out keeps the
            // best board found so far, which is the one we were going to use
            // anyway.
            if (best.solved && sinceImprovement >= STALL_LIMIT) break

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
        return Climbed(flips, best)
    }

    private fun apply(arrows: List<CurvedArrow>, climbed: Climbed): TunedLevel =
        TunedLevel(
            arrows.mapIndexed { slot, arrow ->
                if (climbed.flips[slot]) arrow.reversed() else arrow
            },
            climbed.result
        )

    private class Climbed(val flips: BooleanArray, val result: CurvedGameEngine.SolveResult)

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
