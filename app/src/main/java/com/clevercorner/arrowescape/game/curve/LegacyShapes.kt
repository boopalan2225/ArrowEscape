package com.clevercorner.arrowescape.game.curve

/**
 * The original curved-mode shapes.
 *
 * They are single-region designs, each painted in the one colour the thing
 * naturally is (a heart is red, a frog is green). They still benefit from every
 * generator upgrade (dense packing, contour flow, curl, snake-style escape)
 * because those live in the engine, not in the shape.
 *
 * Order matters: level N picks catalogue entry `N % size`, so reordering would
 * change which shape players see on levels they already know.
 */
object LegacyShapes {

    private fun legacy(
        name: String,
        color: Long,
        build: (width: Float, height: Float) -> ShapePath
    ) = ShapeDesign(
        name = name,
        regions = listOf(ShapeRegion(name, color, builder = build))
    )

    val ALL: List<ShapeDesign> = listOf(
        legacy("Circle", Hues.BLUE) { w, h -> ShapePath.circle(w / 2, h / 2, minOf(w, h) * 0.4f) },
        legacy("Heart", Hues.RED) { w, h -> ShapePath.heart(w * 0.8f, h * 0.8f) },
        legacy("Star", Hues.GOLD) { w, h ->
            ShapePath.star(w / 2, h / 2, minOf(w, h) * 0.4f, minOf(w, h) * 0.2f, 5)
        },
        legacy("Kitten", Hues.ORANGE) { w, h -> ShapePath.cat(w * 0.8f, h * 0.9f) },
        legacy("Barn Owl", Hues.BROWN) { w, h -> ShapePath.owl(w * 0.7f, h * 0.9f) },
        legacy("Fish", Hues.ORANGE) { w, h -> ShapePath.fish(w * 0.9f, h * 0.6f) },
        legacy("Butterfly", Hues.PURPLE) { w, h -> ShapePath.butterfly(w * 0.85f, h * 0.85f) },
        legacy("Frog", Hues.GREEN) { w, h -> ShapePath.frog(w * 0.85f, h * 0.8f) },
        legacy("Fawn", Hues.LIGHT_BROWN) { w, h -> ShapePath.deer(w * 0.9f, h * 0.9f) },
        legacy("Snail", Hues.BROWN) { w, h -> ShapePath.snail(w * 0.9f, h * 0.7f) },
        legacy("Moon", Hues.GOLD) { w, h -> ShapePath.moon(w * 0.7f, h * 0.85f) },
        legacy("Leaf", Hues.GREEN) { w, h -> ShapePath.leaf(w * 0.7f, h * 0.9f) },
        legacy("Star6", Hues.SKY) { w, h ->
            ShapePath.star(w / 2, h / 2, minOf(w, h) * 0.45f, minOf(w, h) * 0.18f, 6)
        },
        legacy("Star8", Hues.ORANGE) { w, h ->
            ShapePath.star(w / 2, h / 2, minOf(w, h) * 0.42f, minOf(w, h) * 0.22f, 8)
        }
    )
}
