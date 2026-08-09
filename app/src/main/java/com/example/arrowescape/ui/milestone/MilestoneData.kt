package com.example.arrowescape.ui.milestone

import com.example.arrowescape.game.level.LevelShape

/**
 * A milestone/chapter in the player's journey (every 10 levels).
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

object MilestoneData {

    const val TOTAL_LEVELS = 150
    const val LEVELS_PER_MILESTONE = 10

    val milestones: List<Milestone> = listOf(
        Milestone(0, 1, 10, "First Steps", LevelShape.SQUARE, "\uD83C\uDF31"),
        Milestone(1, 11, 20, "Rising Up", LevelShape.PYRAMID, "\u2B50"),
        Milestone(2, 21, 30, "Sharp Turns", LevelShape.DIAMOND, "\uD83D\uDD37"),
        Milestone(3, 31, 40, "Full Circle", LevelShape.CIRCLE, "\uD83D\uDD35"),
        Milestone(4, 41, 50, "Heartfelt", LevelShape.HEART, "\u2764\uFE0F"),
        Milestone(5, 51, 60, "Crossroads", LevelShape.CROSS, "\u271A"),
        Milestone(6, 61, 70, "Hexa Hive", LevelShape.HEXAGON, "\u2B22"),
        Milestone(7, 71, 80, "Star Power", LevelShape.STAR, "\uD83C\uDF1F"),
        Milestone(8, 81, 90, "Eclipse", LevelShape.CRESCENT, "\uD83C\uDF19"),
        Milestone(9, 91, 100, "X Marks It", LevelShape.LETTER_X, "\u2716\uFE0F"),
        // Hard category shapes
        Milestone(10, 101, 110, "Spiral Madness", LevelShape.SPIRAL, "\uD83C\uDF00"),
        Milestone(11, 111, 120, "Storm Surge", LevelShape.LIGHTNING, "\u26A1"),
        Milestone(12, 121, 130, "Royal Challenge", LevelShape.CROWN, "\uD83D\uDC51"),
        Milestone(13, 131, 140, "Ring Master", LevelShape.RING, "\uD83D\uDCAD"),
        Milestone(14, 141, 150, "Grand Finale", LevelShape.BUTTERFLY, "\uD83E\uDD8B")
    )

    fun milestoneForLevel(level: Int): Milestone {
        val idx = ((level - 1) / LEVELS_PER_MILESTONE).coerceIn(0, milestones.size - 1)
        return milestones[idx]
    }

    enum class LevelState { COMPLETED, CURRENT, LOCKED }

    /**
     * State of an individual level relative to progress.
     * A level is CURRENT if it's the highest reached; COMPLETED if below it;
     * LOCKED if above.
     */
    fun levelState(level: Int, highestLevel: Int): LevelState = when {
        level < highestLevel -> LevelState.COMPLETED
        level == highestLevel -> LevelState.CURRENT
        else -> LevelState.LOCKED
    }
}
