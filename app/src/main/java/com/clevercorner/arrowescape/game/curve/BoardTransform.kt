package com.clevercorner.arrowescape.game.curve

import kotlin.random.Random

/**
 * A rigid reshuffle of the whole board: mirror or rotate every arrow at
 * once, so the maze visibly relocates instead of only its arrowheads.
 *
 * Why a rigid transform and not, say, regenerating fresh curves: it is the
 * only move that is both dramatic AND free. Mirroring or rotating the entire
 * scene is an isometry - every arrow keeps its exact shape and length, and
 * every pairwise relationship between arrows (who blocks whom) survives
 * unchanged, because the SAME map is applied to everything at once. That
 * means a solved orientation stays solved after being transformed, so the
 * daily shuffle gets a visibly different board without paying for a second
 * solver pass just to prove the transform didn't break anything - the
 * existing orientation climb (which still runs afterwards, for per-arrow
 * direction variety) stays the only thing doing real search.
 *
 * Regenerating brand-new curves was the other option on the table and was
 * rejected: [CurveGenerator] packs as many arrows as fit per region rather
 * than a target count, so a fresh pack could not promise the exact arrow
 * count the daily shuffle has to preserve.
 */
internal enum class BoardTransform {
    MIRROR_X,
    MIRROR_Y,
    ROTATE_180;

    fun mapPoint(width: Float, height: Float): (Point2D) -> Point2D = when (this) {
        MIRROR_X -> { p -> Point2D(width - p.x, p.y) }
        MIRROR_Y -> { p -> Point2D(p.x, height - p.y) }
        ROTATE_180 -> { p -> Point2D(width - p.x, height - p.y) }
    }

    companion object {
        fun random(random: Random): BoardTransform = entries.random(random)
    }
}

/** Applies [transform] to every arrow in the list, in place of a map call. */
internal fun List<CurvedArrow>.transformedBy(
    transform: BoardTransform,
    width: Float,
    height: Float
): List<CurvedArrow> {
    val mapPoint = transform.mapPoint(width, height)
    return map { it.transformed(mapPoint) }
}

/**
 * The same outline, mirrored or turned about the board.
 *
 * Transforming the *outline* rather than the finished arrows is what keeps a
 * level internally consistent: everything downstream - the pack, the escape
 * rays, the region shapes handed to the renderer - is then derived from one
 * piece of geometry instead of being patched up afterwards.
 */
internal fun ShapePath.transformedBy(
    transform: BoardTransform,
    width: Float,
    height: Float
): ShapePath {
    val mapPoint = transform.mapPoint(width, height)
    return copy(
        commands = commands.map { command ->
            when (command) {
                is PathCommand.MoveTo -> PathCommand.MoveTo(mapPoint(command.point))
                is PathCommand.LineTo -> PathCommand.LineTo(mapPoint(command.point))
                is PathCommand.QuadTo -> PathCommand.QuadTo(
                    mapPoint(command.control),
                    mapPoint(command.end)
                )
                is PathCommand.CurveTo -> PathCommand.CurveTo(
                    mapPoint(command.control1),
                    mapPoint(command.control2),
                    mapPoint(command.end)
                )
                // No points to move.
                PathCommand.Close -> command
            }
        }
    )
}
