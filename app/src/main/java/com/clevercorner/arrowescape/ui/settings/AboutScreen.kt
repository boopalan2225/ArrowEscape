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

    DocumentScaffold(title = "About", onBackClick = onBackClick, modifier = modifier) {
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
            text = "Version ${AppInfo.VERSION}",
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

        DocumentCard(title = "The idea") {
            DocumentParagraph(
                "Every arrow wants out. Tap one and it bolts in the direction it " +
                    "points - but only if nothing stands in its way. Clear the board " +
                    "and the shape it was hiding sets itself free."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "How to play") {
            DocumentBullet("\u2191", "An arrow escapes only when its path to the edge is clear.")
            DocumentBullet("\u2716", "Tap a blocked arrow and it costs a life. Three per level.")
            DocumentBullet("\u2605", "Clear fast and clean for three stars.")
            DocumentBullet("\u25CE", "Stuck? Two hints per level point out a safe tap.")
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Two ways to play") {
            DocumentBullet("\u25A6", "Classic: arrows on a grid, shapes drawn in cells.")
            DocumentBullet("\u25CF", "Curved: arrows flow along the lines of a drawn figure.")
            DocumentParagraph(
                "Each mode keeps its own progress, so switching never costs a level."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Make it yours") {
            DocumentParagraph(
                "Five appearances - Classic, Midnight, Sunset, Pirate and Ink - each " +
                    "with its own home screen, motion and sound. Prefer to follow your " +
                    "phone? Pick System in Settings."
            )
        }

        Spacer(Modifier.height(14.dp))

        DocumentCard(title = "Your progress is safe") {
            DocumentParagraph(
                "Everything is stored on your device and plays offline. Sign in with " +
                    "Play Games and your journey rides along to your next phone."
            )
        }

        Spacer(Modifier.height(14.dp))

        // The small print the store asks for, one tap away from the app's
        // own identity card - which is exactly where reviewers look for it.
        DocumentLinkGroup {
            DocumentLinkRow("Terms of Service", onTermsClick)
            DocumentLinkRow("Privacy Policy", onPrivacyClick, showDivider = true)
            DocumentLinkRow("Licenses", onLicensesClick, showDivider = true)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = persona.tagline,
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
