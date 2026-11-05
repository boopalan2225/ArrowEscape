package com.clevercorner.arrowescape.game.curve

import com.clevercorner.arrowescape.game.LevelHeader
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
    /**
     * Who blocks whom on this board.
     *
     * Travels with the level rather than being rebuilt by whoever plays it:
     * it is a by-product of the orientation search that produced the arrows,
     * so recomputing it is pure repetition - and repetition the player waits
     * through, on a cache hit as well as a miss.
     */
    val blockingGraph: CurvedGameEngine.BlockingGraph,
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
     * How many slots [pickSlot] weighs before committing to one. Three is
     * enough to steer the search without turning move selection into its own
     * search.
     */
    private const val SLOT_SAMPLES = 3

    /** Attempts per shuffle before it is skipped. See [reshuffle]. */
    private const val SHUFFLE_ATTEMPTS = 4

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
     * What the header can say about [levelNumber] before any board exists.
     *
     * Reads the same two sources [buildLevel] does, so this instant answer
     * and the finished board can never disagree. See [LevelHeader].
     */
    fun headerFor(levelNumber: Int): LevelHeader = LevelHeader(
        shapeName = CurvedJourney.designFor(levelNumber).name,
        difficultyLabel = DifficultyTuning.tierOf(levelNumber)
    )

    /**
     * Recently built boards. Curved generation runs a hill-climb of up to
     * four thousand solves, which is far too much to repeat just because a
     * player stepped back a level.
     */
    private val cache = LevelCache<CacheKey, CurvedLevel>()

    private data class CacheKey(val level: Int, val width: Float, val height: Float)

    /**
     * The tuning a level plays with: its tier sets the character, and where
     * it sits in its chapter sets the pace.
     *
     * Public so that anything re-arranging a level later - the daily shuffle
     * - searches with the *same* targets the level was generated with, rather
     * than a second set of constants that quietly drifts from the first.
     */
    fun tuningFor(levelNumber: Int): DifficultyTuning =
        DifficultyTuning.forLevel(levelNumber, Random(levelSeed(levelNumber)))
            .rampedBy(CurvedJourney.progressThroughChapter(levelNumber))

    private fun levelSeed(levelNumber: Int): Long = levelNumber.toLong() * 31337 + 42

    private fun buildLevel(levelNumber: Int, boardWidth: Float, boardHeight: Float): CurvedLevel {
        val random = Random(levelSeed(levelNumber))
        // Same tuning as tuningFor(levelNumber), which is why that function
        // builds its own Random from the same seed: the two stay in step.
        val tuning = DifficultyTuning.forLevel(levelNumber, random)
            .rampedBy(CurvedJourney.progressThroughChapter(levelNumber))
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
        val regionMembers = mutableListOf<Set<Int>>()
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

            // What the region *is* decides how it becomes arrows: a mass is
            // packed, an edge is followed, and a gap is simply left alone.
            val regionArrows = when (region.kind) {
                RegionKind.GAP -> emptyList()
                RegionKind.FILL -> fillRegion(
                    shape = outlines[index],
                    config = config,
                    random = random,
                    exclusions = claimed.toList(),
                    startId = arrows.size,
                    canvas = canvas
                )
                RegionKind.OUTLINE, RegionKind.FEATURE -> CurveGenerator.generateContourArrows(
                    shape = outlines[index],
                    config = config,
                    random = random,
                    exclusions = claimed.toList(),
                    startId = arrows.size,
                    canvas = canvas
                )
            }

            arrows.addAll(regionArrows)
            regionMembers += regionArrows.mapTo(HashSet()) { it.id }
            if (region.kind.reservesArea) claimed.add(outlines[index])
        }

        val tuned = searchOrientations(
            arrows,
            board,
            tuning.targetTightness,
            tuning.flowWeight,
            random
        )

        // Locks go on *after* orientations are settled: tiers are derived
        // from the blocking graph, and flipping an arrow rewrites that graph.
        // Assigning tiers first would be tiering a board that no longer
        // exists by the time anyone plays it.
        val locked = LayerLockPlanner.applyTo(
            tuned.arrows, tuned.graph, regionMembers, tuning.lockTiers, board
        )
        val sequenced = CurvedSequencePlanner.apply(locked, tuned.result.order, tuning.sequencedArrows, board)

        return CurvedLevel(
            levelNumber = levelNumber,
            shapeName = design.name,
            shape = outlines.first(),
            regionOutlines = outlines,
            arrows = sequenced,
            difficulty = tuning.label,
            colorPalette = regions.map { it.color }.distinct(),
            tightness = tuned.result.tightness,
            solutionOrder = tuned.result.order,
            blockingGraph = tuned.graph.withOrdering(sequenced),
            droppedArrows = arrows.size - tuned.arrows.size
        )
    }

    /**
     * Turn a solid region into arrows.
     *
     * [CurveGenerator.FlowType.MEANDER] is the reference art's signature fill -
     * long switchback serpentines - and it is a separate generator rather
     * than another angle field, because a turn-rate-capped streamline cannot
     * hairpin (see [MeanderFill]).
     *
     * The two passes are additive rather than either/or, which took a failing
     * test to get right. A lattice can only be walked where it fits, so on a
     * spiky shape the meander fills the fat middle and leaves the thin
     * extremities bare - a star came out with seven arrows and no points.
     * Running the streamline fill afterwards picks up exactly what the
     * lattice could not reach, because it reads the same occupancy grid.
     *
     * Pleasingly, this does not undo the spacing the meander just created:
     * the deliberate gap between two lanes is narrower than the streamline
     * pass's own seed clearance, so it cannot seed there. It fills genuinely
     * empty space and declines to fill breathing room.
     */
    private fun fillRegion(
        shape: ShapePath,
        config: CurveGenerator.GenerationConfig,
        random: Random,
        exclusions: List<ShapePath>,
        startId: Int,
        canvas: PackingCanvas
    ): List<CurvedArrow> {
        val meander = if (config.flowType == CurveGenerator.FlowType.MEANDER) {
            MeanderFill.generateArrows(
                shape = shape,
                config = config,
                random = random,
                exclusions = exclusions,
                startId = startId,
                canvas = canvas
            )
        } else {
            emptyList()
        }

        val streamlines = CurveGenerator.generateArrows(
            shape = shape,
            config = config,
            random = random,
            exclusions = exclusions,
            startId = startId + meander.size,
            canvas = canvas
        )

        return meander + streamlines
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
        flowWeight: Float,
        random: Random
    ): TunedLevel {
        if (arrows.isEmpty()) {
            return TunedLevel(
                arrows,
                CurvedGameEngine.SolveResult(true, emptyList(), 0f, 0),
                CurvedGameEngine.BlockingGraph(emptyList(), emptyMap())
            )
        }

        val graph = CurvedGameEngine.OrientationGraph(arrows, board)
        val climbed = climb(
            graph = graph,
            start = BooleanArray(arrows.size),
            targetTightness = targetTightness,
            // Starting every flip false means starting from the flow field's
            // own orientation - the most coherent arrangement there is. The
            // climb now has to justify walking away from it.
            flow = FlowCoherence(arrows),
            flowWeight = flowWeight,
            random = random
        )
        // The clear order and the live-play graph are both built here, once,
        // rather than on every candidate the climb looked at.
        val tuned = TunedLevel(
            applyFlips(arrows, climbed.flips),
            graph.analyze(climbed.flips),
            graph.blockingGraph(climbed.flips)
        )
        return guaranteeSolvable(tuned)
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
     * @return the re-arranged arrows, or null if no attempt found a solvable
     *         arrangement. Null means "skip this shuffle": dropping arrows to
     *         force one through would break the promise that the count never
     *         changes.
     */
    fun reshuffle(
        arrows: List<CurvedArrow>,
        board: CurvedGameEngine.Board,
        random: Random,
        tuning: DifficultyTuning
    ): ShuffledBoard? {
        if (arrows.size < 2) return null
        // Hard tiers solve on roughly one attempt in three to five. Each attempt
        // is a few milliseconds, so a few tries recovers the shuffles that the
        // old easier target used to land for free, without easing the target.
        repeat(SHUFFLE_ATTEMPTS) {
            attemptReshuffle(arrows, board, random, tuning)?.let { return it }
        }
        return null
    }

    private fun attemptReshuffle(
        arrows: List<CurvedArrow>,
        board: CurvedGameEngine.Board,
        random: Random,
        tuning: DifficultyTuning
    ): ShuffledBoard? {
        // Moved first: an isometry of the whole scene preserves every
        // pairwise blocking relationship exactly, so the orientation climb
        // below is searching the identical solution space it always does -
        // the transform costs nothing in solver terms, only in geometry.
        val relocated = arrows.transformedBy(BoardTransform.random(random), board.width, board.height)

        val graph = CurvedGameEngine.OrientationGraph(relocated, board)
        val start = BooleanArray(relocated.size) { random.nextBoolean() }
        val climbed = climb(
            graph = graph,
            start = start,
            targetTightness = tuning.targetTightness,
            flow = FlowCoherence(relocated),
            flowWeight = tuning.flowWeight,
            random = random
        )
        if (!climbed.result.solved) return null

        val resolved = applyFlips(relocated, climbed.flips)
        // The ordering edges travel with the arrows (lockTier/sequenceKey
        // survive the isometry via copy()), but the hill-climb above scores
        // purely geometric blocking - it has no idea those fields exist, so
        // it can happily converge on an arrangement that is geometrically
        // fine and a deadlock once the existing lock/sequence edges are
        // folded back in (geometry could now want arrow 3 before arrow 1,
        // while a sequence key still demands the opposite). Re-verify after
        // folding rather than trusting the climb's verdict about a graph it
        // never saw.
        val ordered = graph.blockingGraph(climbed.flips).withOrdering(resolved)
        if (!CurvedGameEngine.analyze(ordered).solved) return null

        return ShuffledBoard(resolved, ordered)
    }

    /**
     * A shuffled board and the graph that describes it, handed over together.
     *
     * Arrows and their blocking graph are two halves of one fact; letting
     * them travel separately is how a caller ends up animating new geometry
     * against stale blocking rules.
     */
    data class ShuffledBoard(
        val arrows: List<CurvedArrow>,
        val blockingGraph: CurvedGameEngine.BlockingGraph
    )

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
        flow: FlowCoherence,
        flowWeight: Float,
        random: Random
    ): Climbed {
        var flips = start
        var best = Candidate(graph.score(flips), flow.of(flips))

        val budget = (flips.size * STEPS_PER_ARROW).coerceIn(200, 4000)
        var sinceImprovement = 0

        for (step in 0 until budget) {
            if (best.solved && best.stats.tightness >= targetTightness) break

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
                // Aim only once there is a solvable board to improve on.
                // While still hunting for one, "make this arrow more blocked"
                // is pushing toward the deadlocks we are trying to escape, so
                // plain random exploration is the better move.
                val slot = if (best.solved) {
                    pickSlot(graph, candidate, random)
                } else {
                    candidate.indices.random(random)
                }
                candidate[slot] = !candidate[slot]
            }

            val result = Candidate(graph.score(candidate), flow.of(candidate))
            if (isBetter(result, best, flowWeight)) {
                flips = candidate
                best = result
                sinceImprovement = 0
            } else {
                sinceImprovement++
            }
        }
        return Climbed(flips, best.stats)
    }

    /** Applies a flip vector to the arrows. Geometry only - no solving. */
    private fun applyFlips(arrows: List<CurvedArrow>, flips: BooleanArray): List<CurvedArrow> =
        arrows.mapIndexed { slot, arrow -> if (flips[slot]) arrow.reversed() else arrow }

    /**
     * A scored arrangement: how good a puzzle it is, and how good a drawing.
     *
     * The two are measured separately and only combined at the moment of
     * comparison, so the exchange rate stays one visible number
     * ([DifficultyTuning.flowWeight]) rather than being baked into the score.
     */
    private class Candidate(
        val stats: CurvedGameEngine.SolveStats,
        val coherence: Float
    ) {
        val solved: Boolean get() = stats.solved

        /** Worth of a *solvable* board: a tight puzzle that still looks drawn. */
        fun appeal(flowWeight: Float): Float = stats.tightness + coherence * flowWeight
    }

    private class Climbed(val flips: BooleanArray, val result: CurvedGameEngine.SolveStats)

    /**
     * Chooses which arrow to try flipping.
     *
     * Not uniformly at random: it samples a few slots and takes the one whose
     * flip would put the most arrows in its way. An arrow with an empty lane
     * ahead of it is free on turn one and contributes nothing to the puzzle,
     * so turning it to face a crowd is the move most likely to tighten the
     * board. Picking blind meant most candidates were rejected and the search
     * needed thousands of full evaluations to get anywhere; weighing moves
     * first costs only array lengths, and no sweep at all.
     */
    private fun pickSlot(
        graph: CurvedGameEngine.OrientationGraph,
        flips: BooleanArray,
        random: Random
    ): Int {
        var bestSlot = flips.indices.random(random)
        var bestGain = Int.MIN_VALUE
        repeat(SLOT_SAMPLES) {
            val slot = flips.indices.random(random)
            val gain = graph.blockerCount(slot, !flips[slot]) - graph.blockerCount(slot, flips[slot])
            if (gain > bestGain) {
                bestGain = gain
                bestSlot = slot
            }
        }
        return bestSlot
    }

    /**
     * Solvability dominates; among solvable boards, a tight puzzle that still
     * reads as a drawing beats a tighter one that does not. While unsolvable,
     * clearing more arrows is the gradient to climb - coherence is no use on
     * a board nobody can finish.
     */
    private fun isBetter(
        candidate: Candidate,
        best: Candidate,
        flowWeight: Float
    ): Boolean = when {
        candidate.solved && !best.solved -> true
        !candidate.solved && best.solved -> false
        candidate.solved -> candidate.appeal(flowWeight) > best.appeal(flowWeight)
        else -> candidate.stats.cleared > best.stats.cleared
    }

    private data class TunedLevel(
        val arrows: List<CurvedArrow>,
        val result: CurvedGameEngine.SolveResult,
        val graph: CurvedGameEngine.BlockingGraph
    )

    /**
     * Final safety net: never hand the player a board they cannot finish.
     * Dropping deadlocked arrows is always safe - removing an arrow can never
     * block another one, which is also why the graph only has to be filtered
     * rather than measured again.
     */
    private fun guaranteeSolvable(level: TunedLevel): TunedLevel {
        if (level.result.solved) return level

        val survivors = level.result.order.toSet()
        val graph = level.graph.restrictedTo(survivors)
        return TunedLevel(
            level.arrows.filter { it.id in survivors },
            CurvedGameEngine.analyze(graph),
            graph
        )
    }
}
