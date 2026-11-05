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
 * A chapter list used to be a fixed snapshot, which is how the Journey Map
 * once ended up unable to show a level the player had already reached: a
 * frozen list is a list that goes stale the moment the catalogue it was
 * drawn from grows. [milestonesFor] takes the player's own progress and
 * sizes itself to it instead - never fewer chapters than exist to show.
 *
 * [minChapters] is the ceiling as well as the floor: every [milestonesUpTo]
 * implementation here returns at most that many chapters regardless of how
 * many are asked for, because the number of chapters that exist to show is
 * a property of the catalogue, not of how far ahead a caller asks to look.
 * A mode whose content genuinely ends (grid) and a mode whose generator
 * could keep inventing shapes forever (curved) therefore behave identically
 * from this class's point of view: both report a fixed, finite list, and
 * both stop exactly where their own catalogue does.
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

    /**
     * Chapter [level] belongs to, clamped to what [milestonesFor] actually
     * returns for [highestLevel] - the real list size, not the chapter count
     * requested of it. A journey is free to hand back fewer chapters than
     * asked (curved does, once the authored catalogue runs out), and a
     * clamp against the request rather than the result would point this at
     * a card that was never drawn.
     */
    fun chapterIndexOf(level: Int, highestLevel: Int): Int =
        chapterIndex(level).coerceIn(0, milestonesFor(highestLevel).size - 1)

    /** How many levels are currently shown as real chapters for a player at [highestLevel]. */
    fun totalLevelsFor(highestLevel: Int): Int = milestonesFor(highestLevel).last().endLevel

    /**
     * Levels the developer "unlock all" tool should grant for a player whose
     * own progress is [currentHighestLevel].
     *
     * **The authored catalogue, and nothing beyond it.** A dev tool exists to
     * remove the app's own roadblocks, not to invent content past them, so
     * the answer is simply where the hand-made chapters stop: 215 for curved
     * (43 catalogue shapes x 5), 150 for grid (15 chapters x 10). Both
     * numbers come from the catalogue itself, so neither can go stale when
     * shapes are added - and [milestonesUpTo] enforces the same ceiling on
     * everything else this class answers, so this number, [totalLevelsFor]
     * and the Journey Map's own chapter list can never disagree.
     *
     * Curved levels used to continue past 215 for a live player, with
     * [ShapeCatalog.designForChapter] synthesising a shape for whatever came
     * next. That generator capability still exists - it is how a developer
     * previews a chapter before it is authored - but it is no longer
     * reachable by playing: see [CurvedJourney.authoredLevelCount].
     *
     * Three staleness bugs have lived here, and each one came from trusting
     * a number that was true when it was written:
     *
     * 1. An absolute constant (1000, then 400) needed hand-retuning whenever
     *    the catalogue's real size moved. Now nothing is written down.
     * 2. Scaling off the catalogue still went stale when a *player* stepped
     *    past it: a developer could walk past the catalogue by hand and
     *    then be demoted by the very button meant to help them. Hence
     *    [maxOf] - unlock-all may never offer *less* than a level this
     *    player has already reached.
     * 3. Taking that second term from [totalLevelsFor] made the button
     *    **ratchet**. That function is the Journey Map's, and the map adds
     *    [LOOKAHEAD_CHAPTERS] on purpose so "Coming Soon" sits ahead of the
     *    player rather than underfoot. As a target it reads "a hundred
     *    levels past wherever you are" - and clicking moves where you are,
     *    so every click added another hundred: 645, 745, 845. Lookahead is
     *    right for a map and wrong for a target.
     *
     * The result is a **fixed point**: grant it, ask again, same answer.
     */
    fun devUnlockLevelCount(currentHighestLevel: Int): Int {
        val authoredTarget = milestonesUpTo(minChapters).last().endLevel
        val reachedTarget = milestonesUpTo(chapterIndex(currentHighestLevel) + 1).last().endLevel
        return maxOf(authoredTarget, reachedTarget)
    }


    companion object {
        /**
         * Real chapters materialised ahead of the player's own progress.
         * Generous on purpose: the map should read as "here is what's next",
         * not as a wall that happens to sit exactly one level past wherever
         * the player last stopped.
         */
        const val LOOKAHEAD_CHAPTERS = 20
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
            // Clamped to the authored catalogue on purpose: the catalogue
            // *is* the roadblock a dev tool should enable and a player
            // should eventually clear, so the chapter list - and everything
            // derived from it, including [Journey.totalLevelsFor] - stops
            // exactly where [CurvedJourney.authoredLevelCount] does. Past
            // that, `designForChapter` could still synthesise a card, but a
            // card for content nobody has shipped is not a preview, it is a
            // promise this list cannot keep - see [ComingSoonCard] for the
            // promise this journey is allowed to make instead.
            milestonesUpTo = { count ->
                (0 until minOf(count, ShapeCatalog.authoredChapterCount)).map { chapter ->
                    // designForChapter is the exact function the generator
                    // calls to decide what a level plays, so a card can never
                    // show a shape the generator wouldn't. The chapter range
                    // above is what stops it being called for chapters that
                    // are not yet authored.
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
