package com.clevercorner.arrowescape.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.game.model.Cell
import com.clevercorner.arrowescape.game.model.Deflector
import com.clevercorner.arrowescape.game.model.Direction
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import com.clevercorner.arrowescape.ui.theme.GameColors

/**
 * A chip that opens the board's legend in a dialog.
 *
 * Sits on its own row just under the header (see [BoardLegendRow]) so it
 * never competes with the title or the header's trailing widgets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardLegendButton(mode: GameMode, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }

    AssistChip(
        onClick = { open = true },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primary,
            labelColor = MaterialTheme.colorScheme.onPrimary
        ),
        label = {
            Text(
                text = stringResource(R.string.legend_open_label),
                style = MaterialTheme.typography.labelMedium
            )
        },
        modifier = modifier
    )

    if (open) {
        BoardLegendDialog(mode = mode, onDismiss = { open = false })
    }
}

/** The legend button, right-aligned on its own slim row beneath a header. */
@Composable
fun BoardLegendRow(mode: GameMode, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp, end = 25.dp),
        horizontalArrangement = Arrangement.End
    ) {
        BoardLegendButton(mode)
    }
}

@Composable
private fun BoardLegendDialog(mode: GameMode, onDismiss: () -> Unit) {
    val colors = ArrowEscapeTheme.gameColors
    val ink = MaterialTheme.colorScheme.onSurface
    val entries = when (mode) {
        GameMode.CLASSIC -> classicEntries(colors, ink)
        GameMode.CURVED -> curvedEntries(ink)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.legend_title)) },
        text = { LegendGrid(entries, ink) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.legend_close)) }
        }
    )
}

private class LegendEntry(@StringRes val labelRes: Int, val draw: DrawScope.() -> Unit)

private fun classicEntries(colors: GameColors, ink: Color) = listOf(
    LegendEntry(R.string.legend_arrow) { drawArrowGlyph(ink) },
    LegendEntry(R.string.legend_blocked) { drawArrowGlyph(colors.arrowDown) },
    LegendEntry(R.string.legend_turn_pad) {
        with(BoardFurniture) {
            drawDeflector(Deflector(Cell(0, 0), Direction.RIGHT), center, size.minDimension, colors)
        }
    },
    LegendEntry(R.string.legend_lock_pair) {
        with(BoardFurniture) { drawLockBadge(center, size.minDimension, BoardFurniture.lockColors(0).first) }
    },
    LegendEntry(R.string.legend_sequence) {
        with(BoardFurniture) {
            drawSequenceBadge(center, size.minDimension, key = 2, ready = true, color = ink)
        }
    },
    LegendEntry(R.string.legend_route) { drawRouteDots(colors.arrowHint, colors.arrowDown) },
    LegendEntry(R.string.legend_hint) { drawHintGlow(colors.arrowHint) }
)

private fun curvedEntries(ink: Color) = listOf(
    LegendEntry(R.string.legend_arrow) { drawArrowGlyph(ink) },
    LegendEntry(R.string.legend_blocked) { drawArrowGlyph(BLOCKED_TINT) },
    LegendEntry(R.string.legend_sequence) {
        drawSequencePips(centre = center, radiusPx = size.minDimension * 0.30f, key = 2, ready = true, color = ink)
    },
    LegendEntry(R.string.legend_route) { drawRouteDots(PREVIEW_CLEAR, PREVIEW_BLOCKED) },
    LegendEntry(R.string.legend_hint) { drawHintGlow(ink) }
)

@Composable
private fun LegendGrid(entries: List<LegendEntry>, ink: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                pair.forEach { entry -> LegendCell(entry, ink, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LegendCell(entry: LegendEntry, ink: Color, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(36.dp), onDraw = entry.draw)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(entry.labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = ink
        )
    }
}

/** A short arrow: a tail, a shaft and a solid head - the arrow as the board draws it, in outline. */
private fun DrawScope.drawArrowGlyph(color: Color) {
    val y = size.height / 2f
    val tip = Offset(size.width * 0.80f, y)
    drawLine(
        color = color,
        start = Offset(size.width * 0.18f, y),
        end = tip,
        strokeWidth = size.minDimension * 0.12f,
        cap = StrokeCap.Round
    )
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - size.width * 0.22f, y - size.height * 0.20f)
        lineTo(tip.x - size.width * 0.22f, y + size.height * 0.20f)
        close()
    }
    drawPath(head, color)
}

/** Two rings side by side: green where a route clears, red where it is stopped. */
private fun DrawScope.drawRouteDots(clear: Color, blocked: Color) {
    val r = size.minDimension * 0.16f
    val stroke = Stroke(width = size.minDimension * 0.07f)
    drawCircle(clear, r, center + Offset(-size.width * 0.20f, 0f), style = stroke)
    drawCircle(blocked, r, center + Offset(size.width * 0.20f, 0f), style = stroke)
}

/** The soft glow a hinted arrow wears. */
private fun DrawScope.drawHintGlow(color: Color) {
    drawCircle(color.copy(alpha = 0.25f), size.minDimension * 0.36f, center)
    drawCircle(color, size.minDimension * 0.18f, center, style = Stroke(width = size.minDimension * 0.06f))
}
