package com.example.arrowescape.game.curve

import java.io.File

/**
 * Dev-only harness: dumps generated levels as SVG so the artwork can be
 * reviewed without an emulator.
 *
 * SVG rather than PNG because the Android unit-test classpath has no AWT,
 * and the renderer we actually care about is a vector one anyway.
 */
object ShapePreview {

    private const val W = 400f
    private const val H = 620f

    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = File(args.firstOrNull() ?: "build/shape-preview")
        outDir.mkdirs()

        // Level N plays catalogue entry N % size, so this hits every shape once.
        val size = ShapeCatalog.ALL.size
        for (index in 0 until size) {
            val level = size * 2 + index
            val started = System.nanoTime()
            val data = CurvedLevelGenerator.generateLevel(level, W, H)
            val ms = (System.nanoTime() - started) / 1_000_000
            File(outDir, "%02d-%s.svg".format(index, data.shapeName)).writeText(toSvg(data))
            println(
                "idx=%-3d level=%-4d shape=%-10s arrows=%-4d dropped=%-4d %-7s tight=%.2f  %dms".format(
                    index, level, data.shapeName, data.arrows.size, data.droppedArrows, data.difficulty,
                    data.tightness, ms
                )
            )
        }

        println("Wrote previews to ${outDir.absolutePath}")
    }

    private fun toSvg(level: CurvedLevel): String = buildString {
        append("""<svg xmlns="http://www.w3.org/2000/svg" width="$W" height="$H" """)
        append("""viewBox="0 0 $W $H">""")
        append("""<rect width="100%" height="100%" fill="#1e1e1e"/>""")

        // Debug: draw region outlines so silhouette problems stay visible
        // even when the arrow fill is sparse.
        for (region in level.regionOutlines) {
            for (contour in region.contours) {
                append("""<polyline points="""")
                contour.forEach { append("%.1f,%.1f ".format(it.x, it.y)) }
                append("""" fill="none" stroke="#555" stroke-width="1"/>""")
            }
        }

        for (arrow in level.arrows) {
            val hex = "#%06X".format(arrow.color.toInt() and 0xFFFFFF)

            append("""<path d="""")
            arrow.segments.forEachIndexed { i, s ->
                if (i == 0) append("M %.1f %.1f ".format(s.p0.x, s.p0.y))
                append(
                    "C %.1f %.1f %.1f %.1f %.1f %.1f ".format(
                        s.p1.x, s.p1.y, s.p2.x, s.p2.y, s.p3.x, s.p3.y
                    )
                )
            }
            append(
                """" fill="none" stroke="$hex" stroke-width="%.1f" """
                    .format(arrow.strokeWidth)
            )
            append("""stroke-linecap="round" stroke-linejoin="round"/>""")

            // Arrowhead
            val tip = arrow.endPoint
            val a = kotlin.math.atan2(arrow.direction.y, arrow.direction.x)
            val len = arrow.strokeWidth * 2.2f
            val wid = arrow.strokeWidth * 1.6f
            append(
                """<polygon points="%.1f,%.1f %.1f,%.1f %.1f,%.1f %.1f,%.1f" fill="$hex"/>"""
                    .format(
                        tip.x + kotlin.math.cos(a) * len, tip.y + kotlin.math.sin(a) * len,
                        tip.x + kotlin.math.cos(a + 2.5f) * wid, tip.y + kotlin.math.sin(a + 2.5f) * wid,
                        tip.x, tip.y,
                        tip.x + kotlin.math.cos(a - 2.5f) * wid, tip.y + kotlin.math.sin(a - 2.5f) * wid
                    )
            )
        }

        append("</svg>")
    }
}
