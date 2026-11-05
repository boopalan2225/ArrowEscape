package com.clevercorner.arrowescape.game.curve

import android.content.Context
import android.util.Log

/**
 * Loads every SVG file under `assets/shapes` into the curved rotation at
 * process start.
 *
 * This is the one Android-dependent seam in the shape pipeline - reading
 * bytes off disk. [SvgShapeFile] does the actual parsing and stays
 * pure-JVM/testable; this just hands it a string. A bad file is logged and
 * skipped rather than thrown: one broken export should not take the app down
 * before a single level has loaded.
 */
object CurvedAssetShapes {
    private const val TAG = "CurvedAssetShapes"
    private const val DIRECTORY = "shapes"

    fun loadAndRegister(context: Context) {
        val assets = context.assets
        // Sorted explicitly: AssetManager.list()'s order isn't a documented
        // guarantee, and this order becomes part of which level plays which
        // shape. An unstable order would mean the same level could show a
        // different shape on a different device, which is not a cosmetic
        // bug in a seeded generator - it is nondeterminism in the one thing
        // that is supposed to be the same for everyone.
        val files = assets.list(DIRECTORY).orEmpty()
            .filter { it.endsWith(".svg") }
            .sorted()

        val designs = files.mapNotNull { file ->
            runCatching {
                val xml = assets.open("$DIRECTORY/$file").bufferedReader().use { it.readText() }
                SvgShapeFile.parse(xml, file.removeSuffix(".svg"))
            }.onFailure { error ->
                Log.w(TAG, "Skipping $file: ${error.message}")
            }.getOrNull()
        }

        if (designs.isNotEmpty()) ShapeCatalog.register(designs)
    }
}
