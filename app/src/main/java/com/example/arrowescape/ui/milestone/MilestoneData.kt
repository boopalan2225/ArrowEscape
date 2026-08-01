package com.example.arrowescape.ui.milestone

import com.example.arrowescape.game.level.LevelShape

/**
 * A milestone represents a chapter in the player's journey (every 10 levels).
 */
data class Milestone(
    val index: Int,
    val startLevel: Int,
    val endLevel: Int,
    val title: String,
    val shape: LevelShape,
    val emoji: String
) {
    val levelRange: String get() = "$startLevel-$endLevel"
}

/**
 * Provides the milestone map for the 100-level journey.
 */
object MilestoneData {

    const val TOTAL_LEVELS = 100
    const val LEVELS_PER_MILESTONE = 10

    val milestones: List<Milestone> = listOf(
        Milestone(0, 1, 10, "First Steps", LevelShape.SQUARE, "\uD83C\uDF31"),
        Milestone(1, 11, 20, "Rising Star", LevelShape.TRIANGLE, "\u2B50"),
        Milestone(2, 21, 30, "Sharp Mind", LevelShape.DIAMOND, "\uD83D\uDD37"),
        Milestone(3, 31, 40, "Full Circle", LevelShape.CIRCLE, "\uD83D\uDD35"),
        Milestone(4, 41, 50, "Heartfelt", LevelShape.HEART, "\u2764\uFE0F"),
        Milestone(5, 51, 60, "Crossroads", LevelShape.CROSS, "\u271A"),
        Milestone(6, 61, 70, "Pyramid Peak", LevelShape.PYRAMID, "\uD83D\uDD3A"),
        Milestone(7, 71, 80, "Star Power", LevelShape.STAR, "\uD83C\uDF1F"),
        Milestone(8, 81, 90, "Master Class", LevelShape.DIAMOND, "\uD83D\uDC8E"),
        Milestone(9, 91, 100, "Grand Finale", LevelShape.CIRCLE, "\uD83C\uDFC6")
    )

    /**
     * Returns the milestone that a given level belongs to.
     */
    fun milestoneForLevel(level: Int): Milestone {
        val idx = ((level - 1) / LEVELS_PER_MILESTONE).coerceIn(0, milestones.size - 1)
        return milestones[idx]
    }

    /**
     * State of a milestone relative to the player's current progress.
     */
    enum class MilestoneState { COMPLETED, CURRENT, LOCKED }

    fun stateFor(milestone: Milestone, highestLevel: Int): MilestoneState {
        return when {
            highestLevel > milestone.endLevel -> MilestoneState.COMPLETED
            highestLevel >= milestone.startLevel -> MilestoneState.CURRENT
            else -> MilestoneState.LOCKED
        }
    }

    /**
     * Progress (0f..1f) within a milestone based on highest level reached.
     */
    fun progressWithin(milestone: Milestone, highestLevel: Int): Float {
        return when {
            highestLevel > milestone.endLevel -> 1f
            highestLevel < milestone.startLevel -> 0f
            else -> {
                val done = (highestLevel - milestone.startLevel).coerceAtLeast(0)
                (done.toFloat() / LEVELS_PER_MILESTONE).coerceIn(0f, 1f)
            }
        }
    }
}
