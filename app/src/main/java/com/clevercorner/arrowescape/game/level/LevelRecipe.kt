package com.clevercorner.arrowescape.game.level

/**
 * How mean a level is allowed to be.
 *
 * The shape catalogue ([LevelDesigns]) decides what a level LOOKS like; this
 * decides how it PLAYS. Keeping the two apart is deliberate - cranking the
 * grid size alone just makes a bigger soup, while the knobs below attack the
 * part that actually makes a player think.
 *
 * The numbers follow the v2 curve of the generator this port learned from:
 * tangle starts high and early rather than easing in, long snakes dominate
 * the mix, and short two-cell blockers are kept in deliberately - they are
 * the most annoying obstacle in the game for the space they occupy.
 */
enum class LevelKind(val label: String) {
    TUTORIAL("Tutorial"),
    NORMAL(""),
    BOSS("Boss"),
    GOD("God")
}

/**
 * How snake lengths are distributed across a board, as shares of the total.
 *
 * The carver treats these as quotas rather than dice rolls: whichever tier is
 * furthest below its share goes next. Dice would let a lucky run fill a board
 * with nothing but short hops; quotas guarantee the mix you asked for.
 */
data class LengthMix(
    val veryLong: Float,
    val long: Float,
    val medium: Float,
    val short: Float
) {
    companion object {
        val TUTORIAL = LengthMix(0f, 0f, 0f, 1f)
        val EARLY = LengthMix(veryLong = 0.25f, long = 0.30f, medium = 0.35f, short = 0.10f)
        val MID = LengthMix(veryLong = 0.40f, long = 0.30f, medium = 0.20f, short = 0.10f)
        val LATE = LengthMix(veryLong = 0.50f, long = 0.30f, medium = 0.10f, short = 0.10f)
    }
}

data class LevelRecipe(
    val kind: LevelKind,
    val gridSize: Int,
    /** Longest snake the carver may grow. */
    val maxSnakeLength: Int,
    val lengths: LengthMix,
    /** 0..1 bias toward bending at every body step; high values zig-zag hard. */
    val tangle: Float,
    /** How many route-bending dots to reserve space for. */
    val deflectors: Int,
    /** How many colour-locked pairs to weld together. */
    val lockedPairs: Int,
    val difficultyLabel: String
) {
    companion object {
        /** Every fifth level is a boss; every twentieth is a god level. */
        private const val BOSS_EVERY = 5
        private const val GOD_EVERY = 20

        /**
         * Mechanic gates. Levels before these are kept clean so the base rule
         * can be learned on its own - the reference generator calls this the
         * strict mechanic gate, and it is the difference between "hard" and
         * "baffling".
         */
        private const val FIRST_DEFLECTOR_LEVEL = 8
        private const val FIRST_LOCK_LEVEL = 16

        fun kindOf(level: Int): LevelKind = when {
            level <= 3 -> LevelKind.TUTORIAL
            level % GOD_EVERY == 0 -> LevelKind.GOD
            level % BOSS_EVERY == 0 -> LevelKind.BOSS
            else -> LevelKind.NORMAL
        }

        fun forLevel(level: Int, design: LevelDesigns.LevelDesign): LevelRecipe {
            val kind = kindOf(level)
            // 0 at level 1, 1 by level ~150 - the master dial the budgets read.
            val ramp = ((level - 1) / 149f).coerceIn(0f, 1f)

            val gridSize = design.gridSize + when (kind) {
                LevelKind.GOD -> 3
                LevelKind.BOSS -> 2
                else -> 0
            }
            val cells = gridSize * gridSize

            val maxSnakeLength = (design.maxSnakeLength + if (kind == LevelKind.NORMAL) 0 else 2)
                .coerceAtMost(9)

            return LevelRecipe(
                kind = kind,
                gridSize = gridSize,
                maxSnakeLength = maxSnakeLength,
                lengths = lengthsFor(level, kind),
                tangle = tangleFor(level, kind),
                deflectors = deflectorBudget(level, kind, ramp, cells),
                lockedPairs = lockBudget(level, kind, ramp),
                difficultyLabel = labelFor(design, kind)
            )
        }

        /**
         * Tangle is deliberately front-loaded: a level that looks like a
         * woven knot from the very first real board sets the expectation that
         * this is a thinking game, not a tapping one.
         */
        private fun tangleFor(level: Int, kind: LevelKind): Float {
            if (kind == LevelKind.TUTORIAL) return 0f
            val base = when {
                level <= 6 -> 0.50f
                level <= 12 -> 0.70f
                level <= 20 -> 0.85f
                else -> 1.0f
            }
            return when (kind) {
                LevelKind.BOSS -> (base + 0.20f).coerceIn(0.50f, 1f)
                LevelKind.GOD -> (base + 0.30f).coerceIn(0.65f, 1f)
                else -> base
            }
        }

        private fun lengthsFor(level: Int, kind: LevelKind): LengthMix = when {
            kind == LevelKind.TUTORIAL -> LengthMix.TUTORIAL
            kind == LevelKind.BOSS || kind == LevelKind.GOD -> LengthMix.LATE
            level <= 12 -> LengthMix.EARLY
            level <= 40 -> LengthMix.MID
            else -> LengthMix.LATE
        }

        /**
         * Dots are the strongest flavour in the game, so they stay rare: a
         * board sprinkled with them stops reading as a picture and starts
         * reading as noise. Capped against board area for the same reason.
         */
        private fun deflectorBudget(level: Int, kind: LevelKind, ramp: Float, cells: Int): Int {
            if (level < FIRST_DEFLECTOR_LEVEL || kind == LevelKind.TUTORIAL) return 0
            val base = 1 + (ramp * 5f).toInt()
            val bonus = when (kind) {
                LevelKind.GOD -> 3
                LevelKind.BOSS -> 2
                else -> 0
            }
            return (base + bonus).coerceAtMost(cells / 20)
        }

        /** Boss levels always get at least one pair, god levels at least two. */
        private fun lockBudget(level: Int, kind: LevelKind, ramp: Float): Int {
            if (level < FIRST_LOCK_LEVEL || kind == LevelKind.TUTORIAL) return 0
            val base = 1 + (ramp * 3f).toInt()
            return when (kind) {
                LevelKind.GOD -> (base + 3).coerceAtLeast(2)
                LevelKind.BOSS -> (base + 2).coerceAtLeast(1)
                else -> base
            }
        }

        private fun labelFor(design: LevelDesigns.LevelDesign, kind: LevelKind): String =
            if (kind.label.isEmpty()) design.difficultyLabel
            else "${design.difficultyLabel} - ${kind.label}"
    }
}
