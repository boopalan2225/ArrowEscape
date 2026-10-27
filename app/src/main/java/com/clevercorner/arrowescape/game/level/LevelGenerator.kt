package com.clevercorner.arrowescape.game.level

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

    /** Ceiling on candidate boards. Each one costs a carve plus a solve. */
    private const val MAX_CANDIDATES = 5

    /**
     * A [GameSolver.Solution.challenge] worth stopping the search for.
     * Tuned to be reachable on most rolls, so the common path builds one or
     * two boards rather than five.
     */
    private const val GOOD_ENOUGH = 0.62f

    /** Recently built boards, so stepping back a level is instant. */
    private val cache = LevelCache<Int, GeneratedLevel>()

    fun generateLevel(levelNumber: Int): GeneratedLevel = cache.getOrPut(levelNumber) {
        buildLevel(levelNumber)
    }

    private fun buildLevel(levelNumber: Int): GeneratedLevel {
        val design = LevelDesigns.forLevel(levelNumber)
        val recipe = LevelRecipe.forLevel(levelNumber, design)
        val shape = mapCategoryToShape(design.category, design.shapeName)

        var best: GeneratedLevel? = null
        for (attempt in 0 until MAX_CANDIDATES) {
            val seed = Random(levelNumber.toLong() * 31337 + 42 + attempt)
            val candidate = build(design, recipe, shape, seed) ?: continue
            if (best == null || candidate.solution.challenge > best.solution.challenge) {
                best = candidate
            }
            if (candidate.solution.challenge >= GOOD_ENOUGH) break
        }
        best?.let { return it }

        // Dots are the only thing that can jam the carve; drop them and the
        // reverse-peel guarantee is back in force.
        val plain = recipe.copy(deflectors = 0, lockedPairs = 0)
        return build(design, plain, shape, Random(levelNumber.toLong()))
            ?: error("Level $levelNumber could not be generated even without furniture")
    }

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
            shapeName = "Square",
            difficultyLabel = "Tutorial",
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

        return GeneratedLevel(
            arrows = if (verified != null) locked else carved,
            board = board,
            shape = shape,
            shapeName = design.shapeName,
            difficultyLabel = recipe.difficultyLabel,
            kind = recipe.kind,
            solution = verified ?: solution
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
