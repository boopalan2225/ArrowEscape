package com.example.arrowescape.game.curve

/**
 * Clean, instantly recognisable silhouettes ported from the Flutter game's
 * boss / god shape pool.
 *
 * The Flutter game defines these as grid masks; here each is a handful of SVG
 * path commands in a unit square, which the existing region pipeline fills
 * with arrows. Compact paths can be read at a glance - the old hand-built
 * outlines (a "Cat" that looked like a vase) could not.
 *
 * Colour follows meaning, never rotation: a tree has a brown trunk and green
 * foliage, a house a red roof over tan walls. Things made of several parts are
 * several regions; plain geometric shapes get one signature colour.
 */
object PoolShapes {

    private fun silhouette(name: String, color: Long, path: String, aspect: Float = 1f) =
        ShapeDesign(
            name = name,
            aspect = aspect,
            regions = listOf(svgRegion(name = name, path = path, color = color))
        )

    private fun part(name: String, color: Long, path: String, layer: Int = 0) =
        svgRegion(name = name, path = path, color = color, layer = layer)

    // ── geometric ────────────────────────────────────────────────────────

    private val PLUS = silhouette(
        "Plus", Hues.GREEN,
        "M .35 0 H .65 V .35 H 1 V .65 H .65 V 1 H .35 V .65 H 0 V .35 H .35 Z"
    )

    private val DIAMOND = silhouette("Diamond", Hues.CYAN, "M .5 0 L 1 .5 L .5 1 L 0 .5 Z")

    // Honeycomb amber.
    private val HEXAGON = silhouette(
        "Hexagon", Hues.GOLD, "M .25 .06 L .75 .06 L 1 .5 L .75 .94 L .25 .94 L 0 .5 Z"
    )

    // Stop-sign red.
    private val OCTAGON = silhouette(
        "Octagon", Hues.CRIMSON, "M .3 0 L .7 0 L 1 .3 L 1 .7 L .7 1 L .3 1 L 0 .7 L 0 .3 Z"
    )

    private val PENTAGON = silhouette(
        "Pentagon", Hues.TEAL, "M .5 0 L 1 .38 L .81 1 L .19 1 L 0 .38 Z"
    )

    // A sparkle.
    private val STAR4 = silhouette(
        "Star4", Hues.GOLD,
        "M .5 0 L .62 .38 L 1 .5 L .62 .62 L .5 1 L .38 .62 L 0 .5 L .38 .38 Z"
    )

    // An X mark.
    private val X_CROSS = silhouette(
        "XCross", Hues.RED,
        "M .15 0 L .5 .35 L .85 0 L 1 .15 L .65 .5 L 1 .85 L .85 1 L .5 .65 " +
            "L .15 1 L 0 .85 L .35 .5 L 0 .15 Z"
    )

    private val ARROW_UP = silhouette(
        "ArrowUp", Hues.BLUE, "M .5 0 L 1 .5 L .7 .5 L .7 1 L .3 1 L .3 .5 L 0 .5 Z"
    )

    // Road-sign orange.
    private val CHEVRON = silhouette(
        "Chevron", Hues.ORANGE, "M 0 0 L .6 0 L 1 .5 L .6 1 L 0 1 L .4 .5 Z"
    )

    private val LIGHTNING = silhouette(
        "Lightning", Hues.GOLD,
        "M .6 0 L .15 .55 L .45 .55 L .35 1 L .85 .4 L .55 .4 Z",
        aspect = 0.7f
    )

    // ── decorative ───────────────────────────────────────────────────────

    private val HOUSE = ShapeDesign(
        name = "House",
        aspect = 1f,
        regions = listOf(
            part("walls", Hues.TAN, "M .15 .45 L .85 .45 L .85 1 L .15 1 Z"),
            part("roof", Hues.CRIMSON, "M .5 0 L 1 .45 L 0 .45 Z")
        )
    )

    private val CROWN = ShapeDesign(
        name = "Crown",
        aspect = 1f,
        regions = listOf(
            part("gold", Hues.GOLD, "M 0 .25 L .25 .52 L .5 0 L .75 .52 L 1 .25 L .9 1 L .1 1 Z"),
            part("band", Hues.CRIMSON, "M .08 .78 L .92 .78 L .9 1 L .1 1 Z", layer = 1)
        )
    )

    private val SHIELD = ShapeDesign(
        name = "Shield",
        aspect = 0.85f,
        regions = listOf(
            part(
                "field", Hues.BLUE,
                "M .05 .05 L .95 .05 C .95 .6 .8 .85 .5 1 C .2 .85 .05 .6 .05 .05 Z"
            ),
            part(
                "emblem", Hues.GOLD,
                "M .27 .2 L .73 .2 C .73 .52 .65 .68 .5 .78 C .35 .68 .27 .52 .27 .2 Z",
                layer = 1
            )
        )
    )

    private val GEM = ShapeDesign(
        name = "Gem",
        aspect = 1f,
        regions = listOf(
            part("pavilion", Hues.DEEP_CYAN, "M .25 0 L .75 0 L 1 .35 L .5 1 L 0 .35 Z"),
            part("table", 0xFF4DD0E1L, "M .25 0 L .75 0 L 1 .35 L 0 .35 Z", layer = 1)
        )
    )

    // Brown trunk under green foliage.
    private val TREE = ShapeDesign(
        name = "Tree",
        aspect = 0.8f,
        regions = listOf(
            part("trunk", Hues.BROWN, "M .42 .74 L .58 .74 L .58 1 L .42 1 Z"),
            part(
                "foliage", Hues.GREEN,
                "M .5 0 L .88 .38 L .68 .38 L 1 .74 L 0 .74 L .32 .38 L .12 .38 Z"
            )
        )
    )

    private val ROCKET = ShapeDesign(
        name = "Rocket",
        aspect = 0.6f,
        regions = listOf(
            part(
                "body", Hues.STEEL,
                "M .28 .3 L .72 .3 C .75 .5 .76 .66 .72 .78 L .28 .78 C .24 .66 .25 .5 .28 .3 Z"
            ),
            part(
                "nose", Hues.RED,
                "M .5 0 C .62 .08 .7 .2 .72 .3 L .28 .3 C .3 .2 .38 .08 .5 0 Z"
            ),
            part("fins", Hues.RED, "M .28 .6 L .1 .95 L .28 .82 Z M .72 .6 L .9 .95 L .72 .82 Z"),
            part("flame", Hues.ORANGE, "M .34 .78 L .66 .78 L .5 1 Z")
        )
    )

    private val CLOUD = silhouette(
        "Cloud", Hues.SKY,
        "M .2 .85 C -.02 .85 -.02 .5 .2 .5 C .2 .18 .55 .12 .62 .38 " +
            "C .82 .22 1.02 .45 .86 .62 C 1.04 .68 1.0 .85 .8 .85 Z",
        aspect = 1.4f
    )

    private val BOWTIE = silhouette(
        "Bowtie", Hues.PINK,
        "M 0 .1 L .4 .38 L .6 .38 L 1 .1 L 1 .9 L .6 .62 L .4 .62 L 0 .9 Z",
        aspect = 1.5f
    )

    val ALL: List<ShapeDesign> = listOf(
        PLUS, DIAMOND, HEXAGON, OCTAGON, PENTAGON, STAR4, X_CROSS, ARROW_UP, CHEVRON, LIGHTNING,
        HOUSE, CROWN, SHIELD, GEM, TREE, ROCKET, CLOUD, BOWTIE
    )
}
