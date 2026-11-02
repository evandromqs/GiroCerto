package com.girocerto.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.girocerto.app.R
import com.girocerto.app.theme.AppSpacing
import com.girocerto.ui.components.AppText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ptBr = Locale("pt", "BR")

private val weekdayShort = mapOf(
    DayOfWeek.SUNDAY to "Dom",
    DayOfWeek.MONDAY to "Seg",
    DayOfWeek.TUESDAY to "Ter",
    DayOfWeek.WEDNESDAY to "Qua",
    DayOfWeek.THURSDAY to "Qui",
    DayOfWeek.FRIDAY to "Sex",
    DayOfWeek.SATURDAY to "Sáb"
)

/** "Segunda-feira, 2 de nov" — localized, capitalized, compact to fit one line. */
internal fun diaryDateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMM", ptBr))
        .replaceFirstChar { it.titlecase(ptBr) }

@Composable
fun DiaryDateHeader(date: LocalDate, onOpenCalendar: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        AppText(
            text = diaryDateLabel(date),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            onClick = onOpenCalendar,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(AppSpacing.MinTouchTarget),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(AppSpacing.BorderAction, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar),
                    contentDescription = "Abrir calendário",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun DiaryWeekSelector(
    selected: LocalDate,
    today: LocalDate,
    recorded: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit
) {
    val weekStart = selected.minusDays((selected.dayOfWeek.value % 7).toLong())
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        (0..6).forEach { offset ->
            val day = weekStart.plusDays(offset.toLong())
            val isSelected = day == selected
            val isToday = day == today
            Surface(
                onClick = { onSelect(day) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp),
                shape = RoundedCornerShape(AppSpacing.CornerSmall),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    AppSpacing.BorderThin,
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.outline
                    }
                ),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppText(
                        text = weekdayShort[day.dayOfWeek].orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppText(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(4.dp)
                            .background(
                                if (day in recorded) {
                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary
                                } else androidx.compose.ui.graphics.Color.Transparent,
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun DiaryCalendarDialog(
    initial: LocalDate,
    today: LocalDate,
    recorded: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var month by remember(initial) { mutableStateOf(YearMonth.from(initial)) }
    val currentMonth = YearMonth.from(today)
    val monthLabel = month.month.getDisplayName(TextStyle.FULL, ptBr)
        .replaceFirstChar { it.titlecase(ptBr) } + " de ${month.year}"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(AppSpacing.CornerCard),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(AppSpacing.BorderThick, MaterialTheme.colorScheme.outline),
            tonalElevation = 0.dp,
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    AppText(
                        text = "Escolher o dia",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 44.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = "Fechar calendário",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { month = month.minusMonths(1) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_left),
                            contentDescription = "Mês anterior",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    AppText(
                        text = monthLabel,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { month = month.plusMonths(1) },
                        enabled = month < currentMonth,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = "Próximo mês",
                            tint = if (month < currentMonth) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }

                Row(Modifier.fillMaxWidth()) {
                    listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb").forEach { label ->
                        AppText(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                val firstDayOffset = month.atDay(1).dayOfWeek.value % 7
                val cells = firstDayOffset + month.lengthOfMonth()
                val rows = (cells + 6) / 7
                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (column in 0 until 7) {
                            val dayNumber = row * 7 + column - firstDayOffset + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (dayNumber in 1..month.lengthOfMonth()) {
                                    val day = month.atDay(dayNumber)
                                    val allowed = !day.isAfter(today)
                                    val isSelected = day == initial
                                    val isToday = day == today
                                    Surface(
                                        onClick = { onSelect(day) },
                                        enabled = allowed,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("diary-day-$dayNumber"),
                                        shape = CircleShape,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        },
                                        contentColor = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            allowed -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        },
                                        border = if (isToday && !isSelected) {
                                            BorderStroke(AppSpacing.BorderThin, MaterialTheme.colorScheme.primary)
                                        } else null,
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.Center,
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            AppText(
                                                text = dayNumber.toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                textAlign = TextAlign.Center,
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                                    allowed -> MaterialTheme.colorScheme.onSurface
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                                }
                                            )
                                            if (day in recorded) {
                                                Box(
                                                    Modifier
                                                        .size(3.dp)
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.primary,
                                                            CircleShape
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = { onSelect(today) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    AppText("Hoje", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
