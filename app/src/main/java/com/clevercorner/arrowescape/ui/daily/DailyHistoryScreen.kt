package com.clevercorner.arrowescape.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clevercorner.arrowescape.R
import com.clevercorner.arrowescape.game.daily.DailyProgress
import com.clevercorner.arrowescape.game.daily.DailyStatus
import com.clevercorner.arrowescape.ui.theme.DailyHeaderStyle
import com.clevercorner.arrowescape.ui.theme.LocalThemePersona
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d")

/** Seven columns, because weeks have seven days and a grid is the point. */
private const val WEEK_LENGTH = 7

/**
 * The daily challenge, once today's is spent: a month at a glance.
 *
 * Deliberately not playable. A daily you can replay is not a daily, and a
 * missed day you can go back and mop up is what makes a streak meaningless -
 * so tapping a date here only ever *tells* you something. The screen answers
 * three questions and stops: which days did I solve, how well, and when may
 * I play again.
 */
@Composable
fun DailyHistoryScreen(
    progress: DailyProgress,
    today: LocalDate,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = LocalThemePersona.current.dailyHeader
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }

    Box(modifier = modifier.fillMaxSize()) {
        DailyBackdrop(modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // The way out stays pinned to the top where a back control
            // belongs; only the record itself is centred.
            DailyBackBar(
                onBackClick = onBackClick,
                style = style,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Centred as one block. Top-aligned, the month floated under
                // the header with a lake of empty scene beneath it; the
                // record is the point of this screen, so it sits in the
                // middle of it.
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
            MonthHeader(
                month = month,
                // Tomorrow's puzzle is not yours yet, and neither is its month.
                canGoForward = month < YearMonth.from(today),
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                style = style
            )

            MonthGrid(
                month = month,
                today = today,
                progress = progress,
                selected = selected,
                onSelect = { selected = it },
                style = style
            )

            SelectedDayCard(date = selected, today = today, progress = progress, style = style)

            ComeBackBanner(streak = progress.streak, solved = progress.cleared.size, style = style)
            }
        }
    }
}

@Composable
private fun DailyBackBar(onBackClick: () -> Unit, style: DailyHeaderStyle, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBackClick,
            shape = CircleShape,
            color = style.glass,
            shadowElevation = 2.dp
        ) {
            Text(
                text = "\u2190",
                style = MaterialTheme.typography.titleLarge,
                color = style.ink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        Text(
            text = stringResource(R.string.daily_history_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = style.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(end = 56.dp)
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    style: DailyHeaderStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MonthStep(glyph = "\u2039", enabled = true, onClick = onPrevious, style = style)
        Text(
            text = month.atDay(1).format(MONTH_FORMAT),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = style.ink
        )
        MonthStep(glyph = "\u203A", enabled = canGoForward, onClick = onNext, style = style)
    }
}

@Composable
private fun MonthStep(glyph: String, enabled: Boolean, onClick: () -> Unit, style: DailyHeaderStyle) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (enabled) style.glass else Color.Transparent,
        shadowElevation = if (enabled) 2.dp else 0.dp
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) style.ink else style.mutedInk.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    progress: DailyProgress,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    style: DailyHeaderStyle
) {
    // Blank leading cells so the first of the month lands under its weekday.
    val lead = month.atDay(1).dayOfWeek.value % WEEK_LENGTH
    val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map(month::atDay)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = style.glass,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (index in 0 until WEEK_LENGTH) {
                    // Sunday-first, matching the leading-blank arithmetic above.
                    val day = java.time.DayOfWeek.of(if (index == 0) WEEK_LENGTH else index)
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = style.mutedInk,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            for (week in cells.chunked(WEEK_LENGTH)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (slot in 0 until WEEK_LENGTH) {
                        val date = week.getOrNull(slot)
                        Box(modifier = Modifier.weight(1f)) {
                            if (date != null) {
                                DayCell(
                                    date = date,
                                    status = progress.statusOf(date, today),
                                    stars = progress.starsOn(date),
                                    isSelected = date == selected,
                                    onClick = { onSelect(date) },
                                    style = style
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One date. Solved days are filled and carry their stars; missed days are a
 * faint outline; days not yet open are barely there at all.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    status: DailyStatus,
    stars: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    style: DailyHeaderStyle
) {
    val solved = status == DailyStatus.SOLVED
    val ink = when (status) {
        DailyStatus.SOLVED -> style.scrim
        DailyStatus.EXPIRED -> style.mutedInk
        DailyStatus.LOCKED -> style.mutedInk.copy(alpha = 0.35f)
        DailyStatus.OPEN -> style.accent
    }

    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (solved) style.accent else Color.Transparent)
            .then(
                // Today is ringed rather than filled: it is an invitation,
                // not an achievement.
                if (status == DailyStatus.OPEN || isSelected) {
                    Modifier.border(2.dp, style.accent, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (solved) FontWeight.Bold else FontWeight.Normal,
                color = ink
            )
            if (solved) {
                Text(
                    text = "\u2605".repeat(stars.coerceIn(1, 3)),
                    fontSize = 7.sp,
                    color = ink
                )
            }
        }
    }
}

@Composable
private fun SelectedDayCard(
    date: LocalDate,
    today: LocalDate,
    progress: DailyProgress,
    style: DailyHeaderStyle
) {
    val label = date.format(DAY_FORMAT)
    val stars = progress.starsOn(date)
    val text = when (progress.statusOf(date, today)) {
        DailyStatus.SOLVED -> stringResource(R.string.daily_history_day_solved, label, stars)
        DailyStatus.EXPIRED -> stringResource(R.string.daily_history_day_missed, label)
        DailyStatus.LOCKED -> stringResource(R.string.daily_history_day_locked, label)
        DailyStatus.OPEN -> stringResource(R.string.daily_history_day_open, label)
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        color = style.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ComeBackBanner(streak: Int, solved: Int, style: DailyHeaderStyle) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = style.accent,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.daily_come_back_tomorrow),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = style.scrim
            )
            Text(
                text = stringResource(R.string.daily_history_summary, solved, streak),
                style = MaterialTheme.typography.bodyMedium,
                color = style.scrim.copy(alpha = 0.85f)
            )
        }
    }
}
