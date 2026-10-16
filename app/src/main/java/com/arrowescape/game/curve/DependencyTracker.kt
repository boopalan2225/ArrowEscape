package com.arrowescape.game.curve

import kotlin.math.ceil
import kotlin.random.Random

/**
 * Incremental "who must leave first" bookkeeping used while packing arrows.
 *
 * The old packer only accepted an arrow whose whole exit ray was clear of
 * everything already placed. That is a strict total order, and it is why
 * boards came out sparse: any arrow tucked into a pocket - exactly where a
 * dense silhouette needs ink - was rejected for pointing at a neighbour.
 *
 * Nothing in the rules actually needs a total order. A board is solvable iff
 * the dependency graph ("A cannot leave until B has") has no cycle. So this
 * tracker lets an arrow in whenever doing so keeps that graph acyclic:
 *
 *  - `blockers(c)`   = arrows already placed that stand in c's exit ray
 *  - `blockedBy(c)`  = arrows already placed whose exit ray crosses c's body
 *
 * Adding c makes a cycle exactly when something c must wait for (directly or
 * transitively) is also something that must wait for c.
 *
 * Contact is deliberately over-estimated by up to a cell: an extra edge can
 * only make the graph stricter, never create a deadlock the game would not
 * also see. The real engine remains the final judge.
 */
class DependencyTracker(
    private val width: Float,
    private val height: Float,
    private val cellSize: Float = CELL_SIZE
) {
    private val cols = ceil(width / cellSize).toInt() + 1
    private val rows = ceil(height / cellSize).toInt() + 1

    /** cell -> arrows whose body, inflated by contact distance, covers it. */
    private val bodyOwners = arrayOfNulls<MutableList<Int>>(cols * rows)

    /** cell -> arrows whose exit ray passes through it. */
    private val rayOwners = arrayOfNulls<MutableList<Int>>(cols * rows)

    /** arrow -> arrows that must leave before it can. */
    private val dependsOn = HashMap<Int, MutableSet<Int>>()

    /** Everything accepted so far. */
    val count: Int get() = dependsOn.size

    /**
     * Commit the first of [candidates] that fits, or none.
     *
     * Candidates are tried fewest-dependencies-first, so easy levels stay
     * approachable. An arrow that would wait on more than [maxDependencies]
     * others is rejected - that cap is the difficulty knob. Zero reproduces
     * the old rule: only arrows with a completely clear exit.
     */
    fun tryAdd(
        candidates: List<CurvedArrow>,
        maxDependencies: Int,
        random: Random
    ): CurvedArrow? {
        val viable = candidates.shuffled(random).mapNotNull { arrow ->
            val blockers = blockersOf(arrow)
            if (blockers.size > maxDependencies) return@mapNotNull null
            val blockedBy = blockedBy(arrow)
            if (createsCycle(blockers, blockedBy)) null else Triple(arrow, blockers, blockedBy)
        }
        val (arrow, blockers, blockedBy) = viable.minByOrNull { it.second.size } ?: return null
        commit(arrow, blockers, blockedBy)
        return arrow
    }

    // ── queries ──────────────────────────────────────────────────────────

    private fun blockersOf(arrow: CurvedArrow): Set<Int> {
        val found = HashSet<Int>()
        forEachRayCell(arrow) { cell ->
            bodyOwners[cell]?.forEach { if (it != arrow.id) found.add(it) }
        }
        return found
    }

    private fun blockedBy(arrow: CurvedArrow): Set<Int> {
        val found = HashSet<Int>()
        val reach = arrow.strokeWidth * CONTACT_FACTOR
        for (sample in arrow.polyline) {
            forEachCellNear(sample, reach) { cell ->
                rayOwners[cell]?.forEach { if (it != arrow.id) found.add(it) }
            }
        }
        return found
    }

    /** True when something [blockers] wait on (transitively) also waits on the newcomer. */
    private fun createsCycle(blockers: Set<Int>, blockedBy: Set<Int>): Boolean {
        if (blockedBy.isEmpty() || blockers.isEmpty()) return false

        val seen = HashSet<Int>()
        val stack = ArrayDeque(blockers)
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            if (!seen.add(id)) continue
            if (id in blockedBy) return true
            dependsOn[id]?.let { stack.addAll(it) }
        }
        return false
    }

    // ── mutation ─────────────────────────────────────────────────────────

    private fun commit(arrow: CurvedArrow, blockers: Set<Int>, blockedBy: Set<Int>) {
        dependsOn[arrow.id] = blockers.toMutableSet()
        blockedBy.forEach { dependsOn.getValue(it).add(arrow.id) }

        val reach = arrow.strokeWidth * CONTACT_FACTOR
        for (sample in arrow.polyline) {
            forEachCellNear(sample, reach) { cell -> register(bodyOwners, cell, arrow.id) }
        }
        forEachRayCell(arrow) { cell -> register(rayOwners, cell, arrow.id) }
    }

    private fun register(owners: Array<MutableList<Int>?>, cell: Int, id: Int) {
        val list = owners[cell] ?: mutableListOf<Int>().also { owners[cell] = it }
        if (list.lastOrNull() != id) list.add(id)
    }

    // ── geometry ─────────────────────────────────────────────────────────

    /** Cells along the straight run from [arrow]'s tip to the board edge. */
    private inline fun forEachRayCell(arrow: CurvedArrow, action: (Int) -> Unit) {
        val direction = arrow.direction
        if (direction == Point2D.ZERO) return
        val tip = arrow.endPoint
        val steps = ((width + height) / RAY_STEP).toInt()
        for (i in 0..steps) {
            val probe = tip + direction * (RAY_STEP * i)
            if (probe.x < 0f || probe.x > width || probe.y < 0f || probe.y > height) break
            action(cellAt(probe.x, probe.y))
        }
    }

    private inline fun forEachCellNear(point: Point2D, radius: Float, action: (Int) -> Unit) {
        val minCol = colOf(point.x - radius)
        val maxCol = colOf(point.x + radius)
        val minRow = rowOf(point.y - radius)
        val maxRow = rowOf(point.y + radius)
        for (row in minRow..maxRow) for (col in minCol..maxCol) action(row * cols + col)
    }

    private fun colOf(x: Float) = (x / cellSize).toInt().coerceIn(0, cols - 1)
    private fun rowOf(y: Float) = (y / cellSize).toInt().coerceIn(0, rows - 1)
    private fun cellAt(x: Float, y: Float) = rowOf(y) * cols + colOf(x)

    private companion object {
        const val CELL_SIZE = 3f

        /** Matches the engine's probe spacing, finer than a cell so no cell is skipped. */
        const val RAY_STEP = 2f

        /** Matches the engine's touch distance, as a fraction of stroke width. */
        const val CONTACT_FACTOR = 0.9f
    }
}
