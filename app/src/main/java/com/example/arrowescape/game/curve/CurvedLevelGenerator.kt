package com.example.arrowescape.game.curve

import kotlin.random.Random

/**
 * Game engine for curved arrow puzzle levels.
 */
object CurvedGameEngine {

    /**
     * Check if an arrow can escape (has clear path to edge).
     */
    fun canEscape(
        arrow: CurvedArrow,
        allArrows: List<CurvedArrow>,
        shapeWidth: Float,
        shapeHeight: Float
    ): Boolean {
        if (!arrow.isActive) return false
        
        val tip = arrow.endPoint
        val direction = arrow.direction
        
        // Check if path from tip to edge is clear
        val stepSize = 5f
        val maxSteps = ((shapeWidth + shapeHeight) / stepSize).toInt()
        
        var testPoint = tip
        for (step in 0 until maxSteps) {
            testPoint = testPoint + direction * stepSize
            
            // Check if we've exited the board
            if (testPoint.x < -20 || testPoint.x > shapeWidth + 20 ||
                testPoint.y < -20 || testPoint.y > shapeHeight + 20) {
                return true // Successfully escaped
            }
            
            // Check collision with other active arrows
            for (other in allArrows) {
                if (other.id == arrow.id || !other.isActive) continue
                if (other.isNear(testPoint, arrow.strokeWidth + other.strokeWidth)) {
                    return false // Blocked by another arrow
                }
            }
        }
        
        return false
    }

    /**
     * Find an arrow that can currently escape (for hints).
     */
    fun findEscapableArrow(
        arrows: List<CurvedArrow>,
        shapeWidth: Float,
        shapeHeight: Float
    ): CurvedArrow? {
        return arrows.filter { it.isActive }
            .shuffled()
            .firstOrNull { canEscape(it, arrows, shapeWidth, shapeHeight) }
    }

    /**
     * Check if the level is solvable (at least one arrow can always escape).
     */
    fun isSolvable(
        arrows: List<CurvedArrow>,
        shapeWidth: Float,
        shapeHeight: Float
    ): Boolean {
        val remaining = arrows.toMutableList()
        
        while (remaining.any { it.isActive }) {
            val escapable = remaining.filter { it.isActive }
                .firstOrNull { canEscape(it, remaining, shapeWidth, shapeHeight) }
            
            if (escapable == null) {
                return false // Stuck - no arrow can escape
            }
            
            // Simulate escape
            val index = remaining.indexOfFirst { it.id == escapable.id }
            remaining[index] = escapable.withActive(false)
        }
        
        return true
    }
}

/**
 * Level data for curved arrow puzzles.
 */
data class CurvedLevel(
    val levelNumber: Int,
    val shapeName: String,
    val shape: ShapePath,
    val arrows: List<CurvedArrow>,
    val difficulty: String,
    val colorPalette: List<Long>
)

/**
 * Generates complete curved arrow puzzle levels.
 */
object CurvedLevelGenerator {

    // Color palettes for different themes
    private val PALETTES = mapOf(
        "ocean" to listOf(0xFF4A90D9, 0xFF5BA3E0, 0xFF6BB6E8, 0xFF3D7DC6),
        "sunset" to listOf(0xFFE67E22, 0xFFE74C3C, 0xFFF39C12, 0xFFD35400),
        "forest" to listOf(0xFF27AE60, 0xFF2ECC71, 0xFF1ABC9C, 0xFF16A085),
        "purple" to listOf(0xFF9B59B6, 0xFF8E44AD, 0xFFA569BD, 0xFF7D3C98),
        "rainbow" to listOf(0xFFE74C3C, 0xFFF39C12, 0xFF2ECC71, 0xFF3498DB, 0xFF9B59B6, 0xFFE91E8C),
        "mono" to listOf(0xFF4A90D9)
    )

    // Shape generators for different level ranges (expanded set)
    private val SHAPE_GENERATORS: List<Pair<String, (Float, Float) -> ShapePath>> = listOf(
        "Circle" to { w, h -> ShapePath.circle(w/2, h/2, minOf(w, h) * 0.4f) },
        "Heart" to { w, h -> ShapePath.heart(w * 0.8f, h * 0.8f) },
        "Star" to { w, h -> ShapePath.star(w/2, h/2, minOf(w, h) * 0.4f, minOf(w, h) * 0.2f, 5) },
        "Cat" to { w, h -> ShapePath.cat(w * 0.8f, h * 0.9f) },
        "Owl" to { w, h -> ShapePath.owl(w * 0.7f, h * 0.9f) },
        "Fish" to { w, h -> ShapePath.fish(w * 0.9f, h * 0.6f) },
        "Butterfly" to { w, h -> ShapePath.butterfly(w * 0.85f, h * 0.85f) },
        "Frog" to { w, h -> ShapePath.frog(w * 0.85f, h * 0.8f) },
        "Deer" to { w, h -> ShapePath.deer(w * 0.9f, h * 0.9f) },
        "Snail" to { w, h -> ShapePath.snail(w * 0.9f, h * 0.7f) },
        "Moon" to { w, h -> ShapePath.moon(w * 0.7f, h * 0.85f) },
        "Leaf" to { w, h -> ShapePath.leaf(w * 0.7f, h * 0.9f) },
        "Star6" to { w, h -> ShapePath.star(w/2, h/2, minOf(w, h) * 0.45f, minOf(w, h) * 0.18f, 6) },
        "Star8" to { w, h -> ShapePath.star(w/2, h/2, minOf(w, h) * 0.42f, minOf(w, h) * 0.22f, 8) }
    )

    /**
     * Generate a complete level.
     */
    fun generateLevel(levelNumber: Int, boardWidth: Float = 400f, boardHeight: Float = 600f): CurvedLevel {
        val random = Random(levelNumber.toLong() * 31337 + 42)
        
        // Determine difficulty and parameters based on level
        val (difficulty, config) = getDifficultyConfig(levelNumber, random)
        
        // Pick a shape based on level progression
        val shapeIndex = levelNumber % SHAPE_GENERATORS.size
        val (shapeName, shapeGenerator) = SHAPE_GENERATORS[shapeIndex]
        val baseShape = shapeGenerator(boardWidth, boardHeight)
        
        // Center the shape
        val offsetX = (boardWidth - baseShape.width) / 2
        val offsetY = (boardHeight - baseShape.height) / 2
        val shape = baseShape.scaleTo(baseShape.width, baseShape.height)
        
        // Generate arrows
        var arrows = CurveGenerator.generateArrows(shape, config, random)
        
        // Validate and filter to ensure solvability
        arrows = CurvedGameEngine.validateEscapePaths(arrows, shape)
        
        // Ensure we have enough arrows
        if (arrows.size < 5) {
            // Regenerate with different seed
            arrows = CurveGenerator.generateArrows(
                shape, 
                config.copy(arrowSpacing = config.arrowSpacing * 0.8f),
                Random(levelNumber.toLong() * 12345 + 67)
            )
            arrows = CurvedGameEngine.validateEscapePaths(arrows, shape)
        }
        
        return CurvedLevel(
            levelNumber = levelNumber,
            shapeName = shapeName,
            shape = shape,
            arrows = arrows,
            difficulty = difficulty,
            colorPalette = config.colorPalette
        )
    }

    /**
     * Get difficulty configuration based on level number.
     */
    private fun getDifficultyConfig(level: Int, random: Random): Pair<String, CurveGenerator.GenerationConfig> {
        val paletteName = when {
            level <= 20 -> "ocean"
            level <= 40 -> "forest"
            level <= 60 -> "sunset"
            level <= 80 -> "purple"
            else -> "rainbow"
        }
        
        val palette = PALETTES[paletteName] ?: PALETTES["mono"]!!
        
        return when {
            level <= 10 -> "Easy" to CurveGenerator.GenerationConfig(
                arrowMinLength = 40f,
                arrowMaxLength = 100f,
                arrowSpacing = 25f,
                strokeWidth = 8f,
                bendiness = 0.2f,
                flowType = CurveGenerator.FlowType.HORIZONTAL,
                colorPalette = palette.take(2)
            )
            level <= 30 -> "Normal" to CurveGenerator.GenerationConfig(
                arrowMinLength = 35f,
                arrowMaxLength = 120f,
                arrowSpacing = 20f,
                strokeWidth = 7f,
                bendiness = 0.3f,
                flowType = CurveGenerator.FlowType.ORGANIC,
                colorPalette = palette.take(3)
            )
            level <= 60 -> "Hard" to CurveGenerator.GenerationConfig(
                arrowMinLength = 30f,
                arrowMaxLength = 140f,
                arrowSpacing = 16f,
                strokeWidth = 6f,
                bendiness = 0.4f,
                flowType = if (random.nextBoolean()) CurveGenerator.FlowType.ORGANIC else CurveGenerator.FlowType.SPIRAL,
                colorPalette = palette.take(4)
            )
            level <= 100 -> "Expert" to CurveGenerator.GenerationConfig(
                arrowMinLength = 25f,
                arrowMaxLength = 160f,
                arrowSpacing = 14f,
                strokeWidth = 5f,
                bendiness = 0.5f,
                flowType = CurveGenerator.FlowType.ORGANIC,
                colorPalette = palette
            )
            else -> "Master" to CurveGenerator.GenerationConfig(
                arrowMinLength = 20f,
                arrowMaxLength = 180f,
                arrowSpacing = 12f,
                strokeWidth = 5f,
                bendiness = 0.6f,
                flowType = CurveGenerator.FlowType.ORGANIC,
                colorPalette = palette
            )
        }
    }
}
