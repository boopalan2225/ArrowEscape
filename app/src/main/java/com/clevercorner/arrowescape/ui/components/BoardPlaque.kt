package com.clevercorner.arrowescape.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

/**
 * A theme's painted game board: the artwork, and how much of it is actually
 * playable.
 *
 * The two belong together because they are one physical object. Classic's
 * plaque is a thin gold rail with ivy in the corners, so nearly the whole
 * square is marble; Pirate's is a hat, two lanterns, a ship's wheel and a
 * pile of treasure crowding inwards, so barely three quarters of it is deck.
 * One shared number could only ever be wrong for one of them - either arrows
 * on the pirate's hat, or a postage stamp of marble on the classic.
 *
 * [playArea] is the fraction of the square the puzzle may use, centred. It
 * is the board's four walls at once: arrows are laid out inside it, clipped
 * to it however far the player zooms, and tapped through the same rectangle.
 */
data class BoardPlaque(
    @DrawableRes val art: Int,
    val playArea: Float,
    /**
     * The stone's own colour, which an arrow's soft edge and head halo blend
     * into. Without it every arrow carries a pale glass-coloured outline
     * borrowed from the flat board it no longer sits on - invisible on white
     * marble, a grubby fog on dark slate or wood.
     */
    val halo: Color
)
