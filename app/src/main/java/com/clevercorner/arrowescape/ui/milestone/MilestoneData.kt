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
    /**
     * @param part Which section of this title's shape this is, 1-based, or
     *   null for a title that is not split. Grid mode's hand-authored themes
     *   outnumber its shapes-per-section, so one theme now spans
     *   [MilestoneData.SECTIONS_PER_THEME] sections rather than one - this is
     *   what keeps two adjacent sections reading as "First Steps, Part 1" and
     *   "First Steps, Part 2" instead of two identically-titled cards.
     */
    data class Localized(@StringRes val resId: Int, val part: Int? = null) : MilestoneTitle
    data class Raw(val value: String) : MilestoneTitle
}

@Composable
fun MilestoneTitle.resolve(): String = when (this) {
    is MilestoneTitle.Localized -> part?.let { stringResource(R.string.milestone_title_part, stringResource(resId), it) }
        ?: stringResource(resId)
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
     * Whole chapters a player who has reached [highestLevel] has finished.
     *
     * A coin-economy question, not a map one - [chapterIndexOf] answers
     * "which chapter card should be showing right now", clamped to what the
     * map actually draws; this is unclamped and asks something different:
     * how many chapters are fully *behind* this player. The level they are
     * currently on is still in progress, so its own zero-based chapter
     * index already is that count (a player on the first level of chapter 3
     * has finished chapters 0 through 2 - three of them, which is chapter
     * index 3).
     *
     * Exists because this used to be reimplemented at every call site as
     * `(highestLevel - 1) / MilestoneData.LEVELS_PER_MILESTONE` - a classic-
     * only constant applied to both modes. Curved's real chapter is half
     * that size, so curved players were being credited for finishing half
     * as many chapters as they actually had. Routing through each journey's
     * own [chapterIndex] fixes that, and means a future change to either
     * mode's chapter length - like this one - cannot silently throw the
     * coin economy out of step with the map again.
     */
    fun chaptersClearedFor(highestLevel: Int): Int = chapterIndex(highestLevel)

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
     * (43 catalogue shapes x 5), 150 for grid (15 themes x 2 sections x 5). Both
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

    /**
     * Levels in one chapter card - 5, the same cadence [CurvedJourney] uses,
     * so neither journey feels slower than the other to walk through.
     *
     * Used to be 10. Classic's actual content has no matching concept of
     * "one chapter, one shape" to preserve - [LevelDesigns.forLevel] gives
     * every individual level its own unique shape already, so the grid
     * journey's chapter theme was always a narrative label over that, never
     * a literal description of what plays. Halving it does not touch a
     * single generated board; it only redraws the map.
     */
    const val LEVELS_PER_MILESTONE = 5

    /**
     * How many chapter cards share one hand-authored theme.
     *
     * There are only [GRID_THEMES]`.size` hand-written titles, and at 5
     * levels a chapter that is now half as many levels as a theme used to
     * cover - so each theme now spans this many consecutive cards rather
     * than one, told apart by [MilestoneTitle.Localized]'s `part` number.
     */
    private const val SECTIONS_PER_THEME = 2

    /** One hand-authored theme: a title, a representative shape, an emoji. */
    private class GridTheme(
        @StringRes val titleRes: Int,
        val shape: LevelShape,
        val emoji: String
    )

    private val GRID_THEMES: List<GridTheme> = listOf(
            GridTheme(R.string.milestone_title_first_steps, LevelShape.SQUARE, "\uD83C\uDF31"),
            GridTheme(R.string.milestone_title_rising_up, LevelShape.PYRAMID, "\u2B50"),
            GridTheme(R.string.milestone_title_sharp_turns, LevelShape.DIAMOND, "\uD83D\uDD37"),
            GridTheme(R.string.milestone_title_full_circle, LevelShape.CIRCLE, "\uD83D\uDD35"),
            GridTheme(R.string.milestone_title_heartfelt, LevelShape.HEART, "\u2764\uFE0F"),
            GridTheme(R.string.milestone_title_crossroads, LevelShape.CROSS, "\u271A"),
            GridTheme(R.string.milestone_title_hexa_hive, LevelShape.HEXAGON, "\u2B22"),
            GridTheme(R.string.milestone_title_star_power, LevelShape.STAR, "\uD83C\uDF1F"),
            GridTheme(R.string.milestone_title_eclipse, LevelShape.CRESCENT, "\uD83C\uDF19"),
            GridTheme(R.string.milestone_title_x_marks_it, LevelShape.LETTER_X, "\u2716\uFE0F"),
            // Hard category shapes
            GridTheme(R.string.milestone_title_spiral_madness, LevelShape.SPIRAL, "\uD83C\uDF00"),
            GridTheme(R.string.milestone_title_storm_surge, LevelShape.LIGHTNING, "\u26A1"),
            GridTheme(R.string.milestone_title_royal_challenge, LevelShape.CROWN, "\uD83D\uDC51"),
            GridTheme(R.string.milestone_title_ring_master, LevelShape.RING, "\uD83D\uDCAD"),
            GridTheme(R.string.milestone_title_grand_finale, LevelShape.BUTTERFLY, "\uD83E\uDD8B")
    )

    /**
     * [GRID_THEMES], expanded into [SECTIONS_PER_THEME] 5-level cards each -
     * 30 cards covering the same 150 levels the old 15 ten-level cards did.
     * `grid()`'s old per-call index/title bookkeeping is gone because it is
     * now entirely mechanical: section `n` of theme `t` is always chapter
     * `t * SECTIONS_PER_THEME + n`, titled with the theme's name and `n + 1`.
     */
    private val GRID_CHAPTERS: List<Milestone> = GRID_THEMES.flatMapIndexed { themeIndex, theme ->
        (0 until SECTIONS_PER_THEME).map { section ->
            val index = themeIndex * SECTIONS_PER_THEME + section
            Milestone(
                index = index,
                startLevel = index * LEVELS_PER_MILESTONE + 1,
                endLevel = (index + 1) * LEVELS_PER_MILESTONE,
                title = MilestoneTitle.Localized(theme.titleRes, part = section + 1),
                subtitle = theme.shape.displayName,
                icon = MilestoneIcon.Grid(theme.shape),
                emoji = theme.emoji
            )
        }
    }

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
