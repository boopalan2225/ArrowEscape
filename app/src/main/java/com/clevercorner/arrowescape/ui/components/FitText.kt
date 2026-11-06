package com.clevercorner.arrowescape.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * A single line of text that shrinks until it fits, instead of being cut in
 * half.
 *
 * Every label in this app is either a translation or a shape name, and both
 * are free to be half again as long as the English the layout was eyeballed
 * against - "Medium \u00B7 Butterfly" is comfortable, "Mittel \u00B7
 * Schmetterling" is not. Compose's default overflow is [TextOverflow.Clip],
 * so the old `maxLines = 1` labels did not ellipsize, they sliced a glyph
 * down the middle. Shrinking a point or two is invisible; a severed letter
 * is the first thing anyone notices.
 *
 * It settles by measuring: draw, ask whether it overflowed, step down, draw
 * again. That costs a couple of frames on first composition and nothing
 * afterwards, and unlike a hand-tuned font size it is still correct on a
 * device nobody tested, at a font scale nobody expected.
 *
 * Below [minScale] it stops and ellipsizes. A label shrunk past three
 * quarters has stopped being readable, and an unreadable label that fits is
 * not an improvement on a clipped one.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    minScale: Float = 0.74f
) {
    // Re-measuring from scratch when the text changes is obvious. Doing it
    // when the screen width changes is the part that matters on a tablet or
    // a foldable: scale only ever shrinks, so without this a label that
    // shrank in portrait would stay shrunk after the device opened up.
    val widthKey = LocalConfiguration.current.screenWidthDp
    var scale by remember(text, widthKey) { mutableFloatStateOf(1f) }

    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        fontSize = style.fontSize * scale,
        // A fitted label is a single line by definition; wrapping it would
        // change the height of whatever row it sits in, which is the thing
        // the caller was avoiding by asking for one line.
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && scale > minScale) scale -= STEP
        }
    )
}

/**
 * Small enough that a step is invisible, big enough to settle in a handful
 * of frames rather than dozens.
 */
private const val STEP = 0.04f
