package com.clevercorner.arrowescape.game.level

import com.clevercorner.arrowescape.game.LevelHeader
import com.clevercorner.arrowescape.game.engine.GameSolver
import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board
import com.clevercorner.arrowescape.game.model.Cell
import kotlin.random.Random

data class GeneratedLevel(
    val arrows: List<Arrow>,
    val board: Board,
    val shape: LevelShape,
    val shapeName: String,
    val difficultyLabel: String,
    val kind: LevelKind,
    /** A known-good clear order plus its difficulty measurements. */
    val solution: GameSolver.Solution
) {
    val gridRows: Int get() = board.rows
    val gridCols: Int get() = board.cols

    /** 0..1 - how forced the solution is. 1.0 means one legal move per turn. */
    val tightness: Float get() = solution.tightness

    /** Longest run of moves with exactly one legal option. */
    val longestChain: Int get() = solution.longestChain

    val solutionOrder: List<Int> get() = solution.order
}

/**
 * Builds a playable level out of three independent pieces:
 *
 *  1. [LevelDesigns] says what the level looks like (the shape mask).
 *  2. [LevelRecipe] says how hard it plays (tangle, dots, locks).
 *  3. [SnakeCarver], [DeflectorPlanner] and [ColorLockPlanner] do the work.
 *
 * This file only wires them together and refuses to ship anything the solver
 * cannot finish.
 *
 * Generation is a small contest rather than a single roll: candidate boards
 * are built from the same recipe and the most *chained* one wins - the board
 * with the longest runs of forced moves, where the player has to read ahead
 * instead of poking at it. Rolling also covers the case where deflectors
 * bend a route back inward and jam the carve, with a plain board as the last
 * resort.
 *
 * The contest stops early at the first good-enough board. Running all of
 * them every time multiplied generation cost by five for a difficulty gain
 * nobody could feel, and the player paid for it in a visible stall on every
 * level change.
 */
object LevelGenerator {

    /**
     * Ceiling on candidate boards. Each one costs a carve plus a solve.
     *
     * Slightly higher than the old five because the controller *uses* its
     * attempts - each one is aimed by the last one's miss rather than being
     * another independent roll of the same dice.
     */
    private const val MAX_CANDIDATES = 7

    /** Recently built boards, so stepping back a level is instant. */
    private val cache = LevelCache<Int, GeneratedLevel>()

    fun generateLevel(levelNumber: Int): GeneratedLevel = cache.getOrPut(levelNumber) {
        buildLevel(levelNumber)
    }

    /** Level 1 is hand-made, so its header is a constant rather than a lookup. */
    private const val TUTORIAL_SHAPE_NAME = "Square"
    private const val TUTORIAL_DIFFICULTY = "Tutorial"

    /**
     * What the header can say about [levelNumber] before any board exists.
     *
     * Reads the same sources [buildLevel] does, so this instant answer and
     * the finished board can never disagree. See [LevelHeader].
     */
    fun headerFor(levelNumber: Int): LevelHeader {
        if (levelNumber == 1) return LevelHeader(TUTORIAL_SHAPE_NAME, TUTORIAL_DIFFICULTY)
        val design = LevelDesigns.forLevel(levelNumber)
        return LevelHeader(design.shapeName, LevelRecipe.forLevel(levelNumber, design).difficultyLabel)
    }

    private fun buildLevel(levelNumber: Int): GeneratedLevel {
        val design = LevelDesigns.forLevel(levelNumber)
        val baseRecipe = LevelRecipe.forLevel(levelNumber, design).sizedForBudget(levelNumber, design)
        val shape = mapCategoryToShape(design.category, design.shapeName)
        val band = DifficultyBand.forLevel(levelNumber)

        // A controller, not a tournament. Each attempt is steered by how the
        // previous one missed, so the search walks toward the band instead of
        // rolling dice and keeping the luckiest board.
        var recipe = baseRecipe
        var best: GeneratedLevel? = null
        var bestMiss = Float.MAX_VALUE

        for (attempt in 0 until MAX_CANDIDATES) {
            val seed = Random(levelNumber.toLong() * 31337 + 42 + attempt)
            val candidate = build(design, recipe, shape, seed) ?: continue

            val challenge = candidate.solution.challenge
            val correction = band.correctionFor(challenge)

            if (correction == null) return candidate

            val miss = kotlin.math.abs(correction)
            if (miss < bestMiss) {
                bestMiss = miss
                best = candidate
            }
            recipe = RecipeNudge.toward(recipe, correction)
        }

        best?.let { return it }

        // Dots are the only thing that can jam the carve; drop them and the
        // reverse-peel guarantee is back in force.
        val plain = baseRecipe.copy(deflectors = 0, lockedPairs = 0)
        return build(design, plain, shape, Random(levelNumber.toLong()))
            ?: error("Level $levelNumber could not be generated even without furniture")
    }

    /**
     * Re-sizes a recipe's grid so the board holds roughly the arrow count
     * [ArrowBudget] asked for, rather than whatever its design happened to
     * specify.
     *
     * The fill ratio has to be measured, not declared, so the shape is
     * rasterised once at a reference size purely to ask "how much of your
     * bounding box do you actually use?". That is a cheap answer and it is
     * the whole correction: a ring and a square then get different grids and
     * the same amount of game.
     */
    private fun LevelRecipe.sizedForBudget(
        levelNumber: Int,
        design: LevelDesigns.LevelDesign
    ): LevelRecipe {
        if (kind == LevelKind.TUTORIAL) return this

        val probe = ShapeTemplate.scaleTemplate(design.template, FILL_PROBE_GRID, FILL_PROBE_GRID)
        if (probe.isEmpty()) return this
        val fillRatio = probe.size.toFloat() / (FILL_PROBE_GRID * FILL_PROBE_GRID)

        val sized = ArrowBudget.gridFor(
            targetArrows = ArrowBudget.forLevel(levelNumber),
            fillRatio = fillRatio,
            averageSnakeLength = ArrowBudget.averageSnakeLength(lengths)
        )
        return copy(gridSize = sized)
    }

    /**
     * Grid used only to measure a shape's fill ratio. Big enough that a
     * silhouette is represented fairly, small enough to be free.
     */
    private const val FILL_PROBE_GRID = 16

    fun generateTutorialLevel(): GeneratedLevel {
        val size = 4
        val board = Board(size, size)
        val cells = LevelShape.SQUARE.validCells(size, size).map { Cell(it.first, it.second) }
        val recipe = LevelRecipe.forLevel(1, LevelDesigns.forLevel(1)).copy(
            gridSize = size,
            maxSnakeLength = 2,
            deflectors = 0,
            lockedPairs = 0
        )
        val arrows = SnakeCarver.carve(cells, board, recipe, Random(7)).orEmpty()
        val solution = GameSolver.solve(arrows, board)
            ?: GameSolver.Solution(emptyList(), 0f, 0f, 0)

        return GeneratedLevel(
            arrows = arrows,
            board = board,
            shape = LevelShape.SQUARE,
            shapeName = TUTORIAL_SHAPE_NAME,
            difficultyLabel = TUTORIAL_DIFFICULTY,
            kind = LevelKind.TUTORIAL,
            solution = solution
        )
    }

    /** One roll of the dice. Null means this attempt jammed; try another. */
    private fun build(
        design: LevelDesigns.LevelDesign,
        recipe: LevelRecipe,
        shape: LevelShape,
        random: Random
    ): GeneratedLevel? {
        val size = recipe.gridSize
        val mask = ShapeTemplate.scaleTemplate(design.template, size, size)
            .mapTo(HashSet()) { Cell(it.first, it.second) }
        if (mask.isEmpty()) return null

        val deflectors = DeflectorPlanner.plan(mask, recipe.deflectors, random)
        val board = Board(size, size, deflectors)
        val fill = mask - deflectors.map { it.cell }.toSet()

        val carved = SnakeCarver.carve(fill, board, recipe, random) ?: return null
        val solution = GameSolver.solve(carved, board) ?: return null

        val locked = ColorLockPlanner.weld(carved, solution.order, recipe.lockedPairs, random)
        // Locks are safe by construction, but construction arguments have a
        // habit of being wrong; the solver gets the final word.
        val verified = GameSolver.solve(locked, board)
        val settled = if (verified != null) locked else carved
        val settledSolution = verified ?: solution

        // Numbers go on last, against the order that actually cleared this
        // board. Numbering before the colour locks were welded would be
        // numbering a board that no longer exists - welding changes which
        // arrows leave together, and therefore the order itself.
        val sequenced = SequencePlanner.apply(
            settled, settledSolution.order, recipe.sequencedArrows, random
        )
        val afterSequence = GameSolver.solve(sequenced, board)

        return GeneratedLevel(
            arrows = if (afterSequence != null) sequenced else settled,
            board = board,
            shape = shape,
            shapeName = design.shapeName,
            difficultyLabel = recipe.difficultyLabel,
            kind = recipe.kind,
            solution = afterSequence ?: settledSolution
        )
    }

    /**
     * Maps design category/name to closest LevelShape for icon display.
     */
    private fun mapCategoryToShape(category: String, shapeName: String): LevelShape {
        val nameLower = shapeName.lowercase()
        return when {
            "square" in nameLower -> LevelShape.SQUARE
            "circle" in nameLower || "ring" in nameLower -> LevelShape.CIRCLE
            "triangle" in nameLower -> LevelShape.TRIANGLE
            "diamond" in nameLower -> LevelShape.DIAMOND
            "heart" in nameLower -> LevelShape.HEART
            "star" in nameLower -> LevelShape.STAR
            "cross" in nameLower -> LevelShape.CROSS
            "crown" in nameLower -> LevelShape.CROWN
            "butterfly" in nameLower -> LevelShape.BUTTERFLY
            "spiral" in nameLower -> LevelShape.SPIRAL
            "lightning" in nameLower -> LevelShape.LIGHTNING
            "arrow" in nameLower -> LevelShape.ARROW_UP
            "hexagon" in nameLower -> LevelShape.HEXAGON
            "moon" in nameLower || "crescent" in nameLower -> LevelShape.CRESCENT
            "hourglass" in nameLower -> LevelShape.HOURGLASS
            category == "Letters" -> LevelShape.LETTER_X
            else -> LevelShape.SQUARE
        }
    }
}
