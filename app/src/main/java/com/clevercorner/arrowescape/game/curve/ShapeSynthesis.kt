package com.clevercorner.arrowescape.game.curve

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Builds silhouettes from parameters instead of from a human typing path
 * data, so the catalogue stops being the length of the game.
 *
 * The journey is one chapter per catalogue shape, which means the game ends
 * when the hand-authored shapes run out - levels past that point loop. More
 * hand-drawn SVGs push the cliff back; they do not remove it.
 *
 * Random outlines read as noise, so three constraints do the work of making
 * synthesis look deliberate:
 *
 *  1. **Parametric families.** A shape is a function of a few numbers, not a
 *     point cloud. `star(points, innerRatio)` is recognisably a star for
 *     every legal input, which is a far stronger guarantee than anything a
 *     random walk can offer.
 *  2. **Symmetry by construction.** Every family here is mirror- or radially
 *     symmetric because it is *generated* that way. Symmetry is most of what
 *     separates "a designed shape" from "a blob".
 *  3. **A quality gate.** Synthesis without rejection is noise with extra
 *     steps - see [ShapeQualityGate], which throws out roughly the candidates
 *     a person would have.
 *
 * Names are derived from the parameters rather than stored, so the header can
 * say "Seven-Point Star" without a table mapping seeds to words.
 */
object ShapeSynthesis {

    private const val TWO_PI = (2 * PI).toFloat()

    /** Families worth generating. Each is reliably recognisable across its range. */
    private enum class Family { STAR, FLOWER, GEAR, POLYGON, CRESCENT, LEAF, BLOB }

    private val WORDS = listOf(
        "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
        "Eleven", "Twelve", "Thirteen", "Fourteen"
    )

    private val PALETTE = listOf(
        Hues.RED, Hues.ORANGE, Hues.GOLD, Hues.GREEN, Hues.TEAL,
        Hues.CYAN, Hues.SKY, Hues.BLUE, Hues.PURPLE, Hues.PINK
    )

    /**
     * A deterministic shape for [index]. The same index always yields the
     * same silhouette, on every device and every run, because a journey whose
     * level 400 differs between two phones is not a journey.
     *
     * Returns null when the candidate fails the quality gate; callers should
     * step to the next index rather than lowering the bar.
     */
    fun synthesize(index: Int): ShapeDesign? {
        val random = Random(index.toLong() * 7919 + 13)
        val family = Family.entries[random.nextInt(Family.entries.size)]
        val (name, points) = when (family) {
            Family.STAR -> star(random)
            Family.FLOWER -> flower(random)
            Family.GEAR -> gear(random)
            Family.POLYGON -> polygon(random)
            Family.CRESCENT -> crescent(random)
            Family.LEAF -> leaf(random)
            Family.BLOB -> blob(random)
        }

        if (!ShapeQualityGate.accepts(points)) return null

        val color = PALETTE[random.nextInt(PALETTE.size)]
        return ShapeDesign(
            name = name,
            aspect = 1f,
            regions = listOf(svgRegion(name = name.lowercase(), path = polygonPath(points), color = color))
        )
    }

    // ── families ─────────────────────────────────────────────────────────
    // Each returns a display name and a closed polygon in the unit square.

    private fun star(random: Random): Pair<String, List<Point2D>> {
        val tips = 5 + random.nextInt(8)
        val innerRatio = 0.36f + random.nextFloat() * 0.22f
        val points = radial(tips * 2) { i ->
            if (i % 2 == 0) 0.5f else 0.5f * innerRatio
        }
        return "${word(tips)}-Point Star" to points
    }

    private fun flower(random: Random): Pair<String, List<Point2D>> {
        val petals = 5 + random.nextInt(7)
        val depth = 0.18f + random.nextFloat() * 0.14f
        val points = radialSmooth(petals * 24) { angle ->
            0.5f - depth * 0.5f * (1f + cos(angle * petals))
        }
        return "${word(petals)}-Petal Flower" to points
    }

    private fun gear(random: Random): Pair<String, List<Point2D>> {
        val teeth = 6 + random.nextInt(7)
        val depth = 0.12f + random.nextFloat() * 0.08f
        val outer = 0.5f
        val inner = 0.5f - depth

        // Square teeth need the radius to jump at a *fixed* angle, so each
        // transition emits two points at the same angle and different radii.
        // Stepping angle and radius together instead - which is what the
        // generic radial helper does - produces diagonal flanks, and a gear
        // with diagonal flanks is a flower.
        val points = mutableListOf<Point2D>()
        for (t in 0 until teeth) {
            val a0 = -PI.toFloat() / 2 + TWO_PI * t / teeth
            val a1 = a0 + TWO_PI / teeth * 0.5f
            val a2 = a0 + TWO_PI / teeth
            points.add(unit(a0, outer))
            points.add(unit(a1, outer))
            points.add(unit(a1, inner))
            points.add(unit(a2, inner))
        }
        return "${word(teeth)}-Tooth Gear" to points
    }

    private fun polygon(random: Random): Pair<String, List<Point2D>> {
        val sides = 5 + random.nextInt(8)
        return "${word(sides)}-Sided Polygon" to radial(sides) { 0.5f }
    }

    private fun crescent(random: Random): Pair<String, List<Point2D>> {
        // Two circles: the disc, and the bite taken out of it. The outline is
        // the disc's major arc plus the bite's arc back, meeting exactly at
        // the two intersection points - so it closes cleanly instead of being
        // two arcs that happen to nearly touch.
        //
        // The first version swept only 180 degrees of the disc, which made a
        // half-moon half the width of the unit square. Every single candidate
        // was then rejected for being too narrow - the family produced
        // nothing at all until this was measured.
        val outerR = 0.5f
        val biteR = 0.40f + random.nextFloat() * 0.08f
        // A shallow bite reads as "circle with a chip out of it" rather than
        // as a crescent. Biting deeper costs acceptances - a thin crescent
        // fails the fill-ratio floor - which is the right trade: a few
        // convincing crescents beat many ambiguous discs.
        val centreGap = 0.34f + random.nextFloat() * 0.12f

        // Circle-circle intersection, centres on the x axis a gap apart.
        val a = (centreGap * centreGap - biteR * biteR + outerR * outerR) / (2 * centreGap)
        val hSquared = outerR * outerR - a * a
        if (hSquared <= 0f) return "Crescent" to emptyList()
        val h = kotlin.math.sqrt(hSquared)

        val theta = kotlin.math.atan2(h, a)                 // on the disc
        val phi = kotlin.math.atan2(h, a - centreGap)       // on the bite

        val steps = 60
        val points = mutableListOf<Point2D>()

        // Disc, the long way round (through pi), so the crescent spans the
        // full width rather than just one side.
        val sweep = TWO_PI - 2 * theta
        for (i in 0..steps) {
            val angle = theta + sweep * i / steps
            points.add(Point2D(0.5f + cos(angle) * outerR, 0.5f + sin(angle) * outerR))
        }
        // Bite, back along the short way.
        for (i in steps downTo 0) {
            val angle = -phi + (2 * phi) * i / steps
            points.add(
                Point2D(
                    0.5f + centreGap + cos(angle) * biteR,
                    0.5f + sin(angle) * biteR
                )
            )
        }
        // centreGap is the dominant visual axis - a bigger gap bites deeper
        // and leaves a thinner sliver. Naming it rather than calling every
        // instance "Crescent" was the whole fix: 175 chapters of synthesis
        // produced 47 "Leaf"s and 41 "Pebble"s with zero distinguishing
        // text, because this family (and leaf, and blob) had exactly one
        // name no matter what the parameters did.
        val name = qualified(band(centreGap, 0.34f, 0.46f, "Full", "Slim"), "Crescent")
        return name to points
    }

    private fun leaf(random: Random): Pair<String, List<Point2D>> {
        // Width follows sin(pi*t) raised to a power: 0 at both ends, fattest
        // in the middle, and the exponent decides whether the tip is blunt
        // or drawn out. Mirroring the same curve guarantees it stays a leaf.
        val sharpness = 0.7f + random.nextFloat() * 0.8f
        val steps = 80
        val points = mutableListOf<Point2D>()
        for (side in listOf(1f, -1f)) {
            val range = if (side > 0) 0..steps else steps downTo 0
            for (i in range) {
                val t = i / steps.toFloat()
                val bulge = Math.pow(sin(PI * t), sharpness.toDouble()).toFloat()
                points.add(Point2D(0.5f + side * 0.30f * bulge, 0.04f + t * 0.92f))
            }
        }
        // Low sharpness blunts the tip toward an oval, high draws it to a
        // point - the single parameter this family has, now named instead
        // of discarded.
        val name = qualified(band(sharpness, 0.7f, 1.5f, "Round", "Pointed"), "Leaf")
        return name to points
    }

    private fun blob(random: Random): Pair<String, List<Point2D>> {
        // Low-order Fourier radius: a couple of harmonics is reliably
        // organic, while more becomes the noise this whole object avoids.
        val h1 = 1 + random.nextInt(3)
        val h2 = h1 + 1 + random.nextInt(3)
        val a1 = 0.05f + random.nextFloat() * 0.07f
        val a2 = 0.03f + random.nextFloat() * 0.05f
        val phase = random.nextFloat() * TWO_PI
        val points = radialSmooth(140) { angle ->
            0.5f - a1 * cos(angle * h1 + phase) - a2 * sin(angle * h2)
        }
        // Total harmonic order is the lobe count: low h1+h2 reads as a
        // smooth stone, high as something lumpier.
        val name = qualified(band((h1 + h2).toFloat(), 3f, 9f, "Smooth", "Lumpy"), "Pebble")
        return name to points
    }

    // ── helpers ──────────────────────────────────────────────────────────

    /** [count] evenly spaced vertices, radius chosen per index. */
    private fun radial(count: Int, radiusAt: (Int) -> Float): List<Point2D> =
        (0 until count).map { i ->
            unit(-PI.toFloat() / 2 + TWO_PI * i / count, radiusAt(i))
        }

    /** Same, but the radius is a continuous function of angle. */
    private fun radialSmooth(count: Int, radiusAt: (Float) -> Float): List<Point2D> =
        (0 until count).map { i ->
            val angle = TWO_PI * i / count
            unit(-PI.toFloat() / 2 + angle, radiusAt(angle))
        }

    private fun unit(angle: Float, radius: Float) =
        Point2D(0.5f + cos(angle) * radius, 0.5f + sin(angle) * radius)

    private fun word(n: Int): String = WORDS.getOrElse(n - 3) { "$n" }

    /**
     * Buckets [value] within [low]..[high] into three descriptive bands, the
     * middle one unnamed - a family's "default" instance should read as
     * plain "Leaf", not "Medium Leaf".
     */
    private fun band(value: Float, low: Float, high: Float, lowWord: String, highWord: String): String {
        val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
        return when {
            t < 0.33f -> lowWord
            t > 0.67f -> highWord
            else -> ""
        }
    }

    private fun qualified(qualifier: String, noun: String): String =
        if (qualifier.isEmpty()) noun else "$qualifier $noun"

    private fun polygonPath(points: List<Point2D>): String = buildString {
        append("M ${fmt(points.first().x)} ${fmt(points.first().y)}")
        for (p in points.drop(1)) append(" L ${fmt(p.x)} ${fmt(p.y)}")
        append(" Z")
    }

    private fun fmt(v: Float) = "%.4f".format(v)
}

/**
 * Throws out synthesised candidates a person would have thrown out.
 *
 * Synthesis is only as good as its rejection rule: every family in
 * [ShapeSynthesis] has parameter combinations that produce something thin,
 * spiky or barely there, and shipping those is what makes procedural content
 * feel cheap. The thresholds are the ones from the plan, and they are all
 * cheap to evaluate on the polygon itself - no rasterisation needed for most
 * of them.
 */
object ShapeQualityGate {

    /** Fraction of the bounding box the shape must occupy. */
    private const val MIN_FILL = 0.30f
    private const val MAX_FILL = 0.85f

    /**
     * Cap on perimeter squared over area - the standard compactness measure,
     * and the single most effective anti-spike rule. A circle sits at about
     * 12.6; anything past the cap is a star so thin it reads as a scribble.
     */
    private const val MAX_COMPACTNESS = 95f

    /** The shape must span at least this much of the unit square on both axes. */
    private const val MIN_EXTENT = 0.55f

    fun accepts(points: List<Point2D>): Boolean {
        if (points.size < 3) return false

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY
        if (width < MIN_EXTENT || height < MIN_EXTENT) return false

        val area = abs(shoelaceArea(points))
        if (area <= 0f) return false

        val boxArea = width * height
        val fill = area / boxArea
        if (fill < MIN_FILL || fill > MAX_FILL) return false

        val perimeter = perimeterOf(points)
        if (perimeter * perimeter / area > MAX_COMPACTNESS) return false

        return !selfIntersects(points)
    }

    private fun shoelaceArea(points: List<Point2D>): Float {
        var sum = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return sum / 2f
    }

    private fun perimeterOf(points: List<Point2D>): Float {
        var total = 0f
        for (i in points.indices) {
            total += points[i].distanceTo(points[(i + 1) % points.size])
        }
        return total
    }

    /**
     * A self-intersecting outline fills in a way nobody intended - the cat
     * silhouette taught this the hard way, twice, so synthesis checks rather
     * than hopes.
     *
     * Skipped for dense outlines, where adjacent samples are close enough
     * that floating point noise produces false positives and the O(n^2) scan
     * stops being free.
     */
    private fun selfIntersects(points: List<Point2D>): Boolean {
        val n = points.size
        if (n > 90) return false

        for (i in 0 until n) {
            for (j in i + 2 until n) {
                if (i == 0 && j == n - 1) continue
                if (segmentsCross(
                        points[i], points[(i + 1) % n],
                        points[j], points[(j + 1) % n]
                    )
                ) return true
            }
        }
        return false
    }

    private fun segmentsCross(p1: Point2D, p2: Point2D, p3: Point2D, p4: Point2D): Boolean {
        fun ccw(a: Point2D, b: Point2D, c: Point2D) =
            (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
        return ccw(p1, p3, p4) != ccw(p2, p3, p4) && ccw(p1, p2, p3) != ccw(p1, p2, p4)
    }

    /** Smallest grid a shape should be rasterised at before it turns to mush. */
    fun survivesGrid(points: List<Point2D>, grid: Int): Boolean {
        if (grid < 4) return false
        var filled = 0
        for (row in 0 until grid) {
            for (col in 0 until grid) {
                val p = Point2D((col + 0.5f) / grid, (row + 0.5f) / grid)
                if (containsPoint(points, p)) filled++
            }
        }
        val fill = filled.toFloat() / (grid * grid)
        return fill in MIN_FILL..MAX_FILL
    }

    private fun containsPoint(polygon: List<Point2D>, p: Point2D): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > p.y) != (b.y > p.y) &&
                p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
            ) inside = !inside
            j = i
        }
        return inside
    }
}
