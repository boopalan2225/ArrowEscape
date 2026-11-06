package com.clevercorner.arrowescape.ui.review

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.clevercorner.arrowescape.ui.components.readableWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.ui.components.GlyphIcon
import com.clevercorner.arrowescape.ui.theme.ArrowEscapeTheme
import kotlinx.coroutines.delay

private const val MAX_STARS = 5

/**
 * "Enjoying Arrow Escape?" - asked properly.
 *
 * **One rule governs this whole screen, and it is not a style choice.**
 * Google Play forbids screening players by sentiment: you may not ask how
 * somebody feels and then send only the happy ones to the store. So the
 * stars here are an *affordance*, not a filter - they are fun to tap, they
 * animate, and they change precisely nothing about where the button goes.
 * Tap one star or five, the same Play listing opens.
 *
 * What *is* allowed, and what this app does, is choosing the **moment**:
 * [com.clevercorner.arrowescape.review.ReviewPolicy] waits for a run of wins
 * before anything is offered. Timing by delight is fair game. Filtering by
 * it is not.
 *
 * Note this screen never opens automatically. The automatic path is still
 * Play's own in-app review sheet (see `ReviewPromptEffect`), because Play
 * also asks that nothing be put in front of it. This is the door a player
 * walks through themselves - from Settings, or from the card on Home.
 */
@Composable
fun RatingScreen(
    onRateClick: () -> Unit,
    onLaterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Nothing is read back out of this. It exists so tapping a star feels
    // like something, which is the entire point of putting stars on a page.
    var picked by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 28.dp)
            .readableWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Mascot()

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.rating_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.rating_body),
            style = MaterialTheme.typography.bodyMedium,
            color = ArrowEscapeTheme.gameColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        StarRow(picked = picked, onPick = { picked = it })

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onRateClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = stringResource(R.string.rating_cta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(4.dp))

        TextButton(onClick = onLaterClick) {
            Text(
                text = stringResource(R.string.rating_later),
                color = ArrowEscapeTheme.gameColors.textSecondary
            )
        }
    }
}

/** The active theme's own crest, bouncing in. Every look says hello its own way. */
@Composable
private fun Mascot() {
    val entry = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { entry.animateTo(1f, tween(620, easing = EaseOutBack)) }

    Box(
        modifier = Modifier
            .size(96.dp)
            .scale(entry.value)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(
            glyph = ArrowEscapeTheme.persona.crest,
            fontSize = 44.sp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Five stars that fill from the left, each landing with its own small
 * overshoot a beat after the one before - a ripple, not a switch.
 */
@Composable
private fun StarRow(picked: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (star in 1..MAX_STARS) {
            val lit = star <= picked
            val pop = remember(star) { Animatable(1f) }

            LaunchedEffect(picked) {
                if (!lit) return@LaunchedEffect
                // Staggered by position, so the row ripples outward from the
                // first star rather than all five flinching at once.
                delay((star - 1) * 70L)
                pop.animateTo(1.35f, tween(140, easing = EaseOutBack))
                pop.animateTo(1f, tween(220))
            }

            GlyphIcon(
                glyph = if (lit) "\u2605" else "\u2606",
                fontSize = 36.sp,
                color = if (lit) MaterialTheme.colorScheme.primary
                else ArrowEscapeTheme.gameColors.textSecondary,
                modifier = Modifier
                    .scale(pop.value)
                    .clip(CircleShape)
                    .clickable { onPick(star) }
                    .padding(2.dp)
            )
        }
    }
}

/**
 * The nudge on Home: shown only while the player is visibly on a roll.
 *
 * A card rather than a popup, because an interruption mid-celebration is a
 * worse read of the room than an invitation sitting quietly on the front
 * door. It is the player who decides to tap it.
 */
@Composable
fun RatingInviteCard(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entry.animateTo(1f, tween(500, easing = EaseOutBack)) }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .scale(0.92f + entry.value * 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Text(
                    text = stringResource(R.string.rating_invite_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.rating_invite_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rating_later))
            }
        }
    }
}
