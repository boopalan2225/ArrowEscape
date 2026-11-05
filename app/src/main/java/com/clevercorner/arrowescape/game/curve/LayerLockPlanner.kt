package com.clevercorner.arrowescape.game.curve

/**
 * Makes the artwork's structure part of the puzzle: a cat's eyes must be
 * cleared before its face, its face before its body.
 *
 * Every other difficulty lever in this game makes the board harder to *read*.
 * This one makes it harder to *plan*, which is a different and better kind of
 * difficulty - and unlike the mechanics that break the safety property (ice,
 * spawners), it cannot produce a board the player is unable to finish.
 *
 * ## Why this is safe, and what would make it unsafe
 *
 * The engine clears an arrow once nothing remains in its escape path, so the
 * board is a dependency graph and solvability is exactly "that graph is
 * acyclic". A layer lock adds edges: every arrow in a lower tier must clear
 * before any arrow in a higher one.
 *
 * Adding edges to a DAG can absolutely create a cycle, and here a cycle is a
 * dead board. Concretely: if arrow X sits in tier 0 but is physically blocked
 * by arrow Y in tier 1, then X waits for Y (geometry) while Y waits for X
 * (the lock), and the level is unwinnable from the first tap. So tiers cannot
 * be assigned by artistic preference alone - they have to agree with the
 * geometry.
 *
 * The fix is to derive tiers *from* the blocking graph rather than imposing
 * them on it:
 *
 *  1. Lift the arrow-level blocking graph to a region-level one.
 *  2. Condense its strongly-connected components. Two regions that block each
 *     other cannot be separated by a lock, so they share a tier - no cycle is
 *     possible within a tier because a tier adds no edges inside itself.
 *  3. Layer the condensation by longest path. An edge only ever points from a
 *     lower tier to a higher one, so the added edges agree with every edge
 *     that was already there.
 *
 * The result is a strict ordering the player must respect, assembled only out
 * of orderings the board already required - which is why it adds planning
 * without risking a single unsolvable level.
 */
object LayerLockPlanner {

    /**
     * Fewer tiers than this is not a mechanic, it is a formality. A shape
     * whose regions all tangle together simply does not get locks.
     */
    private const val MIN_USEFUL_TIERS = 2

    /**
     * Tags [arrows] with lock tiers, keeping them only if the board still
     * solves.
     *
     * [assignTiers] derives tiers so that they cannot deadlock, and the
     * argument for that is given above - but "the argument is sound" is how
     * every unsolvable level ever shipped got shipped. Re-solving the locked
     * board costs one peel and turns a proof into a check, so locks are
     * dropped rather than trusted whenever the check disagrees.
     */
    fun applyTo(
        arrows: List<CurvedArrow>,
        graph: CurvedGameEngine.BlockingGraph,
        regionMembers: List<Set<Int>>,
        maxTiers: Int,
        board: CurvedGameEngine.Board
    ): List<CurvedArrow> {
        if (maxTiers < MIN_USEFUL_TIERS || arrows.isEmpty()) return arrows

        val surviving = arrows.mapTo(HashSet()) { it.id }
        val groups = regionMembers.map { ids -> ids.filterTo(HashSet()) { it in surviving } }
        val tiers = assignTiers(groups, graph::blockersOf, maxTiers)
        if (tiers.isEmpty()) return arrows

        val locked = arrows.map { it.copy(lockTier = tiers[it.id] ?: 0) }
        return if (CurvedGameEngine.analyze(locked, board).solved) locked else arrows
    }

    /**
     * Assigns a lock tier to each arrow, or leaves every tier at zero when
     * the shape cannot support locks safely.
     *
     * @param groups arrow ids per region, in paint order.
     */
    fun assignTiers(
        groups: List<Set<Int>>,
        blockersOf: (Int) -> Set<Int>,
        maxTiers: Int
    ): Map<Int, Int> {
        val populated = groups.filter { it.isNotEmpty() }
        if (populated.size < MIN_USEFUL_TIERS || maxTiers < MIN_USEFUL_TIERS) return emptyMap()

        val regionOf = HashMap<Int, Int>()
        populated.forEachIndexed { index, ids -> ids.forEach { regionOf[it] = index } }

        // Region-level edges: "some arrow in A must clear before some arrow in B".
        val edges = Array(populated.size) { HashSet<Int>() }
        for ((arrow, region) in regionOf) {
            for (blocker in blockersOf(arrow)) {
                val from = regionOf[blocker] ?: continue
                if (from != region) edges[from].add(region)
            }
        }

        val component = stronglyConnectedComponents(edges)
        val componentCount = (component.maxOrNull() ?: 0) + 1
        if (componentCount < MIN_USEFUL_TIERS) return emptyMap()

        val depth = longestPathLayering(edges, component, componentCount)

        // Squeeze the layering into the allowed number of tiers. Collapsing
        // is always safe: merging two tiers only ever removes constraints.
        val span = (depth.maxOrNull() ?: 0) + 1
        val tiers = HashMap<Int, Int>(regionOf.size)
        for ((arrow, region) in regionOf) {
            val raw = depth[component[region]]
            tiers[arrow] = if (span <= maxTiers) raw else raw * maxTiers / span
        }

        return if (tiers.values.distinct().size < MIN_USEFUL_TIERS) emptyMap()
        else if (!isBalanced(tiers)) emptyMap()
        else tiers
    }

    /**
     * Rejects splits where one tier is a rounding error.
     *
     * Measured across the authored journey, the planner's natural splits are
     * often wildly lopsided - an owl came out 68 arrows against 3, a deer 3
     * against 46. Three arrows is not a phase of a puzzle; it is an
     * unexplained rule that stops four taps from working and teaches the
     * player nothing. Better no mechanic than a mechanic that only ever
     * manifests as confusion.
     */
    private fun isBalanced(tiers: Map<Int, Int>): Boolean {
        val counts = tiers.values.groupingBy { it }.eachCount().values
        val smallest = counts.minOrNull() ?: return false
        return smallest >= tiers.size * MIN_TIER_SHARE
    }

    /** Smallest share of the board a tier may hold and still be worth locking. */
    private const val MIN_TIER_SHARE = 0.20f

    /**
     * Tarjan's algorithm, iterative.
     *
     * Recursive is the readable version, but region counts come from
     * generated artwork and nothing guarantees a modest depth - a stack
     * overflow inside level generation would be a crash on a player's phone,
     * which is a poor trade for a few lines of brevity.
     */
    private fun stronglyConnectedComponents(edges: Array<HashSet<Int>>): IntArray {
        val n = edges.size
        val index = IntArray(n) { -1 }
        val low = IntArray(n)
        val onStack = BooleanArray(n)
        val component = IntArray(n) { -1 }
        val stack = ArrayDeque<Int>()
        var nextIndex = 0
        var nextComponent = 0

        for (root in 0 until n) {
            if (index[root] != -1) continue

            val callStack = ArrayDeque<Pair<Int, Iterator<Int>>>()
            index[root] = nextIndex
            low[root] = nextIndex
            nextIndex++
            stack.addLast(root)
            onStack[root] = true
            callStack.addLast(root to edges[root].iterator())

            while (callStack.isNotEmpty()) {
                val (node, iterator) = callStack.last()
                if (iterator.hasNext()) {
                    val next = iterator.next()
                    if (index[next] == -1) {
                        index[next] = nextIndex
                        low[next] = nextIndex
                        nextIndex++
                        stack.addLast(next)
                        onStack[next] = true
                        callStack.addLast(next to edges[next].iterator())
                    } else if (onStack[next]) {
                        low[node] = minOf(low[node], index[next])
                    }
                } else {
                    callStack.removeLast()
                    callStack.lastOrNull()?.let { (parent, _) ->
                        low[parent] = minOf(low[parent], low[node])
                    }
                    if (low[node] == index[node]) {
                        while (true) {
                            val popped = stack.removeLast()
                            onStack[popped] = false
                            component[popped] = nextComponent
                            if (popped == node) break
                        }
                        nextComponent++
                    }
                }
            }
        }
        return component
    }

    /**
     * Depth of each component along the longest path into it, which is the
     * tier it belongs in.
     *
     * Longest path rather than shortest: a tier must come after *everything*
     * it depends on, and the shortest path would happily place a component
     * ahead of one of its own ancestors.
     */
    private fun longestPathLayering(
        edges: Array<HashSet<Int>>,
        component: IntArray,
        componentCount: Int
    ): IntArray {
        val condensed = Array(componentCount) { HashSet<Int>() }
        val inDegree = IntArray(componentCount)
        for (from in edges.indices) {
            for (to in edges[from]) {
                val a = component[from]
                val b = component[to]
                if (a != b && condensed[a].add(b)) inDegree[b]++
            }
        }

        val depth = IntArray(componentCount)
        val queue = ArrayDeque<Int>()
        for (c in 0 until componentCount) if (inDegree[c] == 0) queue.addLast(c)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in condensed[current]) {
                depth[next] = maxOf(depth[next], depth[current] + 1)
                if (--inDegree[next] == 0) queue.addLast(next)
            }
        }
        return depth
    }
}

/**
 * Folds layer-lock tiers into a geometric blocking map, so a locked arrow is
 * simply an arrow with more blockers.
 *
 * Expressing locks as edges rather than as a separate legality rule means the
 * solver, the hint system, the "why can't I tap this" feedback and the
 * never-unsolvable guarantee all keep working untouched - they already handle
 * "blocked by something still on the board", and that is exactly what a lock
 * is. A parallel rule would have needed every one of them taught about it.
 *
 * Returns the map unchanged when nothing is locked, which is the common case.
 */
internal fun Map<Int, Set<Int>>.withLayerLocks(
    arrows: List<CurvedArrow>
): Map<Int, Set<Int>> {
    if (arrows.none { it.lockTier > 0 }) return this

    val tierOf = arrows.associate { it.id to it.lockTier }
    val byTier = arrows.groupBy({ it.lockTier }, { it.id })

    // The *populated* tiers in order, because the planner squeezes its
    // layering into a tier budget and can leave gaps. Reading "tier - 1"
    // directly would silently drop the lock wherever a number was skipped.
    val ladder = byTier.keys.sorted()
    val tierBelow = ladder.zipWithNext().associate { (lower, upper) -> upper to lower }

    // Only the nearest lower tier is named: tier 2 waits on tier 1, which
    // already waits on tier 0, so the ordering is transitive and listing
    // every ancestor would just inflate the graph.
    return mapValues { (id, blockers) ->
        val below = tierBelow[tierOf[id]]
        if (below == null) blockers else blockers + byTier[below].orEmpty()
    }
}
