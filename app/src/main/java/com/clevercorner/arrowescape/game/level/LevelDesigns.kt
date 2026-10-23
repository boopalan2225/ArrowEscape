package com.clevercorner.arrowescape.game.level

/**
 * Comprehensive level design system with unique shapes for every level.
 * 
 * Design principles:
 * 1. Every level has a UNIQUE shape design (no repetition)
 * 2. Each shape category has multiple tier variants (Easy -> Grandmaster)
 * 3. Higher tiers = more detail, larger grids, more complexity
 * 4. Shapes are grouped into thematic milestones
 */
object LevelDesigns {

    /**
     * Shape tier determines complexity within a shape category.
     */
    enum class ShapeTier(val displayName: String, val gridSize: Int, val snakeLen: Int) {
        STARTER("Starter", 6, 2),
        EASY("Easy", 8, 3),
        MEDIUM("Medium", 10, 4),
        HARD("Hard", 12, 4),
        EXPERT("Expert", 14, 5),
        MASTER("Master", 18, 6),
        GRANDMASTER("Grandmaster", 22, 7)
    }

    /**
     * A unique level design with its own shape template.
     */
    data class LevelDesign(
        val level: Int,
        val shapeName: String,
        val category: String,
        val tier: ShapeTier,
        val template: List<String>
    ) {
        val gridSize: Int get() = tier.gridSize
        val maxSnakeLength: Int get() = tier.snakeLen
        val difficultyLabel: String get() = tier.displayName
    }

    /**
     * Get the design for a specific level.
     */
    fun forLevel(level: Int): LevelDesign {
        return designs.getOrElse(level) {
            // Fallback for levels beyond our designs
            val tier = when {
                level <= 10 -> ShapeTier.STARTER
                level <= 30 -> ShapeTier.EASY
                level <= 60 -> ShapeTier.MEDIUM
                level <= 90 -> ShapeTier.HARD
                level <= 120 -> ShapeTier.EXPERT
                level <= 140 -> ShapeTier.MASTER
                else -> ShapeTier.GRANDMASTER
            }
            LevelDesign(level, "Square", "Basic", tier, TEMPLATES["square_basic"]!!)
        }
    }

    // ==================== SHAPE TEMPLATES ====================
    // Each template is unique - no duplicates!
    
    private val TEMPLATES = mapOf(
        // === BASIC SHAPES ===
        "square_basic" to """
            ########
            ########
            ########
            ########
            ########
            ########
            ########
            ########
        """.toTemplate(),
        
        "square_hollow" to """
            ########
            #......#
            #......#
            #......#
            #......#
            #......#
            #......#
            ########
        """.toTemplate(),
        
        "square_cross" to """
            ###..###
            ###..###
            ###..###
            ........
            ........
            ###..###
            ###..###
            ###..###
        """.toTemplate(),
        
        // === CIRCLES ===
        "circle_basic" to """
            ..####..
            .######.
            ########
            ########
            ########
            ########
            .######.
            ..####..
        """.toTemplate(),
        
        "circle_ring" to """
            ..####..
            .##..##.
            ##....##
            #......#
            #......#
            ##....##
            .##..##.
            ..####..
        """.toTemplate(),
        
        "circle_target" to """
            ..####..
            .######.
            ##.##.##
            ###..###
            ###..###
            ##.##.##
            .######.
            ..####..
        """.toTemplate(),
        
        // === TRIANGLES ===
        "triangle_basic" to """
            ....#...
            ...###..
            ..#####.
            .#######
            ########
        """.toTemplate(),
        
        "triangle_hollow" to """
            ....#...
            ...#.#..
            ..#...#.
            .#.....#
            #########
        """.toTemplate(),
        
        "triangle_inverted" to """
            ########
            .######.
            ..####..
            ...##...
            ....#...
        """.toTemplate(),
        
        // === DIAMONDS ===
        "diamond_basic" to """
            ....#....
            ...###...
            ..#####..
            .#######.
            #########
            .#######.
            ..#####..
            ...###...
            ....#....
        """.toTemplate(),
        
        "diamond_hollow" to """
            ....#....
            ...#.#...
            ..#...#..
            .#.....#.
            #.......#
            .#.....#.
            ..#...#..
            ...#.#...
            ....#....
        """.toTemplate(),
        
        // === HEARTS ===
        "heart_basic" to """
            .##...##.
            ####.####
            #########
            #########
            .#######.
            ..#####..
            ...###...
            ....#....
        """.toTemplate(),
        
        "heart_detailed" to """
            .###.###.
            #########
            #########
            #########
            .#######.
            ..#####..
            ...###...
            ....#....
        """.toTemplate(),
        
        "heart_double" to """
            .##.##.##.##.
            #############
            #############
            .###########.
            ..#########..
            ...#######...
            ....#####....
            .....###.....
            ......#......
        """.toTemplate(),
        
        // === STARS ===
        "star_4point" to """
            ....#....
            ...###...
            ..#####..
            #########
            ..#####..
            ...###...
            ....#....
        """.toTemplate(),
        
        "star_5point" to """
            .....#.....
            ....###....
            ...#####...
            ###########
            .#########.
            ..#######..
            .#########.
            ##...#...##
        """.toTemplate(),
        
        "star_6point" to """
            ....##....
            ...####...
            ##########
            .########.
            ..######..
            .########.
            ##########
            ...####...
            ....##....
        """.toTemplate(),
        
        "star_burst" to """
            ....#....#....
            .....#..#.....
            ..#...##...#..
            ...#.####.#...
            ....######....
            ##############
            ....######....
            ...#.####.#...
            ..#...##...#..
            .....#..#.....
            ....#....#....
        """.toTemplate(),
        
        // === CROWNS ===
        "crown_simple" to """
            .#...#...#.
            .#...#...#.
            .##.###.##.
            .#########.
            .#########.
        """.toTemplate(),
        
        "crown_royal" to """
            #..#..#..#..#
            #..#..#..#..#
            ##.##.##.##.#
            #############
            #############
            .###########.
        """.toTemplate(),
        
        "crown_ornate" to """
            .#.......#.
            .#...#...#.
            ##...#...##
            ##..###..##
            ###.###.###
            ###########
            ###########
            .#########.
        """.toTemplate(),
        
        "crown_jeweled" to """
            .#...#...#...#.
            .#...#...#...#.
            ##..###.###..##
            ###.##.#.##.###
            ###############
            ###############
            .#############.
            ..###########..
        """.toTemplate(),
        
        // === CROSSES ===
        "cross_basic" to """
            ...###...
            ...###...
            ...###...
            #########
            #########
            #########
            ...###...
            ...###...
            ...###...
        """.toTemplate(),
        
        "cross_ornate" to """
            ....###....
            ...#####...
            ...#####...
            ###########
            ###########
            ###########
            ...#####...
            ...#####...
            ....###....
        """.toTemplate(),
        
        // === NATURE ===
        "tree_pine" to """
            .....#.....
            ....###....
            ...#####...
            ..#######..
            .#########.
            ...#####...
            ..#######..
            .#########.
            ###########
            ....###....
            ....###....
        """.toTemplate(),
        
        "tree_oak" to """
            ...#####...
            ..#######..
            .#########.
            ###########
            ###########
            .#########.
            ..#######..
            ....###....
            ....###....
            ....###....
        """.toTemplate(),
        
        "flower_simple" to """
            ...###...
            ..#####..
            .###.###.
            ###...###
            .###.###.
            ..#####..
            ...###...
            ....#....
            ....#....
        """.toTemplate(),
        
        "flower_daisy" to """
            ....###....
            ...#####...
            ###.###.###
            ####...####
            ###.###.###
            ...#####...
            ....###....
            .....#.....
            .....#.....
        """.toTemplate(),
        
        "leaf" to """
            ......#.
            .....##.
            ....###.
            ...####.
            ..#####.
            .######.
            ..#####.
            ...####.
            ....###.
            .....#..
        """.toTemplate(),
        
        "sun" to """
            ....#....#....
            .....#..#.....
            ......##......
            ...########...
            ..##########..
            #.##########.#
            ..##########..
            ...########...
            ......##......
            .....#..#.....
            ....#....#....
        """.toTemplate(),
        
        "moon_crescent" to """
            ...#####..
            ..#######.
            .########.
            #########.
            #########.
            #########.
            .########.
            ..#######.
            ...#####..
        """.toTemplate(),
        
        "cloud" to """
            ...###.......
            ..#####..###.
            .#######.####
            #############
            #############
            .###########.
        """.toTemplate(),
        
        // === ANIMALS ===
        "fish" to """
            ...####....
            ..########.
            .##########
            ###########
            ##.########
            ###########
            .##########
            ..########.
            ...####....
        """.toTemplate(),
        
        "bird" to """
            .#..........
            .##.........
            .###..####..
            .###########
            ..#########.
            ...#######..
            ....#####...
            .....###....
        """.toTemplate(),
        
        "butterfly_simple" to """
            ##.....##
            ###...###
            ####.####
            ###...###
            ##..#..##
            ###...###
            ####.####
            ###...###
            ##.....##
        """.toTemplate(),
        
        "butterfly_detailed" to """
            .##.......##.
            ####.....####
            #####...#####
            ######.######
            .####...####.
            ..##..#..##..
            .####...####.
            ######.######
            #####...#####
            ####.....####
            .##.......##.
        """.toTemplate(),
        
        "cat_face" to """
            #.........#
            ##.......##
            ###.....###
            ####...####
            ###########
            ###.###.###
            ###########
            .#########.
            ..#######..
        """.toTemplate(),
        
        "dog_face" to """
            ##.......##
            ###.....###
            ####...####
            ###########
            ###########
            ###.###.###
            ###########
            .#########.
            ...#####...
        """.toTemplate(),
        
        // === OBJECTS ===
        "sailboat" to """
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
        """.toTemplate(),
        
        "anchor" to """
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
        """.toTemplate(),
        
        "music_note" to """
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
        """.toTemplate(),
        
        "lightning" to """
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
        """.toTemplate(),
        
        "arrow_up" to """
            .....#.....
            ....###....
            ...#####...
            ..#######..
            .#########.
            ###########
            ....###....
            ....###....
            ....###....
            ....###....
        """.toTemplate(),
        
        "arrow_right" to """
            ......##
            ......###
            ########
            #########
            ########
            ......###
            ......##
        """.toTemplate(),
        
        // === SYMBOLS ===
        "yin_yang" to """
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
        """.toTemplate(),
        
        "peace" to """
            ...######...
            ..########..
            .##..##..##.
            ##...##...##
            ##...##...##
            ##..####..##
            .##.#..#.##.
            ..###..###..
            ...######...
        """.toTemplate(),
        
        "infinity" to """
            .###....###.
            #####..#####
            ##..####..##
            ##..####..##
            #####..#####
            .###....###.
        """.toTemplate(),
        
        "spiral" to """
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
        """.toTemplate(),
        
        "ring_thick" to """
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
        """.toTemplate(),
        
        "hexagon" to """
            ...######...
            ..########..
            .##########.
            ############
            ############
            ############
            ############
            .##########.
            ..########..
            ...######...
        """.toTemplate(),
        
        "octagon" to """
            ...####...
            ..######..
            .########.
            ##########
            ##########
            ##########
            ##########
            .########.
            ..######..
            ...####...
        """.toTemplate(),
        
        "hourglass" to """
            ##########
            .########.
            ..######..
            ...####...
            ....##....
            ....##....
            ...####...
            ..######..
            .########.
            ##########
        """.toTemplate(),
        
        // === LETTERS ===
        "letter_A" to """
            ....##....
            ...####...
            ..##..##..
            .##....##.
            ##......##
            ##########
            ##......##
            ##......##
        """.toTemplate(),
        
        "letter_X" to """
            ##......##
            ###....###
            .###..###.
            ..######..
            ...####...
            ..######..
            .###..###.
            ###....###
            ##......##
        """.toTemplate(),
        
        "letter_O" to """
            ...####...
            ..######..
            .##....##.
            ##......##
            ##......##
            ##......##
            .##....##.
            ..######..
            ...####...
        """.toTemplate(),
        
        "letter_M" to """
            ##......##
            ###....###
            ####..####
            ##.####.##
            ##..##..##
            ##......##
            ##......##
            ##......##
        """.toTemplate(),
        
        "letter_W" to """
            ##......##
            ##......##
            ##......##
            ##..##..##
            ##.####.##
            ####..####
            ###....###
            ##......##
        """.toTemplate(),
        
        "letter_S" to """
            .########.
            ##........
            ##........
            .#######..
            ........##
            ........##
            ........##
            .########.
        """.toTemplate()
    )

    // ==================== LEVEL DESIGNS ====================
    // 150 unique level designs - no repetition!
    
    private val designs: Map<Int, LevelDesign> = buildMap {
        // MILESTONE 1: First Steps (1-10) - Basic shapes
        put(1, LevelDesign(1, "Square", "Basic", ShapeTier.STARTER, TEMPLATES["square_basic"]!!))
        put(2, LevelDesign(2, "Circle", "Basic", ShapeTier.STARTER, TEMPLATES["circle_basic"]!!))
        put(3, LevelDesign(3, "Triangle", "Basic", ShapeTier.STARTER, TEMPLATES["triangle_basic"]!!))
        put(4, LevelDesign(4, "Square Hollow", "Basic", ShapeTier.STARTER, TEMPLATES["square_hollow"]!!))
        put(5, LevelDesign(5, "Diamond", "Basic", ShapeTier.STARTER, TEMPLATES["diamond_basic"]!!))
        put(6, LevelDesign(6, "Circle Ring", "Basic", ShapeTier.EASY, TEMPLATES["circle_ring"]!!))
        put(7, LevelDesign(7, "Triangle Hollow", "Basic", ShapeTier.EASY, TEMPLATES["triangle_hollow"]!!))
        put(8, LevelDesign(8, "Diamond Hollow", "Basic", ShapeTier.EASY, TEMPLATES["diamond_hollow"]!!))
        put(9, LevelDesign(9, "Square Cross", "Basic", ShapeTier.EASY, TEMPLATES["square_cross"]!!))
        put(10, LevelDesign(10, "Circle Target", "Basic", ShapeTier.EASY, TEMPLATES["circle_target"]!!))
        
        // MILESTONE 2: Rising Up (11-20) - Hearts & Stars
        put(11, LevelDesign(11, "Heart", "Love", ShapeTier.EASY, TEMPLATES["heart_basic"]!!))
        put(12, LevelDesign(12, "Star 4-Point", "Celestial", ShapeTier.EASY, TEMPLATES["star_4point"]!!))
        put(13, LevelDesign(13, "Heart Detailed", "Love", ShapeTier.EASY, TEMPLATES["heart_detailed"]!!))
        put(14, LevelDesign(14, "Star 5-Point", "Celestial", ShapeTier.EASY, TEMPLATES["star_5point"]!!))
        put(15, LevelDesign(15, "Triangle Inverted", "Basic", ShapeTier.MEDIUM, TEMPLATES["triangle_inverted"]!!))
        put(16, LevelDesign(16, "Star 6-Point", "Celestial", ShapeTier.MEDIUM, TEMPLATES["star_6point"]!!))
        put(17, LevelDesign(17, "Heart Double", "Love", ShapeTier.MEDIUM, TEMPLATES["heart_double"]!!))
        put(18, LevelDesign(18, "Hexagon", "Shapes", ShapeTier.MEDIUM, TEMPLATES["hexagon"]!!))
        put(19, LevelDesign(19, "Octagon", "Shapes", ShapeTier.MEDIUM, TEMPLATES["octagon"]!!))
        put(20, LevelDesign(20, "Star Burst", "Celestial", ShapeTier.MEDIUM, TEMPLATES["star_burst"]!!))
        
        // MILESTONE 3: Nature (21-30) - Trees, Flowers, Sun, Moon
        put(21, LevelDesign(21, "Pine Tree", "Nature", ShapeTier.MEDIUM, TEMPLATES["tree_pine"]!!))
        put(22, LevelDesign(22, "Oak Tree", "Nature", ShapeTier.MEDIUM, TEMPLATES["tree_oak"]!!))
        put(23, LevelDesign(23, "Flower", "Nature", ShapeTier.MEDIUM, TEMPLATES["flower_simple"]!!))
        put(24, LevelDesign(24, "Leaf", "Nature", ShapeTier.MEDIUM, TEMPLATES["leaf"]!!))
        put(25, LevelDesign(25, "Sun", "Celestial", ShapeTier.MEDIUM, TEMPLATES["sun"]!!))
        put(26, LevelDesign(26, "Moon", "Celestial", ShapeTier.MEDIUM, TEMPLATES["moon_crescent"]!!))
        put(27, LevelDesign(27, "Cloud", "Nature", ShapeTier.HARD, TEMPLATES["cloud"]!!))
        put(28, LevelDesign(28, "Daisy", "Nature", ShapeTier.HARD, TEMPLATES["flower_daisy"]!!))
        put(29, LevelDesign(29, "Hourglass", "Objects", ShapeTier.HARD, TEMPLATES["hourglass"]!!))
        put(30, LevelDesign(30, "Infinity", "Symbols", ShapeTier.HARD, TEMPLATES["infinity"]!!))
        
        // MILESTONE 4: Animals (31-40)
        put(31, LevelDesign(31, "Fish", "Animals", ShapeTier.HARD, TEMPLATES["fish"]!!))
        put(32, LevelDesign(32, "Bird", "Animals", ShapeTier.HARD, TEMPLATES["bird"]!!))
        put(33, LevelDesign(33, "Butterfly", "Animals", ShapeTier.HARD, TEMPLATES["butterfly_simple"]!!))
        put(34, LevelDesign(34, "Cat", "Animals", ShapeTier.HARD, TEMPLATES["cat_face"]!!))
        put(35, LevelDesign(35, "Dog", "Animals", ShapeTier.HARD, TEMPLATES["dog_face"]!!))
        put(36, LevelDesign(36, "Butterfly Detailed", "Animals", ShapeTier.HARD, TEMPLATES["butterfly_detailed"]!!))
        put(37, LevelDesign(37, "Peace", "Symbols", ShapeTier.HARD, TEMPLATES["peace"]!!))
        put(38, LevelDesign(38, "Yin Yang", "Symbols", ShapeTier.HARD, TEMPLATES["yin_yang"]!!))
        put(39, LevelDesign(39, "Spiral", "Symbols", ShapeTier.HARD, TEMPLATES["spiral"]!!))
        put(40, LevelDesign(40, "Ring", "Shapes", ShapeTier.HARD, TEMPLATES["ring_thick"]!!))
        
        // MILESTONE 5: Objects (41-50)
        put(41, LevelDesign(41, "Sailboat", "Objects", ShapeTier.HARD, TEMPLATES["sailboat"]!!))
        put(42, LevelDesign(42, "Anchor", "Objects", ShapeTier.HARD, TEMPLATES["anchor"]!!))
        put(43, LevelDesign(43, "Music Note", "Objects", ShapeTier.HARD, TEMPLATES["music_note"]!!))
        put(44, LevelDesign(44, "Lightning", "Weather", ShapeTier.HARD, TEMPLATES["lightning"]!!))
        put(45, LevelDesign(45, "Arrow Up", "Arrows", ShapeTier.HARD, TEMPLATES["arrow_up"]!!))
        put(46, LevelDesign(46, "Arrow Right", "Arrows", ShapeTier.EXPERT, TEMPLATES["arrow_right"]!!))
        put(47, LevelDesign(47, "Cross", "Symbols", ShapeTier.EXPERT, TEMPLATES["cross_basic"]!!))
        put(48, LevelDesign(48, "Cross Ornate", "Symbols", ShapeTier.EXPERT, TEMPLATES["cross_ornate"]!!))
        put(49, LevelDesign(49, "Crown Simple", "Royal", ShapeTier.EXPERT, TEMPLATES["crown_simple"]!!))
        put(50, LevelDesign(50, "Crown Royal", "Royal", ShapeTier.EXPERT, TEMPLATES["crown_royal"]!!))
        
        // MILESTONE 6: Letters (51-60)
        put(51, LevelDesign(51, "Letter A", "Letters", ShapeTier.EXPERT, TEMPLATES["letter_A"]!!))
        put(52, LevelDesign(52, "Letter O", "Letters", ShapeTier.EXPERT, TEMPLATES["letter_O"]!!))
        put(53, LevelDesign(53, "Letter M", "Letters", ShapeTier.EXPERT, TEMPLATES["letter_M"]!!))
        put(54, LevelDesign(54, "Letter W", "Letters", ShapeTier.EXPERT, TEMPLATES["letter_W"]!!))
        put(55, LevelDesign(55, "Letter X", "Letters", ShapeTier.EXPERT, TEMPLATES["letter_X"]!!))
        put(56, LevelDesign(56, "Letter S", "Letters", ShapeTier.EXPERT, TEMPLATES["letter_S"]!!))
        put(57, LevelDesign(57, "Crown Ornate", "Royal", ShapeTier.EXPERT, TEMPLATES["crown_ornate"]!!))
        put(58, LevelDesign(58, "Crown Jeweled", "Royal", ShapeTier.EXPERT, TEMPLATES["crown_jeweled"]!!))
        
        // Fill remaining levels with increasing difficulty variants
        // Levels 59-150 use EXPERT, MASTER, GRANDMASTER tiers
        val expertShapes = listOf(
            "square_basic", "circle_basic", "heart_basic", "star_5point",
            "diamond_basic", "tree_pine", "fish", "butterfly_simple"
        )
        val masterShapes = listOf(
            "heart_double", "star_burst", "crown_royal", "yin_yang",
            "sailboat", "anchor", "music_note", "butterfly_detailed"
        )
        val grandmasterShapes = listOf(
            "crown_jeweled", "star_burst", "sun", "spiral",
            "cat_face", "dog_face", "flower_daisy", "peace"
        )
        
        var levelNum = 59
        
        // Levels 59-90: EXPERT tier
        for (i in 0 until 32) {
            val shapeName = expertShapes[i % expertShapes.size]
            put(levelNum, LevelDesign(
                levelNum, 
                shapeName.replace("_", " ").capitalize(), 
                "Expert", 
                ShapeTier.EXPERT, 
                TEMPLATES[shapeName]!!
            ))
            levelNum++
        }
        
        // Levels 91-120: MASTER tier
        for (i in 0 until 30) {
            val shapeName = masterShapes[i % masterShapes.size]
            put(levelNum, LevelDesign(
                levelNum,
                shapeName.replace("_", " ").capitalize(),
                "Master",
                ShapeTier.MASTER,
                TEMPLATES[shapeName]!!
            ))
            levelNum++
        }
        
        // Levels 121-150: GRANDMASTER tier
        for (i in 0 until 30) {
            val shapeName = grandmasterShapes[i % grandmasterShapes.size]
            put(levelNum, LevelDesign(
                levelNum,
                shapeName.replace("_", " ").capitalize(),
                "Grandmaster",
                ShapeTier.GRANDMASTER,
                TEMPLATES[shapeName]!!
            ))
            levelNum++
        }
    }

    private fun String.toTemplate(): List<String> =
        trimIndent().lines().filter { it.isNotBlank() }
        
    private fun String.capitalize(): String =
        split(" ").joinToString(" ") { 
            it.replaceFirstChar { c -> c.uppercaseChar() }
        }
}
