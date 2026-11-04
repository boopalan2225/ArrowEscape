package com.clevercorner.arrowescape.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.economy.CoinRules
import com.clevercorner.arrowescape.economy.Wallet
import com.clevercorner.arrowescape.ui.coins.PriceTag
import com.clevercorner.arrowescape.ui.coins.ThemeUnlockDialog
import com.clevercorner.arrowescape.ui.theme.AppTheme
import com.clevercorner.arrowescape.ui.theme.colorScheme
import com.clevercorner.arrowescape.ui.theme.persona

/** How many cards fit side by side before wrapping to the next row. */
private const val COLUMNS = 3

/**
 * Choose the app's look: one tappable card per [AppTheme], plus "System" for
 * players who would rather the app follow their phone.
 *
 * Every card previews its own colours and mascot, so adding a theme needs no
 * code here - only a palette and a persona. A theme with a price on it shows
 * the price instead of a tick and opens the till rather than selecting, so
 * the shop *is* the picker: a separate "store" screen would mean drawing
 * every theme preview twice.
 */
@Composable
fun ThemePicker(
    selectedId: String?,
    onSelect: (String) -> Unit,
    systemDark: Boolean,
    wallet: Wallet,
    onBuyTheme: suspend (AppTheme) -> Boolean,
    modifier: Modifier = Modifier
) {
    val options = themeOptions(systemDark, wallet)
    var shopping by remember { mutableStateOf<AppTheme?>(null) }
    // One price for whichever locked card you tap: what the *next* unlock
    // costs. Every card shows the same number, which is the honest way to
    // say "your first one is 150" without ranking the artwork.
    val price = CoinRules.unlockPrice(wallet.unlockedThemes.size)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    ThemeOption(
                        option = option,
                        isSelected = option.id == selectedId ||
                            (option.id == AppTheme.SYSTEM_ID && AppTheme.followsSystem(selectedId)),
                        price = price,
                        affordable = wallet.canAfford(price),
                        onClick = {
                            // A locked card is a shop window, not a choice.
                            val locked = option.lockedTheme
                            if (locked == null) onSelect(option.id) else shopping = locked
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keeps the final row's cards the same width as the rows above.
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    shopping?.let { theme ->
        ThemeUnlockDialog(
            theme = theme,
            balance = wallet.balance,
            price = price,
            onBuy = onBuyTheme,
            onBought = {
                shopping = null
                // Bought is worn: nobody spends 220 coins to then go hunting
                // for the card they just paid for.
                onSelect(it.id)
            },
            onDismiss = { shopping = null }
        )
    }
}

/** One card's worth of information - no theme-specific branching downstream. */
private data class ThemeChoice(
    val id: String,
    @androidx.annotation.StringRes val labelRes: Int,
    val crest: String,
    val backdrop: Brush,
    val outline: Color,
    val dots: List<Color>,
    /** The theme this card sells, or null when it is simply selectable. */
    val lockedTheme: AppTheme? = null
)

@Composable
private fun themeOptions(systemDark: Boolean, wallet: Wallet): List<ThemeChoice> {
    val light = AppTheme.CLASSIC.colorScheme()
    val dark = AppTheme.MIDNIGHT.colorScheme()

    // "System" is a rule, not a look, so its card shows both halves of the rule.
    val system = ThemeChoice(
        id = AppTheme.SYSTEM_ID,
        labelRes = R.string.theme_system_label,
        crest = "\u25D1",
        backdrop = Brush.horizontalGradient(
            0f to light.background,
            0.5f to light.background,
            0.5f to dark.background,
            1f to dark.background
        ),
        outline = if (systemDark) dark.outline else light.outline,
        dots = listOf(light.primary, dark.primary, if (systemDark) dark.surface else light.surface)
    )

    return listOf(system) + AppTheme.entries.map { theme ->
        val scheme = theme.colorScheme()
        val owned = theme.isFree || theme.id in wallet.unlockedThemes
        ThemeChoice(
            id = theme.id,
            labelRes = theme.labelRes,
            crest = theme.persona().crest,
            backdrop = Brush.verticalGradient(
                listOf(scheme.background, theme.persona().glow.copy(alpha = 0.18f))
            ),
            outline = scheme.outline,
            dots = listOf(scheme.primary, scheme.secondary, scheme.primaryContainer),
            lockedTheme = theme.takeUnless { owned }
        )
    }
}

@Composable
private fun ThemeOption(
    option: ThemeChoice,
    isSelected: Boolean,
    price: Int,
    affordable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locked = option.lockedTheme != null

    // A small spring on selection: confirmation you can feel, not just read.
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "themeCardScale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        // Constant width, so selecting never nudges the layout - only the colour changes.
        border = BorderStroke(
            width = 2.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier
            .scale(scale)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            )
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The theme in miniature: its own backdrop, mascot and accents.
            // Locked, it is shown faded rather than hidden - you cannot want
            // what you have never seen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(option.backdrop)
                    .border(1.dp, option.outline, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(if (locked) 0.45f else 1f)
                ) {
                    Text(option.crest, fontSize = 14.sp, color = option.dots.first())
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        option.dots.forEach { Dot(it) }
                    }
                }

                if (locked) {
                    PriceTag(
                        price = price,
                        affordable = affordable,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Selection is never colour alone: a tick and a bolder label too.
            val label = stringResource(option.labelRes)
            Text(
                text = when {
                    locked -> "\uD83D\uDD12 $label"
                    isSelected -> "\u2713 $label"
                    else -> label
                },
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
