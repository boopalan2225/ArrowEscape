package com.example.arrowescape.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.arrowescape.ui.theme.AppTheme
import com.example.arrowescape.ui.theme.ArrowEscapeTheme

private const val LAST_UPDATED = "October 2025"
private const val CONTACT_EMAIL = "support@arrowescape.app"

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

    DocumentScaffold(title = "Privacy Policy", onBackClick = onBackClick, modifier = modifier) {
        Text(
            text = "Arrow Escape",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Last updated $LAST_UPDATED",
            style = MaterialTheme.typography.bodySmall,
            color = gameColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        DocumentCard(title = "The short version") {
            DocumentParagraph(
                "We do not ask for your name, email or phone number, and we do not " +
                    "sell anything about you. The game runs offline; the only data " +
                    "that leaves your device is what ads and the optional Play Games " +
                    "sign-in need."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "What stays on your device") {
            DocumentBullet("\u25CF", "Current level, best level and stars, for each mode.")
            DocumentBullet("\u25CF", "Your settings: theme, sound and haptics.")
            DocumentParagraph(
                "This lives in the app's private storage and is removed when you " +
                    "uninstall the game."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Advertising") {
            DocumentParagraph(
                "Rewarded and interstitial ads are served by Google AdMob. To show " +
                    "and measure ads, AdMob may process your device's advertising ID, " +
                    "coarse location derived from your IP address, and basic device " +
                    "and app information. You can reset or delete the advertising ID " +
                    "in Android Settings, under Privacy, Ads."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Optional cloud save") {
            DocumentParagraph(
                "If you sign in with Google Play Games, your progress is stored in " +
                    "your own Play Games saved-games space so it follows you to a new " +
                    "device. We see only your Play Games display name and that saved " +
                    "progress. Sign out any time in Settings; playing offline is always " +
                    "an option."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Children") {
            DocumentParagraph(
                "The game is suitable for all ages and collects no personal details. " +
                    "Where required, ad requests are marked as child-directed so only " +
                    "non-personalised ads are served."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Your choices") {
            DocumentBullet("\u25CF", "Reset progress any time from Settings.")
            DocumentBullet("\u25CF", "Sign out of Play Games to stop cloud saves.")
            DocumentBullet("\u25CF", "Uninstall to erase everything stored locally.")
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Who else is involved") {
            DocumentBullet("\u25CF", "Google AdMob - serves and measures the ads.")
            DocumentBullet("\u25CF", "Google Play Games - optional sign-in and cloud save.")
            DocumentBullet("\u25CF", "Google Play - distributes the app and its updates.")
            DocumentParagraph(
                "No other company receives data from this game, and we run no " +
                    "analytics or tracking of our own."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Changes to this policy") {
            DocumentParagraph(
                "If this policy changes, the new version appears here with a fresh " +
                    "date, and significant changes are called out in the release notes."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Contact") {
            DocumentParagraph(
                "Questions, or want your cloud save deleted? Write to $CONTACT_EMAIL " +
                    "and we will answer within 30 days."
            )
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
