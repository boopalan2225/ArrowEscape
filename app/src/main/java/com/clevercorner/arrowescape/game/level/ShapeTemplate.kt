package com.clevercorner.arrowescape.game.level

/**
 * Bitmap-authored shape templates: the 25 generic category bitmaps used for
 * journey-map chapter icons (see [getValidCells]), plus [scaleTemplate] -
 * shared by those icons and by every per-level design in [LevelDesigns].
 *
 * Each template is a list of strings where:
 * - '#' = filled cell (arrow can be placed)
 * - '.' = empty cell
 */
object ShapeTemplate {

    /**
     * Scales a bitmap template to fit the target grid size.
     * Returns a set of (row, col) pairs that are "inside" the shape.
     *
     * Delegates to [TemplateMask], which traces the bitmap's boundary once
     * and samples that geometry at the target resolution. The previous body
     * mapped each target cell back to one source cell with integer division,
     * which is resampling by copying pixels - upscaling a 12-row template
     * onto a 22-row grid duplicated whole rows verbatim rather than placing a
     * diagonal edge where it actually belongs. Tracing first and sampling the
     * trace is what turns a blown-up bitmap into a shape with real edges.
     */
    fun scaleTemplate(template: List<String>, targetRows: Int, targetCols: Int): Set<Pair<Int, Int>> =
        TemplateMask.scaleTemplate(template, targetRows, targetCols)

    // ==================== BASIC SHAPES ====================
    
    val SQUARE = """
        ############
        ############
        ############
        ############
        ############
        ############
        ############
        ############
        ############
        ############
        ############
        ############
    """.toTemplate()

    val CIRCLE = """
        ....####....
        ..########..
        .##########.
        ############
        ############
        ############
        ############
        ############
        ############
        .##########.
        ..########..
        ....####....
    """.toTemplate()

    val TRIANGLE = """
        ......#.....
        .....###....
        ....#####...
        ...#######..
        ..#########.
        .###########
        ############
    """.toTemplate()

    val DIAMOND = """
        ......#.....
        .....###....
        ....#####...
        ...#######..
        ..#########.
        .###########
        ..#########.
        ...#######..
        ....#####...
        .....###....
        ......#.....
    """.toTemplate()

    val HEART = """
        ..##...##..
        .####.####.
        ###########
        ###########
        ###########
        .#########.
        ..#######..
        ...#####...
        ....###....
        .....#.....
    """.toTemplate()

    val STAR = """
        ......#......
        .....###.....
        ....#####....
        #############
        .###########.
        ..#########..
        ...#######...
        ..#########..
        .####...####.
        ####.....####
        ##.........##
    """.toTemplate()

    val CROSS = """
        ....####....
        ....####....
        ....####....
        ....####....
        ############
        ############
        ############
        ############
        ....####....
        ....####....
        ....####....
        ....####....
    """.toTemplate()

    val HEXAGON = """
        ...######...
        ..########..
        .##########.
        ############
        ############
        ############
        ############
        ############
        .##########.
        ..########..
        ...######...
    """.toTemplate()

    // ==================== INTERMEDIATE SHAPES ====================

    val PYRAMID = """
        ......#.....
        .....###....
        ....#####...
        ...#######..
        ..#########.
        .###########
        ############
    """.toTemplate()

    val ARROW_UP = """
        ......#.....
        .....###....
        ....#####...
        ...#######..
        ..#########.
        .###########
        ....####....
        ....####....
        ....####....
        ....####....
        ....####....
        ....####....
    """.toTemplate()

    val HOURGLASS = """
        ############
        .##########.
        ..########..
        ...######...
        ....####....
        .....##.....
        .....##.....
        ....####....
        ...######...
        ..########..
        .##########.
        ############
    """.toTemplate()

    val OVAL = """
        ...######...
        ..########..
        .##########.
        ############
        ############
        ############
        ############
        ############
        ############
        .##########.
        ..########..
        ...######...
    """.toTemplate()

    val CRESCENT = """
        ...#####....
        ..#######...
        .########...
        #########...
        #########...
        #########...
        #########...
        #########...
        .########...
        ..#######...
        ...#####....
    """.toTemplate()

    val LETTER_X = """
        ##........##
        ###......###
        .###....###.
        ..###..###..
        ...######...
        ....####....
        ...######...
        ..###..###..
        .###....###.
        ###......###
        ##........##
    """.toTemplate()

    // ==================== HARD CATEGORY SHAPES ====================

    val CROWN = """
        .#...#...#..
        .#...#...#..
        ##..###..##.
        ###.###.###.
        ###########.
        ############
        ############
        .##########.
    """.toTemplate()

    val RING = """
        ...######...
        ..########..
        .####..####.
        ####....####
        ###......###
        ###......###
        ####....####
        .####..####.
        ..########..
        ...######...
    """.toTemplate()

    val LIGHTNING = """
        ...#####....
        ..#####.....
        .#####......
        ########....
        ...#######..
        ......#####.
        .....#####..
        ....#####...
        ...#####....
        ..#####.....
    """.toTemplate()

    val SPIRAL = """
        ..########..
        .##......##.
        ##..####..##
        #..######..#
        #.##....#..#
        #.#..##.#..#
        #.#.##..#..#
        #..#....##.#
        #..######..#
        ##..####..##
        .##......##.
        ..########..
    """.toTemplate()

    val BUTTERFLY = """
        ##.......##.
        ####...####.
        #####.#####.
        ####...####.
        ##..#.#..##.
        ####...####.
        #####.#####.
        ####...####.
        ##.......##.
    """.toTemplate()

    // ==================== THEMED SHAPES ====================

    val SAILBOAT = """
        .......#......
        ......##......
        .....###......
        ....####......
        ...#####......
        ..######......
        .#######......
        ########......
        ......##......
        ..##########..
        .############.
        ..##########..
    """.toTemplate()

    val MUSIC_NOTE = """
        ....########
        ....########
        ....##......
        ....##......
        ....##......
        ....##......
        ....##......
        ....##......
        ..####......
        .######.....
        .######.....
        ..####......
    """.toTemplate()

    val YIN_YANG = """
        ...######...
        ..########..
        .####..####.
        ####.##.####
        ###.####.###
        ###.####.###
        ###..##..###
        ####....####
        .####..####.
        ..########..
        ...######...
    """.toTemplate()

    val ANCHOR = """
        ....####....
        ...######...
        ....####....
        ....####....
        ....####....
        ....####....
        ##..####..##
        ###.####.###
        .###....###.
        ..########..
        ...######...
    """.toTemplate()

    val TREE = """
        ......#.....
        .....###....
        ....#####...
        ...#######..
        ....#####...
        ...#######..
        ..#########.
        .###########
        ....###.....
        ....###.....
        ....###.....
    """.toTemplate()

    val FISH = """
        ....####....
        ..########..
        .##########.
        ############
        ##.########.
        ############
        .##########.
        ..########..
        ....####....
    """.toTemplate()

    /**
     * Map of LevelShape to their bitmap templates.
     */
    fun getTemplate(shape: LevelShape): List<String> = when (shape) {
        LevelShape.SQUARE -> SQUARE
        LevelShape.CIRCLE -> CIRCLE
        LevelShape.TRIANGLE -> TRIANGLE
        LevelShape.DIAMOND -> DIAMOND
        LevelShape.HEART -> HEART
        LevelShape.STAR -> STAR
        LevelShape.CROSS -> CROSS
        LevelShape.HEXAGON -> HEXAGON
        LevelShape.PYRAMID -> PYRAMID
        LevelShape.ARROW_UP -> ARROW_UP
        LevelShape.HOURGLASS -> HOURGLASS
        LevelShape.OVAL -> OVAL
        LevelShape.CRESCENT -> CRESCENT
        LevelShape.LETTER_X -> LETTER_X
        LevelShape.CROWN -> CROWN
        LevelShape.RING -> RING
        LevelShape.LIGHTNING -> LIGHTNING
        LevelShape.SPIRAL -> SPIRAL
        LevelShape.BUTTERFLY -> BUTTERFLY
        // Illustrated animals have no hand-made bitmap; their masks come from
        // the vector art via DesignMask (LevelShape falls back to contains()).
        LevelShape.OWL, LevelShape.CAT, LevelShape.CRAB,
        LevelShape.DEER, LevelShape.PANDA -> emptyList()
    }

    /**
     * Get valid cells for a shape at the given grid size using bitmap scaling.
     */
    fun getValidCells(shape: LevelShape, rows: Int, cols: Int): List<Pair<Int, Int>> {
        val template = getTemplate(shape)
        if (template.isEmpty()) return emptyList()
        return scaleTemplate(template, rows, cols).toList()
    }

    private fun String.toTemplate(): List<String> =
        trimIndent().lines().filter { it.isNotBlank() }
}
