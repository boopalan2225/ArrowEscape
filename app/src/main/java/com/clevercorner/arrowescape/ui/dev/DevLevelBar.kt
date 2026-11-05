package com.clevercorner.arrowescape.ui.dev

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clevercorner.arrowescape.R

/**
 * In-game strip shown only in developer mode: what level/shape/tier this is,
 * and how many arrows it has.
 *
 * Used to also carry its own Prev/Next buttons, stacked right above
 * [com.clevercorner.arrowescape.ui.components.GameBottomBar]'s own
 * prev/next-beside-hint icons - two separate controls doing the same job
 * with different, disagreeing bounds (this bar's against a now-removed
 * catalogue constant, the other against the player's real progress). Dev
 * mode now just widens *that one* control's bound instead of drawing a
 * second one; see the `devMode` check around `onNextLevel` in
 * `GameScreen`/`CurvedGameScreen`.
 */
@Composable
fun DevLevelBar(
    level: Int,
    shapeName: String,
    difficulty: String,
    arrowCount: Int,
    modifier: Modifier = Modifier,
    /** How forced the solution is, 0..1. Null hides the read-out. */
    tightness: Float? = null,
    /** Longest run of moves with exactly one legal option. */
    longestChain: Int = 0
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.dev_level_shape, level, shapeName),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.dev_difficulty_arrows, difficulty, arrowCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        // Generator read-out: the two numbers worth tuning against.
        if (tightness != null) {
            Text(
                text = "tight ${"%.2f".format(tightness)} \u00B7 chain $longestChain",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
