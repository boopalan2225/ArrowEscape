package com.clevercorner.arrowescape.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.ui.theme.AppTheme
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/**
 * What the store requires us to disclose, in plain words: what the game keeps,
 * what the ads see, and what you can do about it.
 */
@Composable
fun PrivacyPolicyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    DocumentScaffold(title = stringResource(R.string.privacy_policy_title), onBackClick = onBackClick, modifier = modifier) {
        Text(
            text = AppInfo.NAME,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.legal_last_updated, AppInfo.LEGAL_LAST_UPDATED),
            style = MaterialTheme.typography.bodySmall,
            color = gameColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        DocumentCard(title = stringResource(R.string.doc_short_version_title)) {
            DocumentParagraph(stringResource(R.string.privacy_short_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.privacy_card_device_title)) {
            DocumentBullet("\u25CF", stringResource(R.string.privacy_bullet_progress))
            DocumentBullet("\u25CF", stringResource(R.string.privacy_bullet_settings))
            DocumentParagraph(stringResource(R.string.privacy_device_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.privacy_card_ads_title)) {
            DocumentParagraph(stringResource(R.string.privacy_ads_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.privacy_card_cloud_title)) {
            DocumentParagraph(stringResource(R.string.privacy_cloud_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.privacy_card_children_title)) {
            DocumentParagraph(stringResource(R.string.privacy_children_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.privacy_card_choices_title)) {
            DocumentBullet("\u25CF", stringResource(R.string.privacy_bullet_reset))
            DocumentBullet("\u25CF", stringResource(R.string.privacy_bullet_signout))
            DocumentBullet("\u25CF", stringResource(R.string.privacy_bullet_uninstall))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.privacy_card_others_title)) {
            DocumentBullet("\u25CF", stringResource(R.string.privacy_bullet_admob))
            DocumentBullet("\u25CF", stringResource(R.string.privacy_bullet_playgames))
            DocumentBullet("\u25CF", stringResource(R.string.privacy_bullet_playstore))
            DocumentParagraph(stringResource(R.string.privacy_others_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.privacy_card_changes_title)) {
            DocumentParagraph(stringResource(R.string.privacy_changes_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.doc_contact_title)) {
            DocumentParagraph(stringResource(R.string.privacy_contact_body, AppInfo.SUPPORT_EMAIL))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, name = "Privacy - Ink")
@Composable
private fun PrivacyPolicyScreenPreview() {
    ArrowEscapeTheme(AppTheme.MONO) {
        PrivacyPolicyScreen(onBackClick = {})
    }
}
