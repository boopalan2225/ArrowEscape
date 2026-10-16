package com.arrowescape.ui.components

import com.arrowescape.game.curve.Point2D

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
        viewSize: Point2D
    ): BoardViewport {
        val newZoom = (zoom * zoomFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val applied = newZoom / zoom
        val anchored = centroid - (centroid - pan) * applied
        return BoardViewport(newZoom, anchored + panDelta).clamped(viewSize)
    }

    /**
     * Keep the board within reach: it may drift [BOUNDARY_FRACTION] of the
     * view past each edge (so edge arrows can be pulled clear of thumbs) but
     * never be flung out of sight.
     */
    fun clamped(viewSize: Point2D): BoardViewport = BoardViewport(
        zoom,
        Point2D(
            clampAxis(pan.x, viewSize.x),
            clampAxis(pan.y, viewSize.y)
        )
    )

    private fun clampAxis(value: Float, view: Float): Float {
        val margin = view * BOUNDARY_FRACTION
        val low = view - view * zoom - margin
        val high = margin
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
