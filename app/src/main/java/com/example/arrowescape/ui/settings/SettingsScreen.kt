package com.example.arrowescape.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GameMode
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.milestone.MilestoneData
import com.example.arrowescape.ui.theme.AppTheme
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * Global settings. Note what is *not* here: the game mode. That is a choice you
 * make on the way into a game, so it lives on Home, next to the level you are
 * about to play.
 */
@Composable
fun SettingsScreen(
    prefs: GamePreferences.Prefs,
    onSoundToggle: (Boolean) -> Unit,
    onHapticsToggle: (Boolean) -> Unit,
    onDevModeToggle: (Boolean) -> Unit = {},
    onThemeSelect: (AppTheme) -> Unit = {},
    onBackClick: () -> Unit,
    onUnlockAllLevels: (GameMode) -> Unit = {},
    onResetProgress: (GameMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors
    var debugExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SettingsHeader(onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SettingsSection(title = "Appearance") {
                ThemePicker(
                    selected = AppTheme.resolve(prefs.themeId, isSystemInDarkTheme()),
                    onSelect = onThemeSelect
                )
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection(title = "Game") {
                SettingsToggleItem(
                    icon = "\uD83D\uDD0A",
                    title = "Sound Effects",
                    subtitle = "Play sounds when tapping arrows",
                    checked = prefs.soundEnabled,
                    onCheckedChange = onSoundToggle
                )

                SettingsToggleItem(
                    icon = "\uD83D\uDCF3",
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on interactions",
                    checked = prefs.hapticsEnabled,
                    onCheckedChange = onHapticsToggle
                )
            }

            Spacer(Modifier.height(24.dp))

            // One card per mode: progress is per-mode, so the stats must be too.
            SettingsSection(title = "Progress") {
                GameMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) {
                        Divider(color = gameColors.divider)
                    }
                    ModeStatsRow(
                        mode = mode,
                        progress = prefs.progressOf(mode),
                        totalLevels = MilestoneData.forMode(mode).totalLevels,
                        isActive = mode == prefs.mode
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            SettingsSection(title = "About") {
                SettingsClickableItem(
                    icon = "\uD83D\uDCDC",
                    title = "Privacy Policy",
                    subtitle = "View our privacy policy",
                    onClick = { /* TODO: Open privacy policy URL */ }
                )

                SettingsClickableItem(
                    icon = "\u2139\uFE0F",
                    title = "About Arrow Escape",
                    subtitle = "Version 1.0.0",
                    onClick = { /* TODO: Show about dialog */ }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Collapsed by default: destructive tools should take deliberate effort.
            SettingsSection(title = "Debug") {
                ExpanderRow(
                    title = "Developer Tools",
                    subtitle = if (debugExpanded) "Tap to hide" else "Tap to show",
                    expanded = debugExpanded,
                    onToggle = { debugExpanded = !debugExpanded }
                )

                AnimatedVisibility(
                    visible = debugExpanded,
                    enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(180))
                ) {
                    Column {
                        SettingsToggleItem(
                            icon = "\uD83D\uDEE0",
                            title = "Developer Mode",
                            subtitle = "Open any level from Home; play never changes your progress",
                            checked = prefs.devMode,
                            onCheckedChange = onDevModeToggle
                        )
                        GameMode.entries.forEach { mode ->
                            SettingsClickableItem(
                                icon = "\uD83D\uDD13",
                                title = "Unlock All: ${mode.label}",
                                subtitle = "Set ${mode.label} progress to level " +
                                    "${MilestoneData.forMode(mode).totalLevels}",
                                onClick = { onUnlockAllLevels(mode) }
                            )
                            SettingsClickableItem(
                                icon = "\uD83D\uDD04",
                                title = "Reset: ${mode.label}",
                                subtitle = "Start ${mode.label} from level 1",
                                onClick = { onResetProgress(mode) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Made with \u2764 for puzzle lovers",
                style = MaterialTheme.typography.bodySmall,
                color = gameColors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsHeader(onBackClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = "\u2190",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset(y = (-3).dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** A header row whose chevron spins to show what the tap will do. */
@Composable
private fun ExpanderRow(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(220),
        label = "chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = gameColors.textSecondary
            )
        }
        Text(
            text = "\u203A",
            fontSize = 24.sp,
            color = gameColors.textSecondary,
            modifier = Modifier.rotate(chevronTurn)
        )
    }
}

/** One mode's standing: current, best, cleared. */
@Composable
private fun ModeStatsRow(
    mode: GameMode,
    progress: GamePreferences.ModeProgress,
    totalLevels: Int,
    isActive: Boolean
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = mode.emoji, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = mode.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isActive) {
                Spacer(Modifier.width(8.dp))
                Badge(text = "PLAYING")
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("${progress.currentLevel}", "Current", MaterialTheme.colorScheme.primary)
            StatItem("${progress.highestLevel}", "Best", gameColors.success)
            StatItem(
                "${progress.clearedOutOf(totalLevels)}/$totalLevels",
                "Cleared",
                gameColors.arrowRight
            )
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = ArrowEscapeTheme.gameColors.textSecondary
        )
    }
}

// ======================= PREVIEWS =======================

private fun previewPrefs(sound: Boolean, haptics: Boolean) = GamePreferences.Prefs(
    mode = GameMode.CURVED,
    progress = mapOf(
        GameMode.CURVED to GamePreferences.ModeProgress(15, 23, 22),
        GameMode.CLASSIC to GamePreferences.ModeProgress(4, 7, 6)
    ),
    soundEnabled = sound,
    hapticsEnabled = haptics
)

@Preview(showBackground = true, name = "Settings - Light")
@Composable
private fun SettingsScreenPreviewLight() {
    ArrowEscapeTheme(darkTheme = false) {
        SettingsScreen(
            prefs = previewPrefs(sound = true, haptics = false),
            onSoundToggle = {},
            onHapticsToggle = {},
            onBackClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Settings - Dark")
@Composable
private fun SettingsScreenPreviewDark() {
    ArrowEscapeTheme(darkTheme = true) {
        SettingsScreen(
            prefs = previewPrefs(sound = false, haptics = true),
            onSoundToggle = {},
            onHapticsToggle = {},
            onBackClick = {}
        )
    }
}
