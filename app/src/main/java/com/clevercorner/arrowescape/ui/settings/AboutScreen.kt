package com.clevercorner.arrowescape.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.clevercorner.arrowescape.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.ui.theme.AppTheme
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme

/** The story of the game, how to play it, and the small print. */
@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onTermsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onLicensesClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gameColors = ArrowEscapeTheme.gameColors
    val persona = ArrowEscapeTheme.persona

    DocumentScaffold(title = stringResource(R.string.about_title), onBackClick = onBackClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_arrow_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = AppInfo.NAME,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.about_version, AppInfo.VERSION),
            style = MaterialTheme.typography.bodySmall,
            color = gameColors.textSecondary
        )
        Text(
            text = AppInfo.COPYRIGHT,
            style = MaterialTheme.typography.bodySmall,
            color = gameColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        DocumentCard(title = stringResource(R.string.about_card_idea_title)) {
            DocumentParagraph(stringResource(R.string.about_card_idea_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.about_card_how_to_play_title)) {
            DocumentBullet("\u2191", stringResource(R.string.about_bullet_escape))
            DocumentBullet("\u2716", stringResource(R.string.about_bullet_blocked))
            DocumentBullet("\u2605", stringResource(R.string.about_bullet_stars))
            DocumentBullet("\u25CE", stringResource(R.string.about_bullet_hints))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.about_card_two_ways_title)) {
            DocumentBullet("\u25A6", stringResource(R.string.about_bullet_classic))
            DocumentBullet("\u25CF", stringResource(R.string.about_bullet_curved))
            DocumentParagraph(stringResource(R.string.about_two_ways_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.about_card_make_it_yours_title)) {
            DocumentParagraph(stringResource(R.string.about_make_it_yours_body))
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = stringResource(R.string.about_card_progress_safe_title)) {
            DocumentParagraph(stringResource(R.string.about_progress_safe_body))
        }

        Spacer(Modifier.height(14.dp))

        // The small print the store asks for, one tap away from the app's
        // own identity card - which is exactly where reviewers look for it.
        DocumentLinkGroup {
            DocumentLinkRow(stringResource(R.string.about_link_terms), onTermsClick)
            DocumentLinkRow(stringResource(R.string.privacy_policy_title), onPrivacyClick, showDivider = true)
            DocumentLinkRow(stringResource(R.string.about_link_licenses), onLicensesClick, showDivider = true)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(persona.taglineRes),
            style = MaterialTheme.typography.labelMedium,
            color = gameColors.textSecondary,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, name = "About - Pirate")
@Composable
private fun AboutScreenPreview() {
    ArrowEscapeTheme(AppTheme.PIRATE) {
        AboutScreen(onBackClick = {})
    }
}
