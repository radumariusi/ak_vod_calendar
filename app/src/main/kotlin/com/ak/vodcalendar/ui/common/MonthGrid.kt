@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ak.vodcalendar.ui.touch.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.ak.vodcalendar.core.time.RomaniaTime
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ro"))
private val weekDays = listOf("Lun", "Mar", "Mie", "Joi", "Vin", "Sâm", "Dum")

fun monthLabel(date: LocalDate): String = date.format(monthFormat).replaceFirstChar { it.uppercase() }

/** One line in a month cell. */
data class MonthItem(val key: String, val label: String, val dimmed: Boolean = false)

/**
 * Month view: weeks as rows, Monday first. Each day shows its first [perDay] titles
 * (click opens them, long press plays them); the day header opens the day view.
 */
@Composable
fun MonthGrid(
    from: LocalDate,
    to: LocalDate,
    itemsByDay: Map<LocalDate, List<MonthItem>>,
    listState: LazyListState,
    onOpen: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    perDay: Int = 3,
) {
    val start = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val end = to.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val weeks = generateSequence(start) { it.plusWeeks(1) }.takeWhile { !it.isAfter(end) }.toList()
    val today = RomaniaTime.today()

    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
        item(key = "weekdays") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weekDays.forEach { Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)) }
            }
        }
        weeks.forEach { monday ->
            item(key = "week-$monday") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    (0L..6L).map { monday.plusDays(it) }.forEach { day ->
                        val inMonth = !day.isBefore(from) && !day.isAfter(to)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f).height(196.dp).alpha(if (inMonth) 1f else 0.35f),
                        ) {
                            val items = if (inMonth) itemsByDay[day].orEmpty() else emptyList()
                            Card(onClick = { onOpenDay(day) }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = day.dayOfMonth.toString() + (if (day == today) " · azi" else "") +
                                        (if (items.isNotEmpty()) " (${items.size})" else ""),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            items.take(perDay).forEach { item ->
                                Card(
                                    onClick = { onOpen(item.key) },
                                    onLongClick = { onLongPress(item.key) },
                                    modifier = Modifier.fillMaxWidth().alpha(if (item.dimmed) 0.5f else 1f),
                                ) {
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                            if (items.size > perDay) {
                                Text(
                                    "+${items.size - perDay} (deschide ziua)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
