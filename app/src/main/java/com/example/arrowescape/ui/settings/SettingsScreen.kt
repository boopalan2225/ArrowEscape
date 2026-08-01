package com.example.arrowescape.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.data.GamePreferences

@Composable
fun SettingsScreen(
    prefs: GamePreferences.Prefs,
    onSoundToggle: (Boolean) -> Unit,
    onHapticsToggle: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackClick) {
                Text("<-", fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "SETTINGS",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Sound toggle
        SettingsToggleRow(
            label = "Sound",
            checked = prefs.soundEnabled,
            onCheckedChange = onSoundToggle
        )

        HorizontalDivider()

        // Haptics toggle
        SettingsToggleRow(
            label = "Haptics",
            checked = prefs.hapticsEnabled,
            onCheckedChange = onHapticsToggle
        )

        HorizontalDivider()

        Spacer(modifier = Modifier.height(32.dp))

        // Privacy Policy (placeholder)
        TextButton(
            onClick = { /* TODO: Open privacy policy URL */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Privacy Policy",
                fontSize = 16.sp
            )
        }

        // About
        TextButton(
            onClick = { /* TODO: Show about dialog */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "About",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Stats
        Text(
            text = "Levels completed: ${prefs.totalLevelsCompleted}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 18.sp
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
