package com.arrowescape.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/**
 * A glyph used as an icon, centred on its own box instead of on its font's
 * line box - which is why arrows and crests no longer sit high or left in
 * their circles.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun GlyphIcon(
    glyph: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null
) {
    Text(
        text = glyph,
        fontSize = fontSize,
        lineHeight = fontSize,
        color = color,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        style = LocalTextStyle.current.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            )
        ),
        modifier = modifier
    )
}
