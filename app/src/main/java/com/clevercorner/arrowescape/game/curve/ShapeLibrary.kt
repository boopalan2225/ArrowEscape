package com.clevercorner.arrowescape.game.curve

/**
 * One coloured area within a shape.
 *
 * Reference art is never a single flat silhouette: the owl has a tan belly and
 * yellow eyes, the panda sits on green grass, the cat has a cream chest. Those
 * regions are what make a shape *readable*, so they are modelled explicitly
 * rather than scattering random palette colours across arrows.
 *
 * A region only knows how to build its outline for a given size, so SVG art
 * and the older hand-built [ShapePath] shapes share one pipeline.
 */
class ShapeRegion(
    val name: String,
    /** The region's own colour - chosen for what it depicts, see [Hues]. */
    val color: Long,
    /** Drawn later = on top. Interior details need a higher layer. */
    val layer: Int = 0,
    /** Stroke scale relative to the level default. Eyes want finer lines. */
    val strokeScale: Float = 1f,
    /** How tightly arrows coil inside this region. */
    val curl: Float = 1f,
    /** Overrides the level's flow field for this region (e.g. rings for eyes). */
    val flow: CurveGenerator.FlowType? = null,
    private val builder: (width: Float, height: Float) -> ShapePath
) {
    fun build(width: Float, height: Float): ShapePath = builder(width, height)
}

/** Region authored as an SVG path in a 0..1 coordinate space. */
fun svgRegion(
    name: String,
    path: String,
    color: Long,
    layer: Int = 0,
    strokeScale: Float = 1f,
    curl: Float = 1f,
    flow: CurveGenerator.FlowType? = null
) = ShapeRegion(name, color, layer, strokeScale, curl, flow) { w, h ->
    ShapePath(name, SvgPath.parse(path, w, h), w, h, color)
}

/**
 * A complete illustration: an outer silhouette plus interior detail regions.
 */
class ShapeDesign(
    val name: String,
    val regions: List<ShapeRegion>,
    /**
     * Width / height the artwork was drawn at, or null to fill whatever area
     * the level offers (legacy shapes size themselves).
     */
    val aspect: Float? = null
) {
    /** Regions ordered back-to-front. */
    val ordered: List<ShapeRegion> get() = regions.sortedBy { it.layer }
}

/**
 * The shape library, authored as SVG paths in a unit square.
 *
 * Paths are deliberately written as a handful of smooth curves rather than
 * dozens of micro-segments: the generator fills them with arrows, so the
 * outline only needs to carry the silhouette's character.
 */
object ShapeLibrary {

    // Palettes lifted from the reference artwork.
    private const val OWL_BODY = 0xFFB5551F
    private const val OWL_BELLY = 0xFFF0B97A
    private const val OWL_EYE = 0xFFD2E34A

    private const val CAT_BODY = 0xFFE87A22
    private const val CAT_CHEST = 0xFFF5E2A8
    private const val CAT_EAR = 0xFFA463D6

    private const val PANDA_BODY = 0xFF2F7FEA
    private const val PANDA_GRASS = 0xFF7AC943

    private const val DEER_A = 0xFFF5A623
    private const val DEER_B = 0xFF7ED9C3
    private const val DEER_C = 0xFFE8509B
    private const val DEER_D = 0xFF7B5CE0

    /**
     * Owl: round head with ear tufts, broad body, splayed feet on a branch.
     */
    val OWL = ShapeDesign(
        name = "Owl",
        aspect = 0.62f,
        regions = listOf(
            svgRegion(
                name = "body",
                color = OWL_BODY,
                path = "M 0.50 0.07 " +
                    "C 0.42 0.05 0.32 0.04 0.24 0.07 L 0.13 0.00 " +
                    "C 0.08 0.10 0.06 0.20 0.08 0.30 " +
                    "C 0.10 0.38 0.12 0.44 0.10 0.50 " +
                    "C 0.04 0.60 0.03 0.72 0.08 0.82 " +
                    "C 0.12 0.88 0.20 0.91 0.24 0.92 " +
                    "L 0.20 0.99 L 0.34 0.99 L 0.38 0.94 " +
                    "C 0.46 0.96 0.54 0.96 0.62 0.94 " +
                    "L 0.66 0.99 L 0.80 0.99 L 0.76 0.92 " +
                    "C 0.80 0.91 0.88 0.88 0.92 0.82 " +
                    "C 0.97 0.72 0.96 0.60 0.90 0.50 " +
                    "C 0.88 0.44 0.90 0.38 0.92 0.30 " +
                    "C 0.94 0.20 0.92 0.10 0.87 0.00 L 0.76 0.07 " +
                    "C 0.68 0.04 0.58 0.05 0.50 0.07 Z"
            ),
            svgRegion(
                name = "belly",
                color = OWL_BELLY,
                layer = 1,
                curl = 1.4f,
                path = "M 0.50 0.40 " +
                    "C 0.34 0.40 0.22 0.50 0.20 0.64 " +
                    "C 0.18 0.78 0.26 0.88 0.38 0.92 " +
                    "C 0.44 0.94 0.50 0.94 0.50 0.94 " +
                    "C 0.50 0.94 0.56 0.94 0.62 0.92 " +
                    "C 0.74 0.88 0.82 0.78 0.80 0.64 " +
                    "C 0.78 0.50 0.66 0.40 0.50 0.40 Z"
            ),
            svgRegion(
                name = "eyeLeft",
                color = OWL_EYE,
                layer = 2,
                strokeScale = 0.8f,
                curl = 0f,
                flow = CurveGenerator.FlowType.RING,
                path = "M 0.34 0.20 C 0.26 0.20 0.22 0.25 0.22 0.30 " +
                    "C 0.22 0.35 0.26 0.40 0.34 0.40 " +
                    "C 0.42 0.40 0.46 0.35 0.46 0.30 " +
                    "C 0.46 0.25 0.42 0.20 0.34 0.20 Z"
            ),
            svgRegion(
                name = "eyeRight",
                color = OWL_EYE,
                layer = 2,
                strokeScale = 0.8f,
                curl = 0f,
                flow = CurveGenerator.FlowType.RING,
                path = "M 0.66 0.20 C 0.58 0.20 0.54 0.25 0.54 0.30 " +
                    "C 0.54 0.35 0.58 0.40 0.66 0.40 " +
                    "C 0.74 0.40 0.78 0.35 0.78 0.30 " +
                    "C 0.78 0.25 0.74 0.20 0.66 0.20 Z"
            )
        )
    )

    /**
     * Cat: seated profile with pointed ears, curved back and a sweeping tail.
     */
    val CAT = ShapeDesign(
        name = "Cat",
        aspect = 0.68f,
        regions = listOf(
            svgRegion(
                name = "body",
                color = CAT_BODY,
                path = "M 0.26 0.10 " +
                    "C 0.22 0.18 0.18 0.26 0.16 0.32 " +
                    "C 0.10 0.34 0.08 0.40 0.10 0.46 " +
                    "C 0.12 0.52 0.18 0.56 0.24 0.56 " +
                    "C 0.20 0.66 0.18 0.76 0.18 0.84 " +
                    "C 0.10 0.86 0.04 0.90 0.04 0.94 " +
                    "C 0.04 0.98 0.14 0.99 0.30 0.99 " +
                    "C 0.52 0.99 0.74 0.98 0.86 0.94 " +
                    "C 0.94 0.90 0.96 0.82 0.92 0.74 " +
                    "C 0.88 0.64 0.80 0.56 0.70 0.50 " +
                    "C 0.66 0.42 0.62 0.34 0.58 0.28 " +
                    "C 0.64 0.24 0.70 0.20 0.72 0.16 " +
                    "C 0.66 0.16 0.58 0.20 0.52 0.24 " +
                    "C 0.46 0.20 0.38 0.14 0.32 0.08 " +
                    "C 0.30 0.06 0.28 0.06 0.26 0.10 Z"
            ),
            svgRegion(
                name = "chest",
                color = CAT_CHEST,
                layer = 1,
                curl = 1.5f,
                path = "M 0.34 0.46 " +
                    "C 0.26 0.52 0.22 0.62 0.24 0.72 " +
                    "C 0.26 0.82 0.32 0.88 0.40 0.90 " +
                    "C 0.48 0.88 0.54 0.80 0.54 0.70 " +
                    "C 0.54 0.60 0.46 0.50 0.34 0.46 Z"
            ),
            svgRegion(
                name = "earInner",
                color = CAT_EAR,
                layer = 2,
                strokeScale = 0.9f,
                path = "M 0.28 0.26 C 0.24 0.30 0.24 0.34 0.28 0.36 " +
                    "C 0.34 0.36 0.38 0.32 0.36 0.28 " +
                    "C 0.34 0.25 0.30 0.24 0.28 0.26 Z"
            )
        )
    )

    /**
     * Panda: seated bear with round ears, sitting on a band of grass.
     */
    val PANDA = ShapeDesign(
        name = "Panda",
        aspect = 0.80f,
        regions = listOf(
            svgRegion(
                name = "grass",
                color = PANDA_GRASS,
                layer = 0,
                curl = 1.8f,
                path = "M 0.02 0.70 " +
                    "C 0.14 0.64 0.30 0.66 0.42 0.70 " +
                    "C 0.56 0.66 0.76 0.64 0.96 0.70 " +
                    "C 0.99 0.78 0.98 0.90 0.92 0.97 " +
                    "C 0.70 0.99 0.30 0.99 0.08 0.97 " +
                    "C 0.02 0.90 0.00 0.78 0.02 0.70 Z"
            ),
            svgRegion(
                name = "body",
                color = PANDA_BODY,
                layer = 1,
                path = "M 0.34 0.06 " +
                    "C 0.26 0.06 0.22 0.11 0.24 0.16 " +
                    "C 0.28 0.20 0.34 0.20 0.38 0.17 " +
                    "C 0.46 0.14 0.60 0.14 0.68 0.17 " +
                    "C 0.72 0.20 0.78 0.20 0.82 0.16 " +
                    "C 0.84 0.11 0.80 0.06 0.72 0.06 " +
                    "C 0.66 0.06 0.62 0.09 0.60 0.12 " +
                    "C 0.54 0.10 0.50 0.10 0.46 0.12 " +
                    "C 0.44 0.09 0.40 0.06 0.34 0.06 Z " +
                    "M 0.50 0.14 " +
                    "C 0.30 0.14 0.16 0.26 0.14 0.44 " +
                    "C 0.12 0.58 0.18 0.70 0.28 0.78 " +
                    "C 0.40 0.86 0.60 0.86 0.72 0.78 " +
                    "C 0.82 0.70 0.88 0.58 0.86 0.44 " +
                    "C 0.84 0.26 0.70 0.14 0.50 0.14 Z"
            )
        )
    )

    /**
     * Deer: antlered head, drawn as one silhouette with multi-hue flow.
     */
    val DEER = ShapeDesign(
        name = "Deer",
        aspect = 0.70f,
        regions = listOf(
            svgRegion(
                name = "antlers",
                color = DEER_B,
                layer = 1,
                curl = 1.3f,
                path = "M 0.44 0.30 " +
                    "C 0.34 0.24 0.26 0.16 0.20 0.08 " +
                    "C 0.14 0.14 0.10 0.22 0.08 0.30 " +
                    "C 0.16 0.34 0.26 0.36 0.36 0.38 " +
                    "C 0.40 0.36 0.42 0.33 0.44 0.30 Z " +
                    "M 0.56 0.30 " +
                    "C 0.66 0.24 0.74 0.16 0.80 0.08 " +
                    "C 0.86 0.14 0.90 0.22 0.92 0.30 " +
                    "C 0.84 0.34 0.74 0.36 0.64 0.38 " +
                    "C 0.60 0.36 0.58 0.33 0.56 0.30 Z"
            ),
            svgRegion(
                name = "head",
                color = DEER_A,
                layer = 2,
                path = "M 0.50 0.26 " +
                    "C 0.36 0.26 0.26 0.36 0.26 0.50 " +
                    "C 0.26 0.62 0.32 0.72 0.38 0.82 " +
                    "C 0.42 0.92 0.46 0.98 0.50 0.98 " +
                    "C 0.54 0.98 0.58 0.92 0.62 0.82 " +
                    "C 0.68 0.72 0.74 0.62 0.74 0.50 " +
                    "C 0.74 0.36 0.64 0.26 0.50 0.26 Z"
            ),
            svgRegion(
                name = "ears",
                color = DEER_C,
                layer = 1,
                curl = 1.2f,
                path = "M 0.26 0.40 C 0.18 0.38 0.10 0.40 0.06 0.46 " +
                    "C 0.10 0.52 0.20 0.54 0.28 0.50 Z " +
                    "M 0.74 0.40 C 0.82 0.38 0.90 0.40 0.94 0.46 " +
                    "C 0.90 0.52 0.80 0.54 0.72 0.50 Z"
            ),
            svgRegion(
                name = "neck",
                color = DEER_D,
                layer = 0,
                path = "M 0.38 0.74 " +
                    "C 0.30 0.80 0.24 0.88 0.22 0.98 " +
                    "C 0.40 0.99 0.60 0.99 0.78 0.98 " +
                    "C 0.76 0.88 0.70 0.80 0.62 0.74 Z"
            )
        )
    )

    /**
     * Crab: wide body, two raised claws, legs fanning out either side.
     */
    val CRAB = ShapeDesign(
        name = "Crab",
        aspect = 1.05f,
        regions = listOf(
            svgRegion(
                name = "body",
                color = 0xFFE4572E,
                path = "M 0.50 0.30 " +
                    "C 0.30 0.30 0.16 0.40 0.16 0.54 " +
                    "C 0.16 0.68 0.30 0.78 0.50 0.78 " +
                    "C 0.70 0.78 0.84 0.68 0.84 0.54 " +
                    "C 0.84 0.40 0.70 0.30 0.50 0.30 Z " +
                    "M 0.24 0.16 C 0.16 0.10 0.08 0.12 0.06 0.20 " +
                    "C 0.04 0.28 0.10 0.34 0.18 0.34 " +
                    "C 0.24 0.34 0.28 0.30 0.28 0.24 Z " +
                    "M 0.76 0.16 C 0.84 0.10 0.92 0.12 0.94 0.20 " +
                    "C 0.96 0.28 0.90 0.34 0.82 0.34 " +
                    "C 0.76 0.34 0.72 0.30 0.72 0.24 Z " +
                    "M 0.18 0.60 C 0.10 0.64 0.04 0.72 0.02 0.82 " +
                    "C 0.10 0.84 0.18 0.80 0.24 0.72 Z " +
                    "M 0.82 0.60 C 0.90 0.64 0.96 0.72 0.98 0.82 " +
                    "C 0.90 0.84 0.82 0.80 0.76 0.72 Z"
            )
        )
    )

    val ALL = listOf(OWL, CAT, PANDA, DEER, CRAB)
}

/**
 * Every shape the curved engine can play: legacy, then illustrated animals,
 * then the clean silhouette pool.
 *
 * Level N plays entry `N % size`, so within any window of `size` consecutive
 * levels no shape repeats - the no-repeat rule falls out of the rotation. New
 * designs are appended so the existing entries keep their relative order.
 */
object ShapeCatalog {
    val ALL: List<ShapeDesign> = LegacyShapes.ALL + ShapeLibrary.ALL + PoolShapes.ALL
}
