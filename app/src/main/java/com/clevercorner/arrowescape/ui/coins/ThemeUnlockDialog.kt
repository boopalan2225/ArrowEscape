package com.clevercorner.arrowescape.ui.coins

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.ui.components.GlyphIcon
import com.clevercorner.arrowescape.ui.theme.AppTheme
import com.clevercorner.arrowescape.ui.theme.colorScheme
import com.clevercorner.arrowescape.ui.theme.persona
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where a purchase is up to. Three states, and the dialog shows all three. */
private enum class Till { OFFERED, PAYING, BOUGHT }

/**
 * The till: spend coins on a look.
 *
 * Buying is given its own beat on purpose. The balance *counts down* to its
 * new value, the lock falls away and the theme's own colours bloom out of the
 * space it left - because the thing a player just spent a week of daily
 * puzzles on deserves more than a silently swapped checkbox.
 */
@Composable
fun ThemeUnlockDialog(
    theme: AppTheme,
    balance: Int,
    price: Int,
    onBuy: suspend (AppTheme) -> Boolean,
    onBought: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var till by remember { mutableStateOf(Till.OFFERED) }
    val affordable = balance >= price

    // Shown rather than real: it ticks down the moment the player commits,
    // so the cost is felt before the write has even landed.
    val shownBalance by animateIntAsState(
        targetValue = if (till == Till.OFFERED) balance else balance - price,
        animationSpec = tween(600),
        label = "tillBalance"
    )

    // Having bought it, wear it. Lingering on a dialog that says "unlocked"
    // while the app still looks the way it did is an anticlimax.
    LaunchedEffect(till) {
        if (till == Till.BOUGHT) {
            delay(1100)
            onBought(theme)
        }
    }

    AlertDialog(
        onDismissRequest = { if (till == Till.OFFERED) onDismiss() },
        title = {
            Text(
                text = stringResource(
                    if (till == Till.BOUGHT) R.string.theme_unlocked_title else R.string.theme_locked_title,
                    stringResource(theme.labelRes)
                ),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ThemeShowcase(theme = theme, unlocked = till == Till.BOUGHT)

                Spacer(Modifier.height(16.dp))

                AnimatedContent(
                    targetState = till,
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                    label = "tillMessage"
                ) { state ->
                    Text(
                        text = when {
                            state == Till.BOUGHT -> stringResource(R.string.theme_unlocked_body)
                            affordable -> stringResource(R.string.theme_locked_body, price)
                            else -> stringResource(R.string.theme_locked_short, price - balance)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoinIcon(size = 20.dp, flipping = till != Till.OFFERED)
                    Text(
                        text = "$shownBalance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            if (till == Till.BOUGHT) return@AlertDialog
            Button(
                onClick = {
                    till = Till.PAYING
                    scope.launch {
                        till = if (onBuy(theme)) Till.BOUGHT else Till.OFFERED
                    }
                },
                enabled = affordable && till == Till.OFFERED
            ) {
                Text(
                    text = stringResource(R.string.theme_unlock_button, price),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (till == Till.OFFERED) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/**
 * The theme, in miniature, with its lock on top until it is not.
 *
 * The same swatch the picker draws, scaled up - one idea of "what a theme
 * looks like in preview", drawn twice at different sizes rather than two
 * previews that could drift apart.
 */
@Composable
private fun ThemeShowcase(theme: AppTheme, unlocked: Boolean) {
    val scheme = theme.colorScheme()
    val persona = theme.persona()

    // The lock does not fade: it drops away and the crest springs up through
    // the hole it leaves.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(unlocked) {
        if (unlocked) reveal.animateTo(1f, tween(620, easing = EaseOutBack))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(scheme.background, persona.glow.copy(alpha = 0.3f)))
            )
            .border(2.dp, scheme.outline, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(scheme.primary, scheme.secondary, scheme.primaryContainer).forEach { swatch ->
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(swatch)
                )
            }
        }

        GlyphIcon(
            glyph = if (unlocked) persona.crest else "\uD83D\uDD12",
            fontSize = 30.sp,
            color = scheme.primary,
            modifier = Modifier
                .scale(if (unlocked) 0.6f + reveal.value * 0.6f else 1f)
        )
    }
}

/**
 * A price tag for a locked card. Small, gold, and unmistakably a cost rather
 * than a badge of honour.
 */
@Composable
fun PriceTag(price: Int, affordable: Boolean, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(320, easing = EaseOutBack)) }

    Row(
        modifier = modifier
            .scale(pop.value)
            .clip(RoundedCornerShape(50))
            .background(
                if (affordable) CoinArt.Face.copy(alpha = 0.92f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        CoinIcon(size = 10.dp)
        Text(
            text = "$price",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (affordable) CoinArt.Rim else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
