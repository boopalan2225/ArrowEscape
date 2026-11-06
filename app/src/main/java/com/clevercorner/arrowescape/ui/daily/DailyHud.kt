package com.clevercorner.arrowescape.ui.daily

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.clevercorner.arrowescape.ui.components.FitText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.ui.theme.DailyHeaderStyle
import com.clevercorner.arrowescape.ui.theme.LocalThemePersona

/** How tall the header band is, status bar included. */
val DailyHeaderHeight = 250.dp

/**
 * The daily challenge's header: the day, the shape it is built from, and the
 * three numbers worth a glance, floating over the screen's shared backdrop.
 *
 * This no longer paints its own sky - [DailyBackdrop] now covers the whole
 * screen, header included, so the header and the board read as one scene
 * instead of a decorated card sitting above a plain body. The inks still
 * come from the theme, so each look keeps its own readable text.
 *
 * [trailing] mirrors [com.clevercorner.arrowescape.ui.components.GameTopBar]'s
 * own slot of the same name - a fixed-width spot opposite the back button so
 * whatever sits there (the board legend, here) does not throw the date off
 * centre. Empty by default, which is exactly the old behaviour: a bare
 * spacer doing nothing but balancing the back button's width.
 */
@Composable
fun DailyHud(
    dateLabel: String,
    shapeName: String,
    lives: Int,
    bestRun: Int,
    streak: Int,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val style = LocalThemePersona.current.dailyHeader

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DailyHeaderHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Top inset brings the title down off the status bar.
                .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(style = style, onClick = onBackClick)

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.daily_badge).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = style.accent
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = style.ink
                    )
                    if (shapeName.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        ShapeChip(style = style, shapeName = shapeName)
                    }
                }

                // Mirrors the back button so the date sits dead centre -
                // same 48dp either empty (nothing passed) or holding
                // [trailing], so centring never depends on what's inside it.
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    trailing?.invoke()
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DailyStatCard(
                    style = style,
                    glyph = "\u2764\uFE0F",
                    value = lives,
                    label = stringResource(R.string.daily_stat_lives),
                    modifier = Modifier.weight(1f)
                )
                DailyStatCard(
                    style = style,
                    glyph = "\uD83C\uDFC6",
                    value = bestRun,
                    label = stringResource(R.string.daily_stat_best_run),
                    modifier = Modifier.weight(1f)
                )
                DailyStatCard(
                    style = style,
                    glyph = "\uD83D\uDCC5",
                    value = streak,
                    label = stringResource(R.string.daily_stat_streak),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BackButton(style: DailyHeaderStyle, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = style.glass,
        shadowElevation = 2.dp,
        modifier = Modifier.size(48.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = style.ink
            )
        }
    }
}

@Composable
private fun ShapeChip(style: DailyHeaderStyle, shapeName: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = style.glass,
        shadowElevation = 1.dp
    ) {
        Text(
            text = shapeName,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = style.ink
        )
    }
}

/** One glassy card: a glyph, the number, and what the number is. */
@Composable
private fun DailyStatCard(
    style: DailyHeaderStyle,
    glyph: String,
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = style.glass,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = glyph, fontSize = 20.sp)
            Column {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = style.ink
                )
                FitText(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = style.mutedInk
                )
            }
        }
    }
}
