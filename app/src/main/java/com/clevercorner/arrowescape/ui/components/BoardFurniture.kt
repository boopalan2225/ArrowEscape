package com.clevercorner.arrowescape.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.clevercorner.arrowescape.game.model.Deflector
import com.clevercorner.arrowescape.game.model.Direction
import com.clevercorner.arrowescape.ui.theme.GameColors

/**
 * The furniture that sits on the board next to the arrows: redirect dots and
 * the colour coding for locked pairs.
 *
 * Lives apart from [GameBoard] because the board file is already carrying the
 * gesture plumbing; a dot is its own small idea.
 */
object BoardFurniture {

    /**
     * Hues reserved for locked pairs, deliberately independent of the theme.
     *
     * Pair colour used to be borrowed from the theme's four directional arrow
     * hues, which quietly broke the mechanic in Ink: that palette is
     * monochrome on purpose, so every pair came out a slightly different grey
     * and there was no way to tell which two belonged together.
     *
     * Owning the palette fixes that everywhere at once, and in Ink it turns
     * into a feature - locked pairs become the *only* colour on an otherwise
     * black-and-white board, which is about as readable as a huddle can get.
     *
     * Four hues that stay distinct from each other on both the light paper
     * boards and the daily's dark slate, and that do not rely on separating
     * red from green.
     */
    private val LockPalette = listOf(
        Color(0xFFF08A24), // amber
        Color(0xFF14A89D), // teal
        Color(0xFF9A5CE0), // violet
        Color(0xFFE0457B)  // rose
    )

    /**
     * Colour for a locked pair. Both halves of a group get the same hue,
     * which is the entire tutorial the mechanic needs - matching colours
     * leave together.
     */
    fun lockColors(group: Int): Pair<Color, Color> {
        val base = LockPalette[group.mod(LockPalette.size)]
        return base to base.copy(alpha = 0.72f)
    }

    /**
     * Draws a redirect dot as a *turn pad*: a tile with a curved arrow that
     * sweeps into the outgoing direction, plus a short fading lane showing
     * where the next arrow will be sent.
     *
     * The first version of this was a plain chevron, which read as "something
     * points that way" and taught nobody anything. A U-turn glyph with a
     * visible exit lane says the one thing that matters: arrows arriving here
     * leave THAT way.
     */
    fun DrawScope.drawDeflector(
        deflector: Deflector,
        center: Offset,
        cell: Float,
        colors: GameColors
    ) {
        val tint = colors.arrowHint
        val out = unitVector(deflector.direction)
        val perp = Offset(-out.y, out.x)

        drawExitLane(center, out, cell, tint)

        // Tile. Rounded enough to read as furniture rather than as an arrow.
        val half = cell * 0.34f
        drawRoundRect(
            color = tint.copy(alpha = 0.16f),
            topLeft = center - Offset(half, half),
            size = Size(half * 2, half * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.14f)
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.55f),
            topLeft = center - Offset(half, half),
            size = Size(half * 2, half * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.14f),
            style = Stroke(width = cell * 0.045f)
        )

        drawTurnGlyph(center, out, perp, cell, tint)
    }

    /**
     * A quarter-turn: in from the side, bending out along the dot's direction,
     * finished with a solid head so the exit end is unmistakable.
     */
    private fun DrawScope.drawTurnGlyph(
        center: Offset,
        out: Offset,
        perp: Offset,
        cell: Float,
        tint: Color
    ) {
        val reach = cell * 0.20f
        val entry = center - perp * reach - out * reach
        val corner = center - out * reach * 0.2f + perp * 0f
        val exit = center + out * reach

        drawPath(
            Path().apply {
                moveTo(entry.x, entry.y)
                quadraticBezierTo(corner.x, corner.y, exit.x, exit.y)
            },
            tint,
            style = Stroke(
                width = cell * 0.085f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val tip = exit + out * (cell * 0.12f)
        val back = tip - out * (cell * 0.16f)
        drawPath(
            Path().apply {
                moveTo(tip.x, tip.y)
                val left = back + perp * (cell * 0.11f)
                val right = back - perp * (cell * 0.11f)
                lineTo(left.x, left.y)
                lineTo(right.x, right.y)
                close()
            },
            tint,
            style = Fill
        )
    }

    /** A short gradient streak out of the tile: "this way out". */
    private fun DrawScope.drawExitLane(
        center: Offset,
        out: Offset,
        cell: Float,
        tint: Color
    ) {
        val start = center + out * (cell * 0.34f)
        val end = center + out * (cell * 1.1f)
        drawLine(
            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(tint.copy(alpha = 0.40f), Color.Transparent),
                start = start,
                end = end
            ),
            start = start,
            end = end,
            strokeWidth = cell * 0.22f,
            cap = StrokeCap.Round
        )
    }

    /**
     * A ring marking the two halves of a locked pair as one move, with a link
     * notch so the pairing survives a colour-blind player.
     */
    fun DrawScope.drawLockBadge(center: Offset, cell: Float, color: Color) {
        drawCircle(
            color.copy(alpha = 0.85f),
            cell * 0.30f,
            center,
            style = Stroke(
                width = cell * 0.07f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = center - Offset(cell * 0.16f, cell * 0.16f),
            size = Size(cell * 0.32f, cell * 0.32f),
            style = Stroke(width = cell * 0.08f, cap = StrokeCap.Round)
        )
    }

    /**
     * The number on a sequenced arrow, and whether it is this arrow's turn.
     *
     * Drawn as a disc with pips rather than a numeral: this is a Canvas with
     * no text measurer to hand, and at a third of a cell on a phone a drawn
     * digit would be a smudge. Pips read instantly at small sizes, which is
     * the entire job.
     *
     * [ready] is the whole fairness argument. An arrow still waiting its turn
     * is dim and thin-rimmed; the one that may go now is solid and bright.
     * Without that difference the player taps a clear lane, nothing happens,
     * and the game looks broken rather than demanding.
     */
    fun DrawScope.drawSequenceBadge(
        center: Offset,
        cell: Float,
        key: Int,
        ready: Boolean,
        color: Color
    ) {
        val radius = cell * 0.30f
        val alpha = if (ready) 1f else 0.45f

        drawCircle(Color.Black.copy(alpha = 0.55f * alpha), radius, center)
        drawCircle(
            color.copy(alpha = alpha),
            radius,
            center,
            style = Stroke(width = cell * (if (ready) 0.09f else 0.06f))
        )

        // Pips on a ring, clockwise from the top, so counting them is a
        // glance rather than a puzzle of its own.
        val pipRadius = cell * 0.055f
        val ring = radius * 0.52f
        val count = key.coerceIn(1, 8)
        for (i in 0 until count) {
            val angle = -Math.PI / 2 + 2 * Math.PI * i / count
            val at = center + Offset(
                (Math.cos(angle) * ring).toFloat(),
                (Math.sin(angle) * ring).toFloat()
            )
            drawCircle(color.copy(alpha = alpha), pipRadius, at)
        }
    }

    fun unitVector(direction: Direction): Offset = when (direction) {
        Direction.UP -> Offset(0f, -1f)
        Direction.DOWN -> Offset(0f, 1f)
        Direction.LEFT -> Offset(-1f, 0f)
        Direction.RIGHT -> Offset(1f, 0f)
    }
}
