package com.clevercorner.arrowescape.game.curve

/** Scales every point in a command list - used to turn unit-space (0..1) SVG geometry into board pixels. */
internal fun List<PathCommand>.scaledBy(sx: Float, sy: Float): List<PathCommand> {
    fun p(point: Point2D) = Point2D(point.x * sx, point.y * sy)
    return map { command ->
        when (command) {
            is PathCommand.MoveTo -> PathCommand.MoveTo(p(command.point))
            is PathCommand.LineTo -> PathCommand.LineTo(p(command.point))
            is PathCommand.QuadTo -> PathCommand.QuadTo(p(command.control), p(command.end))
            is PathCommand.CurveTo -> PathCommand.CurveTo(p(command.control1), p(command.control2), p(command.end))
            PathCommand.Close -> PathCommand.Close
        }
    }
}

/**
 * The metadata encoded in one SVG layer's `id`, per the authoring convention
 * [SvgShapeFile] expects: `name[#RRGGBB][@flow][,key=value,...]`.
 *
 * Three real examples from the field this exists to serve:
 *   `body#E87A22`                -> a plain filled region, that colour.
 *   `belly#F5E2A8@meander`       -> filled, flagged for a fill mode that does
 *                                   not exist yet (Stage 8) - parsed now so
 *                                   authored files do not need re-exporting
 *                                   once it lands, but harmlessly ignored
 *                                   today rather than rejected.
 *   `eye#D2E34A@ring,stroke=0.5` -> ring flow, half-width strokes.
 *   `gap` / `outline` / `feature` bare -> the region kind, by name alone,
 *                                   because that is how the roadmap itself
 *                                   writes the convention.
 */
internal data class LayerMeta(
    val name: String,
    val color: Long?,
    val flow: CurveGenerator.FlowType?,
    val strokeScale: Float,
    val curl: Float,
    val kind: RegionKind,
    val layer: Int?
) {
    companion object {
        private val GRAMMAR = Regex(
            "^([A-Za-z0-9_-]+)(#[0-9A-Fa-f]{6})?(@[A-Za-z]+)?((?:,[A-Za-z]+=[-A-Za-z0-9.]+)*)$"
        )
        private val RESERVED_KINDS = mapOf(
            "gap" to RegionKind.GAP,
            "outline" to RegionKind.OUTLINE,
            "feature" to RegionKind.FEATURE
        )

        /** Null means the id did not match the convention - the layer is skipped, not crashed on. */
        fun parse(id: String): LayerMeta? {
            val match = GRAMMAR.matchEntire(id.trim()) ?: return null
            val (name, hex, flowToken, extras) = match.destructured

            val pairs = extras.removePrefix(",").split(",")
                .filter { it.isNotBlank() }
                .associate { pair ->
                    val (key, value) = pair.split("=", limit = 2)
                    key.lowercase() to value
                }

            val kindOverride = pairs["kind"]?.let { RESERVED_KINDS[it.lowercase()] ?: RegionKind.FILL }
            val kind = kindOverride ?: RESERVED_KINDS[name.lowercase()] ?: RegionKind.FILL

            return LayerMeta(
                name = name,
                color = hex.removePrefix("#").takeIf { it.isNotEmpty() }
                    ?.let { 0xFF000000L or it.toLong(16) },
                // Unrecognised tokens (a future fill mode, a typo) are
                // dropped rather than failing the whole layer - an
                // authoring-time mistake should cost one flourish, not the
                // shape.
                flow = flowToken.removePrefix("@").takeIf { it.isNotEmpty() }
                    ?.let { token -> CurveGenerator.FlowType.entries.find { it.name.equals(token, ignoreCase = true) } },
                strokeScale = pairs["stroke"]?.toFloatOrNull() ?: 1f,
                curl = pairs["curl"]?.toFloatOrNull() ?: 1f,
                kind = kind,
                layer = pairs["layer"]?.toIntOrNull()
            )
        }
    }
}
