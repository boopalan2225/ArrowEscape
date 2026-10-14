package com.example.arrowescape.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arrowescape.cloud.CloudAccount
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * Whether progress is backed up to Google Play, and the one button that
 * changes it. Deliberately small: there is nothing to configure here beyond
 * "connected or not" - the sync itself runs on its own in the background
 * (see `cloud.CloudSyncCoordinator`).
 */
@Composable
fun AccountSection(
    account: CloudAccount?,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val gameColors = ArrowEscapeTheme.gameColors

    SettingsSection(title = "Account") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "\uD83C\uDFAE", fontSize = 28.sp)
            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = if (account != null) account.displayName else "Not connected",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (account != null) {
                        "Progress backs up to Google Play"
                    } else {
                        "Sign in to keep your levels if you reinstall"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = gameColors.textSecondary
                )
            }

            Spacer(Modifier.width(12.dp))

            if (account != null) {
                OutlinedButton(onClick = onSignOutClick, modifier = Modifier.height(36.dp)) {
                    Text("Sign out")
                }
            } else {
                Button(onClick = onSignInClick, modifier = Modifier.height(36.dp)) {
                    Text("Sign in")
                }
            }
        }
    }
}
