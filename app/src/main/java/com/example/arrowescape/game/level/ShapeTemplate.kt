package com.example.arrowescape.game.level

/**
 * Bitmap-based shape templates for pixel-perfect shape rendering.
 * 
 * Each template is a list of strings where:
 * - '#' = filled cell (arrow can be placed)
 * - '.' = empty cell
 * 
 * Templates are automatically scaled to fit any grid size while
 * preserving the shape's aspect ratio and details.
 */
object ShapeTemplate {

    /**
     * Scales a bitmap template to fit the target grid size.
     * Returns a set of (row, col) pairs that are "inside" the shape.
     */
    fun scaleTemplate(template: List<String>, targetRows: Int, targetCols: Int): Set<Pair<Int, Int>> {
        if (template.isEmpty()) return emptySet()
        
        val srcRows = template.size
        val srcCols = template.maxOf { it.length }
        
        val result = mutableSetOf<Pair<Int, Int>>()
        
        for (targetRow in 0 until targetRows) {
            for (targetCol in 0 until targetCols) {
                // Map target cell back to source template
                val srcRow = (targetRow * srcRows) / targetRows
                val srcCol = (targetCol * srcCols) / targetCols
                
                val line = template.getOrNull(srcRow) ?: ""
                val char = line.getOrNull(srcCol) ?: '.'
                
                if (char == '#') {
                    result.add(targetRow to targetCol)
                }
            }
        }
        
        return result
    }

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
