package com.clevercorner.arrowescape.ui.milestone

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.game.curve.CurvedJourney
import com.clevercorner.arrowescape.game.curve.DifficultyTuning
import com.clevercorner.arrowescape.game.curve.ShapeCatalog
import com.clevercorner.arrowescape.game.curve.ShapeDesign
import com.clevercorner.arrowescape.game.level.LevelShape

/** What a chapter is drawn with: a grid-mode cell mask, or a curved-mode design. */
sealed interface MilestoneIcon {
    data class Grid(val shape: LevelShape) : MilestoneIcon
    data class Curved(val design: ShapeDesign) : MilestoneIcon
}

/**
 * A chapter's title either comes from a translatable resource (the
 * hand-authored grid journey) or is a raw name (curved-mode shapes, whose
 * names double as lookup keys elsewhere in the shape engine and are not
 * independently translatable without a larger catalog rework).
 */
sealed interface MilestoneTitle {
    data class Localized(@StringRes val resId: Int) : MilestoneTitle
    data class Raw(val value: String) : MilestoneTitle
}

@Composable
fun MilestoneTitle.resolve(): String = when (this) {
    is MilestoneTitle.Localized -> stringResource(resId)
    is MilestoneTitle.Raw -> value
}

/**
 * A chapter in the player's journey (every [MilestoneData.LEVELS_PER_MILESTONE] levels).
 */
data class Milestone(
    val index: Int,
    val startLevel: Int,
    val endLevel: Int,
    val title: MilestoneTitle,
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
        @StringRes titleRes: Int,
        shape: LevelShape,
        emoji: String
    ) = Milestone(
        index = index,
        startLevel = index * LEVELS_PER_MILESTONE + 1,
        endLevel = (index + 1) * LEVELS_PER_MILESTONE,
        title = MilestoneTitle.Localized(titleRes),
        subtitle = shape.displayName,
        icon = MilestoneIcon.Grid(shape),
        emoji = emoji
    )

    private val gridJourney = Journey(
        listOf(
            grid(0, R.string.milestone_title_first_steps, LevelShape.SQUARE, "\uD83C\uDF31"),
            grid(1, R.string.milestone_title_rising_up, LevelShape.PYRAMID, "\u2B50"),
            grid(2, R.string.milestone_title_sharp_turns, LevelShape.DIAMOND, "\uD83D\uDD37"),
            grid(3, R.string.milestone_title_full_circle, LevelShape.CIRCLE, "\uD83D\uDD35"),
            grid(4, R.string.milestone_title_heartfelt, LevelShape.HEART, "\u2764\uFE0F"),
            grid(5, R.string.milestone_title_crossroads, LevelShape.CROSS, "\u271A"),
            grid(6, R.string.milestone_title_hexa_hive, LevelShape.HEXAGON, "\u2B22"),
            grid(7, R.string.milestone_title_star_power, LevelShape.STAR, "\uD83C\uDF1F"),
            grid(8, R.string.milestone_title_eclipse, LevelShape.CRESCENT, "\uD83C\uDF19"),
            grid(9, R.string.milestone_title_x_marks_it, LevelShape.LETTER_X, "\u2716\uFE0F"),
            // Hard category shapes
            grid(10, R.string.milestone_title_spiral_madness, LevelShape.SPIRAL, "\uD83C\uDF00"),
            grid(11, R.string.milestone_title_storm_surge, LevelShape.LIGHTNING, "\u26A1"),
            grid(12, R.string.milestone_title_royal_challenge, LevelShape.CROWN, "\uD83D\uDC51"),
            grid(13, R.string.milestone_title_ring_master, LevelShape.RING, "\uD83D\uDCAD"),
            grid(14, R.string.milestone_title_grand_finale, LevelShape.BUTTERFLY, "\uD83E\uDD8B")
        )
    )

    private val curvedJourney: Journey by lazy {
        Journey(
            ShapeCatalog.ALL.mapIndexed { chapter, design ->
                Milestone(
                    index = chapter,
                    startLevel = CurvedJourney.firstLevelOf(chapter),
                    endLevel = CurvedJourney.lastLevelOf(chapter),
                    title = MilestoneTitle.Raw(design.name),
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
