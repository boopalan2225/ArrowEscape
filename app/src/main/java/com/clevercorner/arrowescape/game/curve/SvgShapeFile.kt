package com.clevercorner.arrowescape.game.curve

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loads a [ShapeDesign] from a multi-layer SVG file exported by any vector
 * tool, instead of hand-typing path strings into Kotlin.
 *
 * ShapeLibrary's hand-typed designs kept losing to hand-drawn bezier math: a
 * cat rebuilt from landmark points still didn't resolve into "cat" at any
 * angle. A real export fixes that because it was drawn by someone who could
 * see it. Two things a real export needs that the first version of this
 * parser didn't have:
 *
 *  - Plain fill="#RRGGBB". A real tool puts colour on the element itself, not
 *    in its id - id="belly#F5E2A8" was an authoring convention invented for
 *    hand-written fixtures, not something an artist's tool produces. An
 *    element's own fill now wins; an id-embedded colour is kept only as a
 *    power-user override.
 *  - circle and ellipse elements, not just path. Round regions (eyes, paws,
 *    ears) are exported as native shapes, not traced into bezier path data,
 *    because why would they be.
 *
 * fill="none" is also read as a signal, not just a missing colour: a
 * stroked, unfilled element (panda.svg's mouth) is a line that is meant to be
 * a line, so it becomes a [RegionKind.FEATURE] drawn in its stroke colour
 * rather than a fill region with no colour to fill it.
 *
 *   path id="body" fill="#D98B45" d="..."
 *   ellipse id="left_eye" cx="185" cy="280" rx="17" ry="23" fill="#38271F"
 *   path id="mouth" fill="none" stroke="#38271F" d="..."
 *
 * The older id="name#RRGGBB@flow,key=value" convention (see [LayerMeta])
 * still works and still wins when present, for files that want to say more
 * than an element's own attributes can - a flow override, an explicit kind,
 * an explicit layer.
 *
 * A group element may hold several children - a region that is more than one
 * disconnected piece (a sunflower's two leaves) - which are concatenated into
 * one region's geometry. An element with no id, or an id that does not match
 * the naming grammar, is skipped rather than failing the whole file.
 *
 * Pure XML/string handling, no Android dependency, so it is unit-testable the
 * same way [SvgPath] already is.
 */
object SvgShapeFile {

    fun parse(xml: String, designName: String): ShapeDesign {
        val root = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xml.byteInputStream())
            .documentElement

        val (viewW, viewH) = viewBoxSize(root)
        val regions = mutableListOf<ShapeRegion>()

        root.children().forEachIndexed { index, element ->
            val id = element.getAttribute("id")
            if (id.isBlank()) return@forEachIndexed
            val meta = LayerMeta.parse(id) ?: return@forEachIndexed

            val pathData = geometryWithin(element)
            if (pathData.isEmpty()) return@forEachIndexed

            val unitCommands = pathData.flatMap { d -> SvgPath.parse(d, 1f / viewW, 1f / viewH) }
            val color = meta.color ?: resolvedColorOf(element) ?: Hues.BLUE
            // A stroked, unfilled element is a line on purpose - draw it as
            // one rather than as an area with nothing to fill it, unless the
            // id already said what it should be.
            val kind = if (meta.kind == RegionKind.FILL && isStrokeOnly(element)) {
                RegionKind.FEATURE
            } else {
                meta.kind
            }

            regions.add(
                ShapeRegion(
                    name = meta.name,
                    color = color,
                    layer = meta.layer ?: index,
                    strokeScale = meta.strokeScale,
                    curl = meta.curl,
                    flow = meta.flow,
                    kind = kind
                ) { w, h -> ShapePath(meta.name, unitCommands.scaledBy(w, h), w, h, color) }
            )
        }

        return ShapeDesign(name = designName, regions = regions, aspect = viewW / viewH)
    }

    private fun isStrokeOnly(element: Element): Boolean =
        element.getAttribute("fill").trim().equals("none", ignoreCase = true)

    /** An element's own fill, or its stroke when fill="none" - an artist's own colour, not a guess. */
    private fun resolvedColorOf(element: Element): Long? {
        val fill = element.getAttribute("fill")
        if (fill.isNotBlank() && !fill.equals("none", ignoreCase = true)) return parseHexColor(fill)
        return parseHexColor(element.getAttribute("stroke"))
    }

    private fun parseHexColor(value: String): Long? {
        val hex = value.trim().removePrefix("#")
        if (hex.length != 6 || hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
        return 0xFF000000L or hex.toLong(16)
    }

    /** viewBox="minX minY w h", falling back to width/height, then to a unit square. */
    private fun viewBoxSize(root: Element): Pair<Float, Float> {
        val viewBox = root.getAttribute("viewBox")
        if (viewBox.isNotBlank()) {
            val parts = viewBox.trim().split(Regex("[\\s,]+"))
            if (parts.size == 4) {
                val w = parts[2].toFloatOrNull()
                val h = parts[3].toFloatOrNull()
                if (w != null && h != null && w > 0f && h > 0f) return w to h
            }
        }
        val w = root.getAttribute("width").toFloatOrNull()
        val h = root.getAttribute("height").toFloatOrNull()
        return (w ?: 1f) to (h ?: 1f)
    }

    /**
     * Every piece of fillable geometry at or below [element], as path "d"
     * strings - a layer's own shape, however many pieces or native shape
     * kinds it is made of. circle/ellipse are converted to the same bezier
     * "d" syntax [SvgPath] already parses, rather than teaching the rest of
     * the pipeline a second shape representation for two primitives.
     */
    private fun geometryWithin(element: Element): List<String> {
        val paths = mutableListOf<String>()
        fun walk(node: Element) {
            when (node.tagName) {
                "path" -> node.getAttribute("d").takeIf { it.isNotBlank() }?.let(paths::add)
                "circle" -> paths.add(circlePathData(node))
                "ellipse" -> paths.add(ellipsePathData(node))
            }
            node.children().forEach(::walk)
        }
        walk(element)
        return paths
    }

    private fun circlePathData(node: Element): String {
        val cx = node.getAttribute("cx").toFloatOrNull() ?: 0f
        val cy = node.getAttribute("cy").toFloatOrNull() ?: 0f
        val r = node.getAttribute("r").toFloatOrNull() ?: 0f
        return ellipseArc(cx, cy, r, r, 0f)
    }

    private fun ellipsePathData(node: Element): String {
        val cx = node.getAttribute("cx").toFloatOrNull() ?: 0f
        val cy = node.getAttribute("cy").toFloatOrNull() ?: 0f
        val rx = node.getAttribute("rx").toFloatOrNull() ?: 0f
        val ry = node.getAttribute("ry").toFloatOrNull() ?: 0f
        val rotation = Regex("rotate\\(\\s*(-?[\\d.]+)").find(node.getAttribute("transform"))
            ?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        return ellipseArc(cx, cy, rx, ry, rotation)
    }

    /**
     * A circle or ellipse as four cubic bezier arcs - the standard
     * constant-k approximation - optionally rotated about its own centre,
     * which is the only pivot a design tool's rotate(deg cx cy) uses when it
     * rotates a shape in place (the common case, and the only one these
     * files need).
     */
    private fun ellipseArc(cx: Float, cy: Float, rx: Float, ry: Float, rotationDeg: Float): String {
        val k = 0.5523f
        val rad = Math.toRadians(rotationDeg.toDouble())
        val cosT = cos(rad).toFloat()
        val sinT = sin(rad).toFloat()
        fun pt(x: Float, y: Float): Pair<Float, Float> =
            (cx + x * cosT - y * sinT) to (cy + x * sinT + y * cosT)

        val p0 = pt(rx, 0f); val p1 = pt(0f, ry); val p2 = pt(-rx, 0f); val p3 = pt(0f, -ry)
        val c0a = pt(rx, ry * k); val c0b = pt(rx * k, ry)
        val c1a = pt(-rx * k, ry); val c1b = pt(-rx, ry * k)
        val c2a = pt(-rx, -ry * k); val c2b = pt(-rx * k, -ry)
        val c3a = pt(rx * k, -ry); val c3b = pt(rx, -ry * k)
        fun f(p: Pair<Float, Float>) = "${p.first} ${p.second}"
        return "M ${f(p0)} " +
            "C ${f(c0a)} ${f(c0b)} ${f(p1)} " +
            "C ${f(c1a)} ${f(c1b)} ${f(p2)} " +
            "C ${f(c2a)} ${f(c2b)} ${f(p3)} " +
            "C ${f(c3a)} ${f(c3b)} ${f(p0)} Z"
    }

    private fun Element.children(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) result.add(node as Element)
        }
        return result
    }
}
