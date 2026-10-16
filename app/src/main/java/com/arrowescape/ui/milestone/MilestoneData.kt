package com.arrowescape.ui.milestone

import com.arrowescape.data.GameMode
import com.arrowescape.game.curve.CurvedJourney
import com.arrowescape.game.curve.DifficultyTuning
import com.arrowescape.game.curve.ShapeCatalog
import com.arrowescape.game.curve.ShapeDesign
import com.arrowescape.game.level.LevelShape

/** What a chapter is drawn with: a grid-mode cell mask, or a curved-mode design. */
sealed interface MilestoneIcon {
    data class Grid(val shape: LevelShape) : MilestoneIcon
    data class Curved(val design: ShapeDesign) : MilestoneIcon
}

/**
 * A chapter in the player's journey (every [MilestoneData.LEVELS_PER_MILESTONE] levels).
 */
data class Milestone(
    val index: Int,
    val startLevel: Int,
    val endLevel: Int,
    val title: String,
    /** Second line under the title: the shape (grid mode) or the difficulty tier (curved). */
    val subtitle: String,
    val icon: MilestoneIcon,
    val emoji: String
) {
    val levelRange: String get() = "$startLevel-$endLevel"
}

/** The chapters of one game mode, and everything derived from them. */
class Journey(val milestones: List<Milestone>) {
    val totalLevels: Int = milestones.last().endLevel

    fun chapterIndexOf(level: Int): Int =
        ((level - 1) / MilestoneData.LEVELS_PER_MILESTONE).coerceIn(0, milestones.lastIndex)
}

/**
 * The two journeys. Grid mode keeps its hand-named chapters; curved mode
 * *derives* its chapters from [CurvedJourney], so the map can never disagree
 * with the levels the generator actually produces - or run out of room when
 * shapes are added.
 */
object MilestoneData {

    const val LEVELS_PER_MILESTONE = 10

    private fun grid(
        index: Int,
        title: String,
        shape: LevelShape,
        emoji: String
    ) = Milestone(
        index = index,
        startLevel = index * LEVELS_PER_MILESTONE + 1,
        endLevel = (index + 1) * LEVELS_PER_MILESTONE,
        title = title,
        subtitle = shape.displayName,
        icon = MilestoneIcon.Grid(shape),
        emoji = emoji
    )

    private val gridJourney = Journey(
        listOf(
            grid(0, "First Steps", LevelShape.SQUARE, "\uD83C\uDF31"),
            grid(1, "Rising Up", LevelShape.PYRAMID, "\u2B50"),
            grid(2, "Sharp Turns", LevelShape.DIAMOND, "\uD83D\uDD37"),
            grid(3, "Full Circle", LevelShape.CIRCLE, "\uD83D\uDD35"),
            grid(4, "Heartfelt", LevelShape.HEART, "\u2764\uFE0F"),
            grid(5, "Crossroads", LevelShape.CROSS, "\u271A"),
            grid(6, "Hexa Hive", LevelShape.HEXAGON, "\u2B22"),
            grid(7, "Star Power", LevelShape.STAR, "\uD83C\uDF1F"),
            grid(8, "Eclipse", LevelShape.CRESCENT, "\uD83C\uDF19"),
            grid(9, "X Marks It", LevelShape.LETTER_X, "\u2716\uFE0F"),
            // Hard category shapes
            grid(10, "Spiral Madness", LevelShape.SPIRAL, "\uD83C\uDF00"),
            grid(11, "Storm Surge", LevelShape.LIGHTNING, "\u26A1"),
            grid(12, "Royal Challenge", LevelShape.CROWN, "\uD83D\uDC51"),
            grid(13, "Ring Master", LevelShape.RING, "\uD83D\uDCAD"),
            grid(14, "Grand Finale", LevelShape.BUTTERFLY, "\uD83E\uDD8B")
        )
    )

    private val curvedJourney: Journey by lazy {
        Journey(
            ShapeCatalog.ALL.mapIndexed { chapter, design ->
                Milestone(
                    index = chapter,
                    startLevel = CurvedJourney.firstLevelOf(chapter),
                    endLevel = CurvedJourney.lastLevelOf(chapter),
                    title = design.name,
                    subtitle = DifficultyTuning.tierOf(CurvedJourney.firstLevelOf(chapter)),
                    icon = MilestoneIcon.Curved(design),
                    emoji = emojiFor(design.name)
                )
            }
        )
    }

    fun forMode(mode: GameMode): Journey = when (mode) {
        GameMode.CURVED -> curvedJourney
        GameMode.CLASSIC -> gridJourney
    }

    // Flavour only; an unknown (future) shape just gets a puzzle piece.
    private val EMOJI = mapOf(
        "Circle" to "\uD83D\uDD35", "Heart" to "\u2764\uFE0F", "Star" to "\u2B50",
        "Kitten" to "\uD83D\uDC31", "Barn Owl" to "\uD83E\uDD89", "Fish" to "\uD83D\uDC1F",
        "Butterfly" to "\uD83E\uDD8B", "Frog" to "\uD83D\uDC38", "Fawn" to "\uD83E\uDD8C",
        "Snail" to "\uD83D\uDC0C", "Moon" to "\uD83C\uDF19", "Leaf" to "\uD83C\uDF43",
        "Star6" to "\u2744\uFE0F", "Star8" to "\u2600\uFE0F", "Owl" to "\uD83E\uDD89",
        "Cat" to "\uD83D\uDC08", "Panda" to "\uD83D\uDC3C", "Deer" to "\uD83E\uDD8C",
        "Crab" to "\uD83E\uDD80", "Plus" to "\u2795", "Diamond" to "\uD83D\uDD37",
        "Hexagon" to "\u2B22", "Octagon" to "\uD83D\uDED1", "Pentagon" to "\u2B1F",
        "Star4" to "\u2728", "XCross" to "\u2716\uFE0F", "ArrowUp" to "\u2B06\uFE0F",
        "Chevron" to "\u00BB", "Lightning" to "\u26A1", "House" to "\uD83C\uDFE0",
        "Crown" to "\uD83D\uDC51", "Shield" to "\uD83D\uDEE1\uFE0F", "Gem" to "\uD83D\uDC8E",
        "Tree" to "\uD83C\uDF32", "Rocket" to "\uD83D\uDE80", "Cloud" to "\u2601\uFE0F",
        "Bowtie" to "\uD83C\uDF80"
    )

    private fun emojiFor(name: String) = EMOJI[name] ?: "\uD83E\uDDE9"

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
