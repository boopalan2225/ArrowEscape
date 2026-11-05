package com.clevercorner.arrowescape.perf

import com.clevercorner.arrowescape.game.curve.CurvedLevelGenerator
import com.clevercorner.arrowescape.game.curve.ShapeCatalog
import com.clevercorner.arrowescape.game.curve.SvgShapeFile
import com.clevercorner.arrowescape.game.level.LevelGenerator
import java.io.File

/**
 * One description of a generated board, stable enough to compare across a
 * refactor and small enough to read in a diff.
 *
 * Levels are *seeded*: every device builds level 203 identically, and that is
 * a promise the game makes to leaderboards, to the daily puzzle and to anyone
 * comparing notes. It also makes performance work dangerous, because changing
 * where a curve is sampled changes what boards exist - a "pure optimisation"
 * can silently re-deal the entire game.
 *
 * So optimisation work goes: fingerprint, change, fingerprint, diff. A change
 * either reproduces these exactly or it is a content change wearing a
 * performance costume, and it says so out loud.
 */
object BoardFingerprint {

    /** Board geometry rounded to a tenth of a pixel - float noise is not content. */
    fun ofCurved(level: Int, width: Float = 400f, height: Float = 600f): String {
        val built = CurvedLevelGenerator.generateLevel(level, width, height)
        val geometry = built.arrows.joinToString(";") { arrow ->
            val tip = arrow.endPoint
            val tail = arrow.startPoint
            "${arrow.id}:${round(tail.x)},${round(tail.y)}>${round(tip.x)},${round(tip.y)}" +
                ":${arrow.lockTier}/${arrow.sequenceKey ?: '-'}"
        }
        return "curved/$level ${built.shapeName} arrows=${built.arrows.size} " +
            "dropped=${built.droppedArrows} tight=${round(built.tightness)} " +
            "order=${built.solutionOrder.joinToString(",")} hash=${hash(geometry)}"
    }

    fun ofClassic(level: Int): String {
        val built = LevelGenerator.generateLevel(level)
        val geometry = built.arrows.joinToString(";") { arrow ->
            val body = arrow.cells.joinToString("-") { "${it.row},${it.col}" }
            "${arrow.id}:$body:${arrow.direction}" +
                ":${arrow.colorGroup ?: '-'}/${arrow.sequenceKey ?: '-'}"
        }
        return "classic/$level ${built.shapeName} ${built.difficultyLabel} " +
            "arrows=${built.arrows.size} grid=${built.gridRows}x${built.gridCols} " +
            "tight=${round(built.tightness)} chain=${built.longestChain} " +
            "order=${built.solutionOrder.joinToString(",")} hash=${hash(geometry)}"
    }

    /**
     * Registers the SVG shapes the app loads at startup.
     *
     * Without this, every unit test in the project is blind to the densest,
     * slowest and newest content in the game: [ShapeCatalog.register] is
     * normally only ever called from `Application.onCreate` with an Android
     * `AssetManager`. Reading the same files straight off disk needs no
     * Android at all, and it is the difference between a harness that covers
     * the catalogue and one that covers the easy half of it.
     *
     * Safe to call from every harness class: the JVM is shared across test
     * classes, so the second caller would otherwise trip
     * [ShapeCatalog.register]'s ordering guard - a guard that is right in
     * production and merely noisy here, since by then the shapes are already
     * in the rotation.
     */
    fun registerAssetShapes() {
        if (registered) return
        registered = true

        val directory = File(ASSET_SHAPES)
        if (!directory.isDirectory) return

        val designs = directory.listFiles { file -> file.name.endsWith(".svg") }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { file ->
                runCatching { SvgShapeFile.parse(file.readText(), file.nameWithoutExtension) }
                    .getOrNull()
            }
        if (designs.isEmpty()) return

        // If some earlier test already read the catalogue, the asset shapes
        // cannot join the rotation in this JVM. Say so rather than failing:
        // a harness that covers 35 of 43 shapes is worth running, as long as
        // nobody mistakes it for one that covers all of them.
        runCatching { ShapeCatalog.register(designs) }.onFailure {
            println("HARNESS asset shapes unavailable: ${it.message}")
        }
    }

    private var registered = false

    /** Where the harness finds the shapes, relative to the module directory. */
    private const val ASSET_SHAPES = "src/main/assets/shapes"

    private fun round(value: Float): String = "%.1f".format(value)

    private fun hash(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
