package com.clevercorner.arrowescape.ui.components

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/**
 * Keeps an arrow visible against the board it is drawn on.
 *
 * Shape art carries its own colours, and some of them are nearly the board's
 * own background: a panda is mostly near-black, and the dark board is
 * `#1A1A1A`, so most of the animal was being drawn in a colour three shades
 * from invisible. The arrows were there and correct - they simply could not
 * be seen, which for a game about spotting which arrow to tap is the same as
 * not being there.
 *
 * Fixing this in the artwork would mean never again using a dark colour for a
 * dark thing, re-doing it for every shape, and re-doing it again for every
 * theme - five of which have *light* boards, where the problem inverts and it
 * is the pale colours that disappear. So it is fixed here instead, at the one
 * point that actually knows what the arrow is being drawn on top of.
 *
 * The threshold is WCAG 2.2's non-text contrast minimum (SC 1.4.11, 3:1 for
 * graphical objects) rather than a number that looked about right, and the
 * lift preserves hue - a dark green becomes a brighter green, never a grey.
 */
object ContrastSafe {

    /** WCAG 2.2 SC 1.4.11: graphical objects need 3:1 against their background. */
    private const val MIN_RATIO = 3f

    /** Bisection steps. Twelve resolves the blend to under 0.03%, far past visible. */
    private const val REFINEMENTS = 12

    /**
     * [this] if it already reads against [background], otherwise the nearest
     * blend toward white (on a dark board) or black (on a light one) that
     * does.
     *
     * Moving toward the far end of the lightness axis rather than toward a
     * fixed colour is what keeps the hue: blending a dark green with white
     * gives a lighter green, so a themed board still has a green belly and a
     * brown wing rather than two indistinguishable pastels.
     */
    fun Color.readableOn(background: Color): Color {
        if (contrastRatio(this, background) >= MIN_RATIO) return this

        // Toward whichever end of the axis is away from the background. A
        // mid-grey board is the awkward case: neither end may reach 3:1, and
        // the bisection below simply returns the best available rather than
        // pretending otherwise.
        val target = if (relativeLuminance(background) < 0.5f) Color.White else Color.Black
        if (contrastRatio(target, background) < MIN_RATIO) return target

        var low = 0f
        var high = 1f
        repeat(REFINEMENTS) {
            val mid = (low + high) / 2f
            if (contrastRatio(blend(this, target, mid), background) >= MIN_RATIO) {
                high = mid
            } else {
                low = mid
            }
        }
        return blend(this, target, high)
    }

    private fun blend(from: Color, to: Color, amount: Float) = Color(
        red = from.red + (to.red - from.red) * amount,
        green = from.green + (to.green - from.green) * amount,
        blue = from.blue + (to.blue - from.blue) * amount,
        alpha = from.alpha
    )

    private fun contrastRatio(a: Color, b: Color): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** WCAG relative luminance: sRGB channels linearised, then weighted. */
    private fun relativeLuminance(color: Color): Float {
        fun linear(channel: Float): Float =
            if (channel <= 0.03928f) channel / 12.92f
            else ((channel + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

        return 0.2126f * linear(color.red) +
            0.7152f * linear(color.green) +
            0.0722f * linear(color.blue)
    }

    /** Exposed for the dev overlay, which reports what it had to lift and by how much. */
    fun ratio(foreground: Color, background: Color): Float = contrastRatio(foreground, background)

    /** True when two colours are close enough that a player would not tell them apart. */
    fun indistinguishable(a: Color, b: Color): Boolean =
        abs(relativeLuminance(a) - relativeLuminance(b)) < 0.02f
}
