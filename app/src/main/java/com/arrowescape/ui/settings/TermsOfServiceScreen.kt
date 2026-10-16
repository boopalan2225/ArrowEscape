package com.arrowescape.ui.settings

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
import com.arrowescape.ui.theme.AppTheme
import com.arrowescape.ui.theme.ArrowEscapeTheme

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

    DocumentScaffold(title = "Terms of Service", onBackClick = onBackClick, modifier = modifier) {
        Text(
            text = AppInfo.NAME,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Last updated ${AppInfo.LEGAL_LAST_UPDATED}",
            style = MaterialTheme.typography.bodySmall,
            color = gameColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        DocumentCard(title = "The short version") {
            DocumentParagraph(
                "Play the game, enjoy it, do not try to break it or resell it. " +
                    "Installing or playing Arrow Escape means you accept these terms."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Your licence to play") {
            DocumentParagraph(
                "We grant you a personal, non-exclusive, non-transferable licence " +
                    "to install and play Arrow Escape on devices you control, for " +
                    "your own entertainment. The game, its levels, artwork, sounds " +
                    "and code stay ours."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "What you agree not to do") {
            DocumentBullet("\u2716", "Reverse engineer, decompile or modify the app.")
            DocumentBullet("\u2716", "Redistribute, sell or sublicense it, in whole or in part.")
            DocumentBullet("\u2716", "Use cheats, bots or tooling that fakes progress or ad views.")
            DocumentBullet("\u2716", "Strip out, hide or interfere with advertising.")
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Ads and third parties") {
            DocumentParagraph(
                "The game is free and paid for by ads. Advertising is served by " +
                    "Google AdMob and governed by their terms; optional cloud save " +
                    "runs on Google Play Games. What they receive is spelled out in " +
                    "the Privacy Policy."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Your progress") {
            DocumentParagraph(
                "Progress is stored on your device, and in your own Play Games " +
                    "saved-games space if you sign in. We do our best to keep it " +
                    "safe, but we cannot guarantee it against device loss, " +
                    "uninstalls or platform changes."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "No warranty") {
            DocumentParagraph(
                "The game is provided \"as is\". To the extent the law allows, we " +
                    "are not liable for lost progress, lost time, or any indirect " +
                    "damages arising from playing it."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Changes and ending the deal") {
            DocumentParagraph(
                "These terms may change; the current version always lives here with " +
                    "a fresh date, and continuing to play means accepting it. You can " +
                    "end this agreement at any time by uninstalling the game."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Contact") {
            DocumentParagraph(
                "Questions about these terms? Write to ${AppInfo.SUPPORT_EMAIL}."
            )
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
