package com.example.arrowescape.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.ui.theme.AppTheme
import com.example.arrowescape.ui.theme.colorScheme

/**
 * Choose the app's look. One tappable card per [AppTheme], each previewing its
 * own colours - so it works for any number of themes with no per-theme code.
 */
@Composable
fun ThemePicker(
    selected: AppTheme,
    onSelect: (AppTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppTheme.entries.forEach { theme ->
            ThemeOption(
                theme = theme,
                isSelected = theme == selected,
                onClick = { onSelect(theme) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ThemeOption(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preview = theme.colorScheme()
    val shape = RoundedCornerShape(14.dp)

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        // Constant width, so selecting never nudges the layout - only the colour changes.
        border = BorderStroke(
            width = 2.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.selectable(
            selected = isSelected,
            onClick = onClick,
            role = Role.RadioButton
        )
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The theme in miniature: its own background with its main colours.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(preview.background)
                    .border(1.dp, preview.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Dot(preview.primary)
                    Dot(preview.secondary)
                    Dot(preview.primaryContainer)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Selection is never colour alone: a tick and a bolder label too.
            Text(
                text = if (isSelected) "\u2713 ${theme.label}" else theme.label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    )
}
