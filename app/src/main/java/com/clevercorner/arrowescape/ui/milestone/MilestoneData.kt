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
 * A chapter in the player's journey (every [MilestoneData.LEVELS_PER_MILESTONE] levels
 * in grid mode; [com.clevercorner.arrowescape.game.curve.CurvedJourney.LEVELS_PER_CHAPTER]
 * in curved mode - the two differ, so nothing here should assume a constant).
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

    /** How many levels this chapter actually spans - see the class doc on why this isn't a constant. */
    val levelCount: Int get() = endLevel - startLevel + 1
}

/**
 * The chapters of one game mode, and everything derived from them.
 *
 * A chapter list used to be a fixed snapshot, which is exactly how the
 * Journey Map ended up unable to show a level the player had already reached:
 * curved mode's content is unbounded (see `ShapeSynthesis`), so any frozen
 * list is a list that is eventually wrong. [milestonesFor] therefore takes
 * the player's own progress and sizes itself to it - never fewer chapters
 * than exist to show, and a lookahead buffer so "Coming Soon" always sits
 * comfortably ahead rather than immediately behind the player's last step.
 *
 * [minChapters] is the floor: a mode whose content genuinely ends (grid) just
 * reports its full fixed list regardless of how many are asked for, while a
 * mode whose content does not (curved) grows past it.
 */
class Journey(
    private val chapterIndex: (Int) -> Int,
    private val milestonesUpTo: (chapterCount: Int) -> List<Milestone>,
    private val minChapters: Int
) {
    /**
     * Chapters that exist to show right now: every one up to and including
     * the player's own highest level, plus [LOOKAHEAD_CHAPTERS] more so the
     * map always previews real upcoming content rather than ending exactly
     * where the player happens to be standing.
     */
    fun chapterCountFor(highestLevel: Int): Int =
        maxOf(minChapters, chapterIndex(highestLevel) + 1 + LOOKAHEAD_CHAPTERS)

    /** The chapter cards to show for a player at [highestLevel]. See [chapterCountFor]. */
    fun milestonesFor(highestLevel: Int): List<Milestone> = milestonesUpTo(chapterCountFor(highestLevel))

    /** Chapter [level] belongs to, clamped to what [milestonesFor] would show for [highestLevel]. */
    fun chapterIndexOf(level: Int, highestLevel: Int): Int =
        chapterIndex(level).coerceIn(0, chapterCountFor(highestLevel) - 1)

    /** How many levels are currently shown as real chapters for a player at [highestLevel]. */
    fun totalLevelsFor(highestLevel: Int): Int = milestonesFor(highestLevel).last().endLevel

    /**
     * Levels the developer "unlock all" tool should currently grant.
     *
     * Deliberately NOT a fixed level number - that was tried twice in the
     * same session (1000, then 400) and both needed a human to come back
     * and retune them as soon as the authored catalogue's real size made
     * the arithmetic land somewhere unintended. [catalogueMultiple] scales
     * against [minChapters] instead, so this grows automatically whenever
     * the catalogue does, with nothing here to ever come back and edit.
     *
     * For grid mode this has no effect: [milestonesUpTo] ignores whatever
     * chapter count it is asked for and always returns the real, finite
     * list - asking for more chapters than exist just asks for the same
     * list again, because past the real ending there is only a repeating
     * fallback square to unlock, not new content.
     */
    fun devUnlockLevelCount(catalogueMultiple: Int = DEV_UNLOCK_CATALOGUE_MULTIPLE): Int =
        milestonesUpTo(minChapters * catalogueMultiple).last().endLevel

    companion object {
        /**
         * Real chapters materialised ahead of the player's own progress.
         * Generous on purpose: the map should read as "here is what's next",
         * not as a wall that happens to sit exactly one level past wherever
         * the player last stopped.
         */
        const val LOOKAHEAD_CHAPTERS = 20

        /**
         * How many times the current authored catalogue [devUnlockLevelCount]
         * reaches for on an endless journey (curved). 3x today's 41 authored
         * chapters lands at 615 levels - comfortably inside the 500-600
         * target measured as the point where synthesised names ("Pebble",
         * "Leaf") start repeating more than they're fresh - and stays there
         * proportionally if the authored catalogue grows later.
         */
        const val DEV_UNLOCK_CATALOGUE_MULTIPLE = 3
    }
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

    private val GRID_CHAPTERS: List<Milestone> = listOf(
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

    private val gridJourney = Journey(
        // The grid journey ends rather than looping: past the last chapter a
        // player stays in it. Clamping here matches that, and there is only
        // ever one lap to show.
        chapterIndex = { level -> (level - 1).coerceAtLeast(0) / LEVELS_PER_MILESTONE },
        // Ignores the requested chapter count on purpose: the grid game's
        // content genuinely stops here (see `LevelDesigns.forLevel`'s
        // fallback), so there is nothing further to materialise no matter
        // how far ahead the caller asks to look.
        milestonesUpTo = { GRID_CHAPTERS },
        minChapters = GRID_CHAPTERS.size
    )

    private val curvedJourney: Journey by lazy {
        Journey(
            // Not a copy of the chapter arithmetic - the function itself.
            // The map cannot drift from the shape the generator deals if
            // both ask the same question of the same object.
            chapterIndex = CurvedJourney::chapterOf,
            milestonesUpTo = { count ->
                (0 until count).map { chapter ->
                    // designForChapter is the exact function the generator
                    // calls to decide what a level plays - authored shape or
                    // synthesised one, it makes no difference here. That is
                    // the whole fix: the map can show every chapter that is
                    // actually playable, not just the hand-authored ones.
                    val design = ShapeCatalog.designForChapter(chapter)
                    val first = CurvedJourney.firstLevelOf(chapter)
                    Milestone(
                        index = chapter,
                        startLevel = first,
                        endLevel = CurvedJourney.lastLevelOf(chapter),
                        title = MilestoneTitle.Raw(design.name),
                        subtitle = DifficultyTuning.tierOf(first),
                        icon = MilestoneIcon.Curved(design),
                        emoji = emojiFor(design.name)
                    )
                }
            },
            minChapters = ShapeCatalog.authoredChapterCount
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
