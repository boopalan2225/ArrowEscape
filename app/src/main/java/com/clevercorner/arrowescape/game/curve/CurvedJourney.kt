package com.clevercorner.arrowescape.game.curve

/**
 * The curved game's level structure - the one place that says which shape a
 * level plays and how many levels exist.
 *
 * The journey is one chapter per catalogue shape, [LEVELS_PER_CHAPTER] levels
 * each. So the length of the game is a *consequence* of the catalogue: add a
 * shape and the journey grows by a chapter, with nothing else to update. The
 * Journey Map, the level generator and the developer picker all read from here
 * and therefore cannot drift apart.
 *
 * Levels past the end wrap round to the first chapter again (at the hardest
 * tier), so the game never runs out.
 */
object CurvedJourney {

    /**
     * Levels spent on one shape before moving to the next.
     *
     * Was ten, which is how the journey earned "too many identical levels":
     * ten boards of one silhouette differ only by seed and ramp, and no ramp
     * spread over ten steps is visible step to step. Stage 7 made shapes
     * cheap - the catalogue is the length of the game - so the honest fix is
     * fewer levels per shape and more shapes, rather than squeezing more
     * apparent variety out of the same silhouette.
     *
     * Five is small enough that consecutive levels are plainly different and
     * large enough to hold a real easy-to-hard arc within one shape.
     */
    const val LEVELS_PER_CHAPTER = 5

    val chapterCount: Int get() = ShapeCatalog.ALL.size

    val totalLevels: Int get() = chapterCount * LEVELS_PER_CHAPTER

    /**
     * Zero-based chapter a level belongs to.
     *
     * No longer wraps: past the authored catalogue, chapters keep counting
     * and [designFor] synthesises a shape for them. Wrapping was what
     * produced the "content cliff" - level 371 was level 1 again with a
     * harder tuning.
     */
    fun chapterOf(level: Int): Int = (level - 1).coerceAtLeast(0) / LEVELS_PER_CHAPTER

    /**
     * How many times the journey has looped by [level]. Zero on the first
     * pass through the catalogue.
     *
     * Retained because the per-lap mirror/rotation variation still reads
     * from it, and an authored shape met a second time should still be
     * presented differently.
     */
    fun lapOf(level: Int): Int =
        (level - 1).coerceAtLeast(0) / totalLevels

    fun designFor(level: Int): ShapeDesign = ShapeCatalog.designForChapter(chapterOf(level))

    fun firstLevelOf(chapter: Int): Int = chapter * LEVELS_PER_CHAPTER + 1

    fun lastLevelOf(chapter: Int): Int = firstLevelOf(chapter) + LEVELS_PER_CHAPTER - 1

    /**
     * How far through its chapter a level sits: 0 at the first, 1 at the last.
     *
     * A chapter is ten levels of one shape, and ten levels of one shape at
     * identical settings is a screensaver rather than a progression. This is
     * what lets the tuning wind up across a chapter instead of restating
     * itself ten times.
     */
    fun progressThroughChapter(level: Int): Float =
        ((level - 1).coerceAtLeast(0) % LEVELS_PER_CHAPTER) / (LEVELS_PER_CHAPTER - 1f)
}
