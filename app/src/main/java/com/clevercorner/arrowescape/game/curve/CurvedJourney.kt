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

    const val LEVELS_PER_CHAPTER = 10

    val chapterCount: Int get() = ShapeCatalog.ALL.size

    val totalLevels: Int get() = chapterCount * LEVELS_PER_CHAPTER

    /** Zero-based chapter a level belongs to, wrapping past the end. */
    fun chapterOf(level: Int): Int =
        ((level - 1).coerceAtLeast(0) / LEVELS_PER_CHAPTER) % chapterCount

    fun designFor(level: Int): ShapeDesign = ShapeCatalog.ALL[chapterOf(level)]

    fun firstLevelOf(chapter: Int): Int = chapter * LEVELS_PER_CHAPTER + 1

    fun lastLevelOf(chapter: Int): Int = firstLevelOf(chapter) + LEVELS_PER_CHAPTER - 1
}
