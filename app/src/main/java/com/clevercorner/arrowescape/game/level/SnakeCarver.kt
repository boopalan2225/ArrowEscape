package com.clevercorner.arrowescape.game.level

import com.clevercorner.arrowescape.game.engine.ExitSimulator
import com.clevercorner.arrowescape.game.model.Arrow
import com.clevercorner.arrowescape.game.model.Board
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.Direction
import kotlin.random.Random

/**
 * Carves a filled shape into bendable snake-arrows by reverse-peeling.
 *
 * Peeling works because of one invariant: a snake is only cut out of the mask
 * once its head already has a clear route off the board *through every cell
 * that is still filled*. Replaying the carve order is therefore a guaranteed
 * solution, and the board can never be born dead.
 *
 * Three levers decide how nasty the result is, all borrowed from the puzzle
 * generator this port is based on:
 *
 *  - TIGHTNESS FIRST. At each step the most *constrained* head wins - the
 *    cell with the fewest ways out. Peeling the desperate cells first keeps
 *    the rest of the board jammed, so the player faces one or two legal moves
 *    per turn instead of twenty.
 *  - LENGTH TIERS. Lengths are handed out by quota (see [LengthQuota]), so a
 *    board gets the promised mix of board-spanning snakes and two-cell
 *    corridor plugs instead of whatever the dice felt like.
 *  - TANGLE. High tangle forces a bend every two cells, turning those long
 *    bodies into zig-zags that are genuinely hard to read.
 */
object SnakeCarver {

    /** How many cells to consider per peel. Full scan only if none work out. */
    private const val CANDIDATE_SAMPLE = 64

    /**
     * @return one arrow per peel, in solution order, or null if the shape
     *         jammed (possible once deflectors bend routes back inward).
     */
    fun carve(
        shapeCells: Collection<Cell>,
        board: Board,
        recipe: LevelRecipe,
        random: Random
    ): List<Arrow>? {
        val occupied = shapeCells.toHashSet()
        val arrows = mutableListOf<Arrow>()
        val quota = LengthQuota(recipe)

        while (occupied.isNotEmpty()) {
            val head = pickHead(occupied, board, random) ?: return null
            val tier = quota.next()
            val body = growBody(head, occupied, tier.lengthIn(recipe, random), recipe, random)

            quota.record(body.size, recipe)
            occupied.removeAll(body.toSet())
            arrows.add(Arrow(id = arrows.size, cells = body, direction = head.direction))
        }
        return arrows
    }

    /** A chosen peel: where the snake's tip sits and the lane it leaves by. */
    private data class Head(
        val cell: Cell,
        val direction: Direction,
        val route: Set<Cell>,
        val exitCount: Int
    )

    /**
     * The most constrained cell that can still get out. Ties are broken at
     * random so two levels with the same shape do not carve identically.
     */
    private fun pickHead(occupied: Set<Cell>, board: Board, random: Random): Head? {
        val sampled = sample(occupied, random)
        return bestOf(sampled, board, occupied, random)
            ?: bestOf(occupied, board, occupied, random)
    }

    private fun bestOf(
        cells: Collection<Cell>,
        board: Board,
        occupied: Set<Cell>,
        random: Random
    ): Head? {
        var best: Head? = null
        var ties = 0

        for (cell in cells) {
            val head = headAt(cell, board, occupied) ?: continue
            val incumbent = best
            when {
                incumbent == null || head.exitCount < incumbent.exitCount -> {
                    best = head
                    ties = 1
                }
                // Reservoir sampling: every equally-constrained cell is
                // equally likely to be picked.
                head.exitCount == incumbent.exitCount -> {
                    ties++
                    if (random.nextInt(ties) == 0) best = head
                }
            }
        }
        return best
    }

    /**
     * Scores one cell as a possible head. Of its legal exits it takes the one
     * that travels furthest across the board: a long run has to stay clear of
     * more arrows, so the snake unlocks late and the chain stays taut.
     */
    private fun headAt(cell: Cell, board: Board, occupied: Set<Cell>): Head? {
        var exits = 0
        var chosen: Direction? = null
        var longest: List<Cell> = emptyList()

        for (dir in Direction.entries) {
            val route = ExitSimulator.route(cell, dir, board, occupied) ?: continue
            exits++
            if (route.size > longest.size) {
                longest = route
                chosen = dir
            }
        }
        val direction = chosen ?: return null
        return Head(cell, direction, longest.toHashSet(), exits)
    }

    private fun sample(occupied: Set<Cell>, random: Random): Collection<Cell> =
        if (occupied.size <= CANDIDATE_SAMPLE) occupied
        else occupied.shuffled(random).take(CANDIDATE_SAMPLE)

    /**
     * Grows the body backwards from the head through still-filled cells.
     * Returns the cells tail -> head, which is the order the renderer wants.
     */
    private fun growBody(
        head: Head,
        occupied: Set<Cell>,
        target: Int,
        recipe: LevelRecipe,
        random: Random
    ): List<Cell> {
        val tip = head.cell
        val route = head.route
        val path = ArrayDeque<Cell>()
        path.addLast(tip)
        val taken = hashSetOf(tip)

        if (target < 2) return path.toList()

        // The neck sits directly behind the head so the arrowhead reads cleanly.
        val neck = Cell(tip.row - head.direction.deltaRow(), tip.col - head.direction.deltaCol())
        if (neck !in occupied || neck in route) return path.toList()
        path.addFirst(neck)
        taken.add(neck)

        val bendBias = 0.60f + recipe.tangle * 0.25f
        val maxStraight = if (recipe.tangle >= 0.7f) 2 else 3
        var straightRun = 1

        while (path.size < target) {
            val tail = path.first()
            val previous = path[1]
            val incoming = Cell(tail.row - previous.row, tail.col - previous.col)

            val open = neighbours(tail).filter {
                it in occupied && it !in taken && it !in route
            }
            if (open.isEmpty()) break

            // Anti-square: never touch our own body sideways. Without this the
            // snakes congeal into 2x2 blobs that read as blocks, not arrows.
            val loose = open.filter { candidate ->
                neighbours(candidate).none { it in taken && it != tail }
            }
            val usable = loose.ifEmpty { open }

            val bends = usable.filter { Cell(tail.row - it.row, tail.col - it.col) != incoming }
            val mustBend = straightRun >= maxStraight && bends.isNotEmpty()
            val pool = if (bends.isNotEmpty() && (mustBend || random.nextFloat() < bendBias)) {
                bends
            } else {
                usable
            }

            val next = pool.random(random)
            straightRun = if (Cell(tail.row - next.row, tail.col - next.col) == incoming) {
                straightRun + 1
            } else {
                1
            }
            path.addFirst(next)
            taken.add(next)
        }
        return path.toList()
    }

    /**
     * Length tiers, kept honest by quota rather than by dice.
     *
     * SHORT is not filler: a two-cell arrow plugs a corridor without taking
     * up visual space, which makes it the most obstructive thing on the board
     * per cell spent. The reference generator learned this the hard way and
     * so does this one.
     */
    private enum class LengthTier {
        VERY_LONG, LONG, MEDIUM, SHORT;

        fun lengthIn(recipe: LevelRecipe, random: Random): Int {
            val max = recipe.maxSnakeLength
            if (max <= 2) return max
            val long = (max - 1).coerceAtLeast(3)
            return when (this) {
                VERY_LONG -> max
                LONG -> random.nextInt(long, max + 1)
                MEDIUM -> random.nextInt(3, long + 1)
                SHORT -> 2
            }
        }
    }

    /**
     * Hands out the tier that is furthest behind its share of the board so
     * far. Measured in cells, not arrows, because that is what the player
     * sees: twenty short snakes and two long ones is not a 50/50 board.
     */
    private class LengthQuota(recipe: LevelRecipe) {
        private val shares = mapOf(
            LengthTier.VERY_LONG to recipe.lengths.veryLong,
            LengthTier.LONG to recipe.lengths.long,
            LengthTier.MEDIUM to recipe.lengths.medium,
            LengthTier.SHORT to recipe.lengths.short
        )
        private val spent = LengthTier.entries.associateWith { 0 }.toMutableMap()

        fun next(): LengthTier {
            val total = spent.values.sum()
            if (total == 0) return shares.entries.maxBy { it.value }.key
            // Largest shortfall wins; a zero-share tier never gets picked.
            return shares.entries.maxBy { (tier, share) ->
                share - spent.getValue(tier).toFloat() / total
            }.key
        }

        fun record(cells: Int, recipe: LevelRecipe) {
            val tier = tierOf(cells, recipe)
            spent[tier] = spent.getValue(tier) + cells
        }

        /**
         * Classified by what was actually carved, not what was asked for -
         * a snake that ran out of room is a short one however it was born.
         */
        private fun tierOf(cells: Int, recipe: LevelRecipe): LengthTier {
            val max = recipe.maxSnakeLength
            val long = (max - 1).coerceAtLeast(3)
            return when {
                cells >= max -> LengthTier.VERY_LONG
                cells >= long -> LengthTier.LONG
                cells >= 3 -> LengthTier.MEDIUM
                else -> LengthTier.SHORT
            }
        }
    }

    private fun neighbours(cell: Cell): List<Cell> = listOf(
        Cell(cell.row - 1, cell.col),
        Cell(cell.row + 1, cell.col),
        Cell(cell.row, cell.col - 1),
        Cell(cell.row, cell.col + 1)
    )
}
