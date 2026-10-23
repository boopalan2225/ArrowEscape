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
 * The deal between the player and the game, in words a human can read.
 *
 * Deliberately short: a free puzzle game with no accounts and no purchases
 * does not need a contract longer than the game.
 */
@Composable
fun TermsOfServiceScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors

    DocumentScaffold(title = stringResource(R.string.about_link_terms), onBackClick = onBackClick, modifier = modifier) {
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
            DocumentParagraph(stringResource(R.string.terms_short_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.terms_card_license_title)) {
            DocumentParagraph(stringResource(R.string.terms_license_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.terms_card_not_allowed_title)) {
            DocumentBullet("\u2716", stringResource(R.string.terms_bullet_reverse_engineer))
            DocumentBullet("\u2716", stringResource(R.string.terms_bullet_redistribute))
            DocumentBullet("\u2716", stringResource(R.string.terms_bullet_cheats))
            DocumentBullet("\u2716", stringResource(R.string.terms_bullet_ads))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.terms_card_ads_title)) {
            DocumentParagraph(stringResource(R.string.terms_ads_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.terms_card_progress_title)) {
            DocumentParagraph(stringResource(R.string.terms_progress_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.terms_card_warranty_title)) {
            DocumentParagraph(stringResource(R.string.terms_warranty_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.terms_card_changes_title)) {
            DocumentParagraph(stringResource(R.string.terms_changes_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.doc_contact_title)) {
            DocumentParagraph(stringResource(R.string.terms_contact_body, AppInfo.SUPPORT_EMAIL))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, name = "Terms - Classic")
@Composable
private fun TermsOfServiceScreenPreview() {
    ArrowEscapeTheme(AppTheme.CLASSIC) {
        TermsOfServiceScreen(onBackClick = {})
    }
}
