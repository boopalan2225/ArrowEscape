package com.clevercorner.arrowescape.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.BuildConfig
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.cloud.CloudAccount
import com.clevercorner.arrowescape.data.GameMode
import com.clevercorner.arrowescape.data.GamePreferences
import com.clevercorner.arrowescape.ui.components.GlyphIcon
import com.clevercorner.arrowescape.ui.milestone.MilestoneData
import com.clevercorner.arrowescape.ui.theme.AppTheme
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * Global settings. Note what is *not* here: the game mode. That is a choice you
 * make on the way into a game, so it lives on Home, next to the level you are
 * about to play.
 */
@Composable
fun SettingsScreen(
    prefs: GamePreferences.Prefs,
    account: CloudAccount? = null,
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onSoundToggle: (Boolean) -> Unit,
    onHapticsToggle: (Boolean) -> Unit,
    onDevModeToggle: (Boolean) -> Unit = {},
    /** Receives an [AppTheme] id, or [AppTheme.SYSTEM_ID] to follow the phone. */
    onThemeSelect: (String) -> Unit = {},
    onAboutClick: () -> Unit = {},
    onHowToPlayClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
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
            SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
                ThemePicker(
                    selectedId = prefs.themeId,
                    onSelect = onThemeSelect,
                    systemDark = isSystemInDarkTheme()
                )
            }

            Spacer(Modifier.height(24.dp))

            AccountSection(
                account = account,
                onSignInClick = onSignInClick,
                onSignOutClick = onSignOutClick
            )

            Spacer(Modifier.height(24.dp))

            SettingsSection(title = stringResource(R.string.settings_section_game)) {
                SettingsToggleItem(
                    icon = "\uD83D\uDD0A",
                    title = stringResource(R.string.settings_toggle_sound_title),
                    subtitle = stringResource(R.string.settings_toggle_sound_subtitle),
                    checked = prefs.soundEnabled,
                    onCheckedChange = onSoundToggle
                )

                SettingsToggleItem(
                    icon = "\uD83D\uDCF3",
                    title = stringResource(R.string.settings_toggle_haptics_title),
                    subtitle = stringResource(R.string.settings_toggle_haptics_subtitle),
                    checked = prefs.hapticsEnabled,
                    onCheckedChange = onHapticsToggle
                )
            }

            Spacer(Modifier.height(24.dp))

            // One card per mode: progress is per-mode, so the stats must be too.
            SettingsSection(title = stringResource(R.string.settings_section_progress)) {
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

            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsClickableItem(
                    icon = "\uD83C\uDFAF",
                    title = stringResource(R.string.settings_item_how_to_play_title),
                    subtitle = stringResource(R.string.settings_item_how_to_play_subtitle),
                    onClick = onHowToPlayClick
                )

                SettingsClickableItem(
                    icon = "\uD83D\uDCDC",
                    title = stringResource(R.string.privacy_policy_title),
                    subtitle = stringResource(R.string.settings_item_privacy_subtitle),
                    onClick = onPrivacyClick
                )

                SettingsClickableItem(
                    icon = "\u2139\uFE0F",
                    title = stringResource(R.string.settings_item_about_title),
                    subtitle = stringResource(R.string.settings_item_about_subtitle),
                    onClick = onAboutClick
                )
            }

            Spacer(Modifier.height(24.dp))

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(24.dp))

                // Collapsed by default: destructive tools should take deliberate effort.
                SettingsSection(title = stringResource(R.string.settings_section_debug)) {
                    ExpanderRow(
                        title = stringResource(R.string.settings_debug_tools_title),
                        subtitle = stringResource(
                            if (debugExpanded) R.string.settings_debug_tap_to_hide else R.string.settings_debug_tap_to_show
                        ),
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
                                title = stringResource(R.string.settings_debug_dev_mode_title),
                                subtitle = stringResource(R.string.settings_debug_dev_mode_subtitle),
                                checked = prefs.devMode,
                                onCheckedChange = onDevModeToggle
                            )
                            GameMode.entries.forEach { mode ->
                                val modeLabel = stringResource(mode.labelRes)
                                SettingsClickableItem(
                                    icon = "\uD83D\uDD13",
                                    title = stringResource(R.string.settings_debug_unlock_all_title, modeLabel),
                                    subtitle = stringResource(
                                        R.string.settings_debug_unlock_all_subtitle,
                                        modeLabel,
                                        MilestoneData.forMode(mode).totalLevels
                                    ),
                                    onClick = { onUnlockAllLevels(mode) }
                                )
                                SettingsClickableItem(
                                    icon = "\uD83D\uDD04",
                                    title = stringResource(R.string.settings_debug_reset_title, modeLabel),
                                    subtitle = stringResource(R.string.settings_debug_reset_subtitle, modeLabel),
                                    onClick = { onResetProgress(mode) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = stringResource(R.string.settings_footer),
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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.settings_label),
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
                text = stringResource(mode.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isActive) {
                Spacer(Modifier.width(8.dp))
                Badge(text = stringResource(R.string.settings_badge_playing))
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("${progress.currentLevel}", stringResource(R.string.settings_stat_current), MaterialTheme.colorScheme.primary)
            StatItem("${progress.bestOutOf(totalLevels)}", stringResource(R.string.settings_stat_best), gameColors.success)
            StatItem(
                "${progress.clearedOutOf(totalLevels)}/$totalLevels",
                stringResource(R.string.settings_stat_cleared),
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
