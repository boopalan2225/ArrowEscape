package com.clevercorner.arrowescape.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * The look of the board surface, which is how a player tells the three modes
 * apart at a glance.
 *
 * The three boards used to be the same rectangle in the same colour, so the
 * only thing saying which mode you were in was a word in the header. Giving
 * each one its own surface means the mode is legible from the board itself -
 * and it costs one enum rather than three divergent copies of the board.
 *
 * Deliberately only the *backdrop* varies. Arrow rendering, hit-testing and
 * layout stay identical across modes, because those are the rules of the game
 * and a skin has no business touching them.
 */
enum class BoardSkin {
    /** Journey, square grid: a quiet paper surface under a dot grid. */
    CLASSIC,

    /** Journey, curved: a soft centre glow, so the art floats rather than sits. */
    CURVED,

    /** Daily: a dark slate. Unmistakably not the journey. */
    DAILY
}

/** Resolved colours for a skin, read once per board. */
data class BoardSkinColors(
    /** Fill behind everything. */
    val surface: Color,
    /** Texture drawn onto the surface - grid dots or a pool of light. */
    val texture: Color,
    /** Colour an arrow's anti-aliasing halo should blend into. */
    val halo: Color
)

@Composable
fun BoardSkin.colors(): BoardSkinColors {
    val game = ArrowEscapeTheme.gameColors
    return when (this) {
        BoardSkin.CLASSIC -> BoardSkinColors(
            surface = game.boardBackground,
            texture = game.gridLine,
            halo = game.boardBackground
        )

        BoardSkin.CURVED -> BoardSkinColors(
            surface = game.boardBackground,
            // A hint of the arrow hue, so the glow belongs to the artwork
            // rather than looking like a smudge on the glass.
            texture = game.arrowPrimary.copy(alpha = 0.10f),
            halo = game.boardBackground
        )

        BoardSkin.DAILY -> {
            // Not pure black: a slate that still carries a trace of the
            // active theme, so switching persona is still visible here.
            val slate = lerp(Color(0xFF161A22), game.arrowPrimary, 0.06f)
            BoardSkinColors(
                surface = slate,
                texture = Color.White.copy(alpha = 0.05f),
                halo = slate
            )
        }
    }
}

/**
 * Paints the mode's texture across the whole board, under the arrows.
 *
 * [cell], [offsetX] and [offsetY] describe the classic grid and are ignored
 * by the skins that have no grid - curved and daily are laid out as free
 * artwork, so there is nothing to line a dot up with.
 */
fun DrawScope.drawBoardBackdrop(
    skin: BoardSkin,
    colors: BoardSkinColors,
    rows: Int = 0,
    cols: Int = 0,
    cell: Float = 0f,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    when (skin) {
        BoardSkin.CLASSIC ->
            if (cell > 0f) drawDotGrid(rows, cols, cell, offsetX, offsetY, colors.texture)

        BoardSkin.CURVED -> drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.texture, Color.Transparent),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.minDimension * 0.62f
            ),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width / 2f, size.height / 2f)
        )

        // A soft pool of light over the middle of the slate, which pulls the
        // eye to the tangle in the centre where the puzzle actually is.
        BoardSkin.DAILY -> drawRect(
            brush = Brush.radialGradient(
                colors = listOf(colors.texture, Color.Transparent),
                center = Offset(size.width / 2f, size.height * 0.45f),
                radius = size.maxDimension * 0.70f
            ),
            size = Size(size.width, size.height)
        )
    }
}
