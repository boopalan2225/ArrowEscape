package com.clevercorner.arrowescape.ui.components

import com.clevercorner.arrowescape.game.curve.Point2D

/**
 * The slab of canvas a board's content lives on, and must stay reachable
 * within.
 *
 * Most boards fill their whole canvas, so [origin] is zero and [size] is the
 * view. The daily's plaque paints its own four walls, so its play area is
 * inset inside them - and nothing may be panned or zoomed out past them,
 * hence [slack] of zero there.
 */
data class BoardBounds(
    val size: Point2D,
    val origin: Point2D = Point2D.ZERO,
    /** How far past each edge the board may drift, as a fraction of [size]. */
    val slack: Float = BoardViewport.BOUNDARY_FRACTION
)

/**
 * Pan / pinch-zoom state for the game board, as pure math.
 *
 * Mirrors the Flutter game's `InteractiveViewer` (min 0.8x, max 4x, a little
 * slack past the edges) but lives apart from Compose so it can be unit-tested
 * without a device.
 *
 * Coordinates: "canvas" space is the un-zoomed board in pixels; "screen" space
 * is what the finger touches.  `screen = pan + zoom * canvas`.
 */
data class BoardViewport(
    val zoom: Float = 1f,
    val pan: Point2D = Point2D.ZERO
) {

    /** Screen point -> canvas point. Used to hit-test taps while zoomed. */
    fun toCanvas(screen: Point2D): Point2D = (screen - pan) / zoom

    /** Canvas point -> screen point. */
    fun toScreen(canvas: Point2D): Point2D = canvas * zoom + pan

    /**
     * Apply one pinch/drag gesture step.
     *
     * The canvas point under [centroid] stays under the fingers while zooming,
     * then [panDelta] slides the board along with them.
     */
    fun transformedBy(
        centroid: Point2D,
        panDelta: Point2D,
        zoomFactor: Float,
        bounds: BoardBounds
    ): BoardViewport {
        val newZoom = (zoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = newZoom / zoom
        val anchored = centroid - (centroid - pan) * applied
        return BoardViewport(newZoom, anchored + panDelta).clamped(bounds)
    }

    /**
     * Keep the board within reach: it may drift [BoardBounds.slack] of the
     * play area past each edge (so edge arrows can be pulled clear of thumbs)
     * but never be flung out of sight.
     */
    fun clamped(bounds: BoardBounds): BoardViewport = BoardViewport(
        zoom,
        Point2D(
            clampAxis(pan.x, bounds.origin.x, bounds.size.x, bounds.slack),
            clampAxis(pan.y, bounds.origin.y, bounds.size.y, bounds.slack)
        )
    )

    /**
     * Pan allowed on one axis for content spanning canvas `origin..origin+length`,
     * which must keep covering that same span on screen, give or take [slack].
     */
    private fun clampAxis(value: Float, origin: Float, length: Float, slack: Float): Float {
        val margin = length * slack
        val low = (origin + length) * (1f - zoom) - margin
        val high = origin * (1f - zoom) + margin
        // Zoomed far out the range can invert; centre instead of fighting it.
        return if (low > high) (low + high) / 2f else value.coerceIn(low, high)
    }

    val isIdentity: Boolean get() = zoom == 1f && pan == Point2D.ZERO

    companion object {
        const val MIN_ZOOM = 0.8f
        const val MAX_ZOOM = 4f
        const val BOUNDARY_FRACTION = 0.2f

        val RESET = BoardViewport()

        /**
         * Tap tolerance in canvas units that stays roughly one fingertip wide
         * on screen no matter how far the player has zoomed in.
         */
        fun tapRadius(baseRadius: Float, zoom: Float): Float =
            (baseRadius / zoom).coerceAtLeast(baseRadius * MIN_TAP_SHRINK)

        private const val MIN_TAP_SHRINK = 0.25f
    }
}

/**
 * Remembers whether the current press already turned into a preview, so the
 * release does not also fire the arrow.
 *
 * A plain mutable holder rather than Compose state on purpose: this is read
 * and written inside a long-lived gesture coroutine, where a recomposition
 * would otherwise lose the flag mid-press. Shared by both boards.
 */
internal class PressTracker {
    var previewShown: Boolean = false
}
