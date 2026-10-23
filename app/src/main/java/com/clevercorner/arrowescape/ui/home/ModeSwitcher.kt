package com.clevercorner.arrowescape.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.ui.components.ThemedProgressTrack
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * Pick which game to play. A segmented control whose highlight *slides* between
 * the options, so the two modes read as one choice rather than two buttons.
 *
 * Driven entirely by [GameMode.entries]: a third mode would appear here with no
 * changes to this file.
 */
@Composable
fun ModeSwitcher(
    selected: GameMode,
    onSelect: (GameMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val index = GameMode.entries.indexOf(selected).coerceAtLeast(0)

    val indicatorFraction by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "modeIndicator"
    )

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        SlidingSelector(
            count = GameMode.entries.size,
            indicatorFraction = indicatorFraction,
            modifier = Modifier
                .selectableGroup()
                .padding(6.dp),
            indicator = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        ) {
            GameMode.entries.forEach { mode ->
                ModeTab(
                    mode = mode,
                    isSelected = mode == selected,
                    onClick = { onSelect(mode) }
                )
            }
        }
    }
}

@Composable
private fun ModeTab(
    mode: GameMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // The highlight is a solid primary block, so the label must flip to onPrimary.
    val labelColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(220),
        label = "modeLabel"
    )
    val taglineColor = labelColor.copy(alpha = if (isSelected) 0.85f else 0.6f)

    Column(
        modifier = Modifier
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = mode.emoji, fontSize = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(mode.labelRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            maxLines = 1
        )
        Text(
            text = stringResource(mode.taglineRes),
            style = MaterialTheme.typography.labelSmall,
            color = taglineColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * Lays out equal-width [tabs] in a row and draws [indicator] behind the one at
 * [indicatorFraction] - a fractional index, so the caller can animate it.
 *
 * Kept generic and dumb on purpose: it knows about widths, not about modes.
 */
@Composable
private fun SlidingSelector(
    count: Int,
    indicatorFraction: Float,
    indicator: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit
) {
    Layout(
        contents = listOf(indicator, tabs),
        modifier = modifier
    ) { (indicatorMeasurables, tabMeasurables), constraints ->
        val tabWidth = constraints.maxWidth / count.coerceAtLeast(1)
        val tabConstraints = constraints.copy(minWidth = tabWidth, maxWidth = tabWidth)
        val placedTabs = tabMeasurables.map { it.measure(tabConstraints) }
        val height = placedTabs.maxOfOrNull { it.height } ?: 0

        val placedIndicator = indicatorMeasurables.firstOrNull()?.measure(
            Constraints.fixed(tabWidth, height)
        )

        layout(constraints.maxWidth, height) {
            placedIndicator?.place(x = (tabWidth * indicatorFraction).toInt(), y = 0)
            placedTabs.forEachIndexed { i, tab ->
                tab.place(x = tabWidth * i, y = (height - tab.height) / 2)
            }
        }
    }
}

/** Progress through one mode's journey, as an animated bar. */
@Composable
fun JourneyProgressBar(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val target = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "journeyProgress"
    )

    ThemedProgressTrack(fraction = fraction, modifier = modifier, height = 8.dp)
}
