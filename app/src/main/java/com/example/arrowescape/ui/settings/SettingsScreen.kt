package com.example.arrowescape.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GamePreferences
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

@Composable
fun SettingsScreen(
    prefs: GamePreferences.Prefs,
    onSoundToggle: (Boolean) -> Unit,
    onHapticsToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
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
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Game Settings Section
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

            Spacer(modifier = Modifier.height(24.dp))

            // About Section
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

            Spacer(modifier = Modifier.height(24.dp))

            // Statistics Section
            SettingsSection(title = "Statistics") {
                StatisticsCard(
                    currentLevel = prefs.currentLevel,
                    highestLevel = prefs.highestLevel,
                    totalCompleted = prefs.totalLevelsCompleted
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Text(
                text = "Made with \u2764 for puzzle lovers",
                style = MaterialTheme.typography.bodySmall,
                color = gameColors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = gameColors.textSecondary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
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

        // Custom toggle switch
        CustomSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun CustomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(200),
        label = "thumbOffset"
    )

    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(200),
        label = "trackColor"
    )

    Box(
        modifier = Modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (24.dp * thumbOffset))
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun SettingsClickableItem(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
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

        // Arrow
        Text(
            text = "\u203A",
            fontSize = 24.sp,
            color = gameColors.textSecondary
        )
    }
}

@Composable
private fun StatisticsCard(
    currentLevel: Int,
    highestLevel: Int,
    totalCompleted: Int
) {
    val gameColors = ArrowEscapeTheme.gameColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                value = "$currentLevel",
                label = "Current",
                color = MaterialTheme.colorScheme.primary
            )
            StatItem(
                value = "$highestLevel",
                label = "Best",
                color = gameColors.success
            )
            StatItem(
                value = "$totalCompleted",
                label = "Completed",
                color = gameColors.arrowRight
            )
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
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

@Preview(showBackground = true, name = "Settings - Light")
@Composable
private fun SettingsScreenPreviewLight() {
    ArrowEscapeTheme(darkTheme = false) {
        SettingsScreen(
            prefs = GamePreferences.Prefs(
                currentLevel = 15,
                highestLevel = 23,
                totalLevelsCompleted = 22,
                soundEnabled = true,
                hapticsEnabled = false
            ),
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
            prefs = GamePreferences.Prefs(
                currentLevel = 42,
                highestLevel = 42,
                totalLevelsCompleted = 41,
                soundEnabled = false,
                hapticsEnabled = true
            ),
            onSoundToggle = {},
            onHapticsToggle = {},
            onBackClick = {}
        )
    }
}
