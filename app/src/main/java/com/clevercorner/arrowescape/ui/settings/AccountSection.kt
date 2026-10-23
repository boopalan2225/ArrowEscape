package com.clevercorner.arrowescape.ui.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.cloud.CloudAccount
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

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

    SettingsSection(title = stringResource(R.string.account_section_title)) {
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
                    text = if (account != null) account.displayName else stringResource(R.string.account_not_connected),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (account != null) R.string.account_backed_up else R.string.account_sign_in_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = gameColors.textSecondary
                )
            }

            Spacer(Modifier.width(12.dp))

            if (account != null) {
                OutlinedButton(onClick = onSignOutClick, modifier = Modifier.height(36.dp)) {
                    Text(stringResource(R.string.account_sign_out))
                }
            } else {
                Button(onClick = onSignInClick, modifier = Modifier.height(36.dp)) {
                    Text(stringResource(R.string.account_sign_in))
                }
            }
        }
    }
}
