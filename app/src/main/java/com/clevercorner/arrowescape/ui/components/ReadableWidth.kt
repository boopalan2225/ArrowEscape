package com.clevercorner.arrowescape.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fills the width on a phone; on anything wider, stops at a sensible
 * measure and centres.
 *
 * A layout built as "fill the width" is correct right up until the width
 * stops being a phone's. Stretched across a tablet the same column turns
 * into a line of prose sixty words long with the eye losing its place on
 * every return sweep, a settings row whose label and switch are a hand's
 * breadth apart, and a HUD with the hearts in one postcode and the counter
 * in another. None of it is broken, and all of it looks like nobody
 * looked.
 *
 * [READABLE] is a typographic limit rather than a device one - somewhere
 * around sixty to seventy-five characters a line, which is where reading
 * comfort stops improving no matter how much glass you own. That is why
 * this is a plain maximum and not a window size class: the question is how
 * long a line should be, not what kind of device is showing it. Deciding
 * between one pane and two is a genuine device question, and that is where
 * a size class will earn its place.
 *
 * On a phone this is a no-op by construction - the cap is far above any
 * phone's width, so the content fills exactly as it did before.
 */
fun Modifier.readableWidth(max: Dp = READABLE): Modifier = this
    // Claim the full width, centre whatever is inside it, then cap that
    // inner content and let it fill the cap. Capping alone would leave the
    // content pinned to the left edge of a wide screen, which trades one
    // odd-looking layout for another.
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = max)
    .fillMaxWidth()

/** Comfortable measure for a column of text or controls. */
val READABLE: Dp = 600.dp
