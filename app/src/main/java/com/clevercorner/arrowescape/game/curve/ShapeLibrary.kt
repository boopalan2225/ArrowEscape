package com.clevercorner.arrowescape.game.curve

/**
 * What a region *is*, which decides how it becomes arrows.
 *
 * Every design used to be an area to be packed solid, and that is the whole
 * reason the references look like drawings and our output looks like hatching.
 * A drawing is not only filled areas: it has a line round the outside, empty
 * channels that separate one mass from another, and small deliberate strokes
 * that *are* the detail rather than a fill of it.
 */
enum class RegionKind {
    /** Pack the interior with flowing strokes. The original behaviour. */
    FILL,

    /**
     * No arrows at all - the region exists to hold space open.
     *
     * This is how negative space gets drawn: the channel between a wing and a
     * body is not a colour, it is an absence, and an absence has to be
     * reserved or the fill beneath simply closes over it.
     */
    GAP,

    /**
     * Strokes that follow the region's contour instead of filling it, laid
     * over whatever is beneath. Does not reserve its area - an outline pass
     * that claimed the silhouette would leave the fill nowhere to go.
     */
    OUTLINE,

    /**
     * Contour strokes that *are* the detail - an owl's eye rings, a mouth
     * line. Unlike [OUTLINE] it keeps its own area, so the fill underneath
     * cannot crowd into the eye it is supposed to leave alone.
     */
    FEATURE;

    /** Whether lower layers must flow around this region's area. */
    val reservesArea: Boolean get() = this != OUTLINE

    /** Whether this region contributes ink of its own. */
    val drawsArrows: Boolean get() = this != GAP
}

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
    /** Fill, hold space open, or stroke the contour. See [RegionKind]. */
    val kind: RegionKind = RegionKind.FILL,
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
    flow: CurveGenerator.FlowType? = null,
    kind: RegionKind = RegionKind.FILL
) = ShapeRegion(name, color, layer, strokeScale, curl, flow, kind) { w, h ->
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
    private const val CAT_EYE = 0xFFA463D6

    private const val PANDA_BODY = 0xFF2F7FEA
    private const val PANDA_GRASS = 0xFF7AC943
    private const val PANDA_DARK = 0xFF16224A

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
            // The eyes are the detail, not an area holding some of it: these
            // follow their own contour (see RegionKind.FEATURE) rather than
            // approximating a ring with a RING flow field and zero curl,
            // which is what they used to do. Flow and curl are deliberately
            // absent now - a contour pass does not consult either.
            svgRegion(
                name = "eyeLeft",
                color = OWL_EYE,
                layer = 2,
                strokeScale = 0.8f,
                kind = RegionKind.FEATURE,
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
                kind = RegionKind.FEATURE,
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
    /**
     * Cat: sitting side profile - pointed ears, a chest patch, and the tail
     * curling up over the back.
     *
     * The previous silhouette was one hand-typed outline that was supposed
     * to read as ears+face+back+tail+legs all at once, and did not - per B,
     * "no one can actually recognise it's that animal". A cat's silhouette
     * is its ears, its sitting posture, and above all its tail; the old
     * outline had none of the three as a distinct, legible shape. This
     * version threads a smooth closed curve through named landmarks (ear
     * tip, notch, cheek, chin, chest, paw, haunch, back, shoulder) via
     * Catmull-Rom interpolation rather than hand-picking bezier control
     * points, so the curve is guaranteed smooth and correct between the
     * chosen landmarks even though the landmarks themselves are a judgement
     * call. The tail is its own region - a wide asymmetric crescent, same
     * construction as the flower's leaves - rather than folded into one
     * self-intersecting outline, which is both safer to get right and
     * exactly how the reference reads: a separate sweeping shape, not a
     * notch in the body.
     */
    /**
     * Cat: sitting silhouette - pointed ears, a chest patch, and the tail
     * curling up over the back.
     *
     * The previous silhouette was one hand-typed outline trying to be
     * ears+face+back+tail+legs at once, and per B, "no one can actually
     * recognise it's that animal". A first rebuild tried to thread a single
     * smooth curve through a full face profile (cheek, chin, jaw) and kept
     * self-intersecting - confirmed by sampling the curve and checking every
     * segment pair, not by eye - because a one-sided jaw detour and the
     * return path to the ears want to occupy the same corridor. Rather than
     * fight that, the body here is a simpler, more convex blob (the same
     * complexity class as the owl's already-proven silhouette), verified
     * crossing-free the same way, and recognisability comes from what's
     * layered on top instead: two triangular ears, a chest patch, eyes, and
     * - the single biggest miss before - an actual tail, built the same
     * asymmetric-crescent way as the flower's leaves.
     */
    val CAT = ShapeDesign(
        name = "Cat",
        aspect = 0.80f,
        regions = listOf(
            svgRegion(
                name = "body",
                color = CAT_BODY,
                path = "M 0.400 0.080 C 0.433 0.043 0.553 0.057 0.600 0.080 " +
                    "C 0.647 0.103 0.650 0.157 0.680 0.220 " +
                    "C 0.710 0.283 0.747 0.380 0.780 0.460 " +
                    "C 0.813 0.540 0.877 0.620 0.880 0.700 " +
                    "C 0.883 0.780 0.870 0.892 0.800 0.940 " +
                    "C 0.730 0.988 0.557 1.003 0.460 0.990 " +
                    "C 0.363 0.977 0.253 0.925 0.220 0.860 " +
                    "C 0.187 0.795 0.230 0.693 0.260 0.600 " +
                    "C 0.290 0.507 0.377 0.387 0.400 0.300 " +
                    "C 0.423 0.213 0.367 0.117 0.400 0.080 Z"
            ),
            svgRegion(
                name = "earLeft",
                color = CAT_BODY,
                layer = 1,
                path = "M 0.420 0.110 L 0.380 -0.080 L 0.520 0.070 Z"
            ),
            svgRegion(
                name = "earRight",
                color = CAT_BODY,
                layer = 1,
                path = "M 0.540 0.070 L 0.620 -0.100 L 0.660 0.130 Z"
            ),
            svgRegion(
                name = "chest",
                color = CAT_CHEST,
                layer = 1,
                curl = 1.5f,
                path = "M 0.392 0.658 C 0.411 0.727 0.389 0.793 0.344 0.806 " +
                    "C 0.298 0.818 0.246 0.771 0.228 0.702 " +
                    "C 0.209 0.633 0.231 0.567 0.276 0.554 " +
                    "C 0.322 0.542 0.374 0.589 0.392 0.658 Z"
            ),
            svgRegion(
                name = "tail",
                color = CAT_BODY,
                layer = 1,
                path = "M 0.860 0.780 C 0.946 0.506 0.851 0.315 0.580 0.220 " +
                    "C 0.628 0.427 0.723 0.618 0.860 0.780 Z"
            ),
            svgRegion(
                name = "eyeLeft",
                color = CAT_EYE,
                layer = 2,
                strokeScale = 0.75f,
                kind = RegionKind.FEATURE,
                path = "M 0.524 0.181 C 0.526 0.191 0.513 0.202 0.495 0.207 " +
                    "C 0.476 0.212 0.459 0.209 0.456 0.199 " +
                    "C 0.454 0.189 0.467 0.178 0.485 0.173 " +
                    "C 0.504 0.168 0.521 0.171 0.524 0.181 Z"
            ),
            svgRegion(
                name = "eyeRight",
                color = CAT_EYE,
                layer = 2,
                strokeScale = 0.75f,
                kind = RegionKind.FEATURE,
                path = "M 0.624 0.189 C 0.621 0.199 0.604 0.202 0.585 0.197 " +
                    "C 0.567 0.192 0.554 0.181 0.556 0.171 " +
                    "C 0.559 0.161 0.576 0.158 0.595 0.163 " +
                    "C 0.613 0.168 0.626 0.179 0.624 0.189 Z"
            )
        )
    )

    /**
     * Panda: seated bear with round ears, sitting on a band of grass.
     *
     * The body silhouette already carved ear-shaped bumps into its own
     * outline, but same-coloured bumps read as lumps, not ears - and with no
     * eye patches at all, there was no panda left to recognise: that one
     * feature is doing most of the identification work for this animal.
     * Both are added here as their own regions rather than redrawn into the
     * body outline, so the existing silhouette - proven to pack and solve
     * correctly - does not have to change.
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
            ),
            // The ears: the same bumps the body silhouette already carves
            // out, recoloured so they read as ears instead of lumps.
            svgRegion(
                name = "earLeft",
                color = PANDA_DARK,
                layer = 2,
                strokeScale = 0.85f,
                path = "M 0.355 0.130 C 0.355 0.166 0.326 0.195 0.290 0.195 " +
                    "C 0.254 0.195 0.225 0.166 0.225 0.130 " +
                    "C 0.225 0.094 0.254 0.065 0.290 0.065 " +
                    "C 0.326 0.065 0.355 0.094 0.355 0.130 Z"
            ),
            svgRegion(
                name = "earRight",
                color = PANDA_DARK,
                layer = 2,
                strokeScale = 0.85f,
                path = "M 0.775 0.130 C 0.775 0.166 0.746 0.195 0.710 0.195 " +
                    "C 0.674 0.195 0.645 0.166 0.645 0.130 " +
                    "C 0.645 0.094 0.674 0.065 0.710 0.065 " +
                    "C 0.746 0.065 0.775 0.094 0.775 0.130 Z"
            ),
            // The eye patches: the single feature that makes a round blue
            // blob read as "panda" specifically.
            svgRegion(
                name = "eyePatchLeft",
                color = PANDA_DARK,
                layer = 2,
                strokeScale = 0.85f,
                path = "M 0.447 0.284 C 0.461 0.314 0.438 0.355 0.395 0.374 " +
                    "C 0.353 0.394 0.307 0.386 0.293 0.356 " +
                    "C 0.279 0.326 0.302 0.285 0.345 0.266 " +
                    "C 0.387 0.246 0.433 0.254 0.447 0.284 Z"
            ),
            svgRegion(
                name = "eyePatchRight",
                color = PANDA_DARK,
                layer = 2,
                strokeScale = 0.85f,
                path = "M 0.707 0.356 C 0.693 0.386 0.647 0.394 0.605 0.374 " +
                    "C 0.562 0.355 0.539 0.314 0.553 0.284 " +
                    "C 0.567 0.254 0.613 0.246 0.655 0.266 " +
                    "C 0.698 0.285 0.721 0.326 0.707 0.356 Z"
            )
        )
    )

    /**
     * Deer: antlered head, drawn as one silhouette with multi-hue flow.
     *
     * The antlers used to be two plain almond blobs - the single reason this
     * design was unrecognisable, since a deer's silhouette *is* its
     * branching antlers. Each side is now a fan of four tapering spikes from
     * one base point near the top of the head: a long main beam plus three
     * shorter tines at increasing angles, the same non-overlapping-fan
     * construction already proven by the owl's two eyes and the flower's two
     * leaves - no new geometry trick, just more pieces of the one already
     * in use.
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
                path = "M 0.437 0.239 L 0.394 -0.059 L 0.403 0.241 L 0.420 0.240 Z " +
                    "M 0.434 0.244 L 0.487 -0.011 L 0.406 0.236 L 0.420 0.240 Z " +
                    "M 0.431 0.233 L 0.305 0.076 L 0.409 0.247 L 0.420 0.240 Z " +
                    "M 0.425 0.230 L 0.299 0.170 L 0.414 0.250 L 0.420 0.240 Z " +
                    "M 0.597 0.241 L 0.606 -0.059 L 0.563 0.239 L 0.580 0.240 Z " +
                    "M 0.594 0.236 L 0.513 -0.011 L 0.566 0.244 L 0.580 0.240 Z " +
                    "M 0.591 0.247 L 0.695 0.076 L 0.569 0.233 L 0.580 0.240 Z " +
                    "M 0.585 0.250 L 0.701 0.170 L 0.575 0.230 L 0.580 0.240 Z"
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

    // CAT and PANDA stay defined above for classic mode's DesignMask (grid-
    // mode icons/masks still use them) but are deliberately absent from the
    // curved rotation below - the SVG-asset versions in assets/shapes/
    // replace them there, registered separately by CurvedAssetShapes.
    val ALL = listOf(OWL, DEER, CRAB)
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
    private val extra = mutableListOf<ShapeDesign>()
    private var consumed = false

    /**
     * Adds designs loaded at runtime to the rotation. Must be called before
     * anything has read [ALL].
     *
     * A plain mutable list rather than folding asset-loading straight into
     * [ALL]: this object has to stay pure-JVM, because the generator's unit
     * tests construct no Android Application and read no assets. Those
     * tests never call [register], so `ALL` is exactly what it always was
     * for them - the asset dependency only exists for whoever opts in.
     */
    fun register(designs: List<ShapeDesign>) {
        check(!consumed) {
            "ShapeCatalog.ALL has already been read - register() is too late to affect the rotation"
        }
        extra.addAll(designs)
    }

    val ALL: List<ShapeDesign> by lazy {
        consumed = true
        LegacyShapes.ALL + ShapeLibrary.ALL + PoolShapes.ALL + extra
    }

    /**
     * The shape for a chapter, synthesising one when the hand-authored
     * catalogue runs out.
     *
     * [ALL] is finite, so the journey used to loop once it was exhausted -
     * meeting the same silhouettes again at a harder tier. That is a
     * reasonable fallback and a visible cliff. Past the end, chapters get
     * generated shapes instead (see [ShapeSynthesis]), which are
     * deterministic per index and therefore identical on every device.
     *
     * Synthesis can reject its own candidate, so this walks forward until
     * one passes rather than lowering the bar to accept a bad shape. The
     * walk is bounded: if a long run somehow all fail, it falls back to the
     * old looping behaviour, because shipping a chapter is better than
     * throwing.
     */
    fun designForChapter(chapter: Int): ShapeDesign {
        if (chapter < ALL.size) return ALL[chapter]

        var index = chapter - ALL.size
        repeat(SYNTHESIS_ATTEMPTS) {
            ShapeSynthesis.synthesize(index)?.let { return it }
            index++
        }
        return ALL[chapter % ALL.size]
    }

    /** How far to walk past a rejected candidate before giving up and looping. */
    private const val SYNTHESIS_ATTEMPTS = 24

    /**
     * Chapters that exist without synthesising anything - what the Journey
     * Map can show as a finite, completable list.
     */
    val authoredChapterCount: Int get() = ALL.size
}
