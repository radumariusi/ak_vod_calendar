@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.CalendarResult
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.ui.common.InfoMessage
import com.ak.vodcalendar.ui.common.MonthGrid
import com.ak.vodcalendar.ui.common.MonthItem
import com.ak.vodcalendar.ui.common.monthLabel
import com.ak.vodcalendar.ui.common.NavButton
import com.ak.vodcalendar.ui.common.badges
import com.ak.vodcalendar.ui.common.dayLabel
import com.ak.vodcalendar.ui.common.ratingLabel
import com.ak.vodcalendar.ui.common.timeLabel
import com.ak.vodcalendar.ui.common.weekLabel
import com.ak.vodcalendar.ui.common.whereToWatch

@Composable
fun CalendarScreen(container: AppContainer, onOpen: (EpisodeEntry) -> Unit, onPlay: (EpisodeEntry) -> Unit) {
    val viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dayListState = rememberLazyListState()
    val weekListState = rememberLazyListState()
    val monthListState = rememberLazyListState()
    // Back to the top when the period changes, but not when coming back from details.
    var shownPeriod by rememberSaveable { mutableStateOf("") }
    val period = "${state.mode}-${state.from}"
    LaunchedEffect(period) {
        if (shownPeriod.isNotEmpty() && shownPeriod != period) {
            dayListState.scrollToItem(0)
            weekListState.scrollToItem(0)
            monthListState.scrollToItem(0)
        }
        shownPeriod = period
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::previous) { Text("◀") }
            Text(
                text = when (state.mode) {
                    CalendarMode.DAY -> dayLabel(state.from)
                    CalendarMode.WEEK -> "Săptămâna " + weekLabel(state.from, state.to)
                    CalendarMode.MONTH -> monthLabel(state.from)
                },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.width(380.dp),
            )
            OutlinedButton(onClick = viewModel::next) { Text("▶") }
            OutlinedButton(onClick = viewModel::goToToday) { Text("Azi") }
            CalendarMode.entries.forEach { mode ->
                NavButton(label = mode.label, selected = state.mode == mode, onClick = { viewModel.setMode(mode) })
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            NavButton(
                label = if (state.onlyInstalledApps) "Doar aplicațiile mele ✓" else "Doar aplicațiile mele",
                selected = state.onlyInstalledApps,
                onClick = viewModel::toggleOnlyInstalledApps,
            )
            NavButton(
                label = if (state.showRejected) "Arată și ce nu trece pragurile ✓" else "Arată și ce nu trece pragurile",
                selected = state.showRejected,
                onClick = viewModel::toggleShowRejected,
            )
            OutlinedButton(onClick = viewModel::reload) { Text("Reîncarcă") }
        }

        when {
            state.missingKey -> InfoMessage(
                "Lipsește cheia TMDB. Adaug-o în Setări sau la build (TMDB_API_KEY în local.properties).",
            )
            state.loading && state.result == null -> InfoMessage(
                if (state.mode != CalendarMode.DAY) "Se încarcă săptămâna… (prima dată durează mai mult)" else "Se încarcă episoadele…",
            )
            state.error != null -> InfoMessage("Eroare: ${state.error}")
            else -> {
                val result = state.result ?: CalendarResult(emptyList())
                Summary(state, result)
                if (!state.hasInstalledApps) {
                    InfoMessage("Sfat: bifează în Setări aplicațiile pe care le ai și țara fiecăreia.")
                }
                if (state.visible.isEmpty()) {
                    InfoMessage("Niciun episod în perioada asta după filtrele tale.")
                } else if (state.mode == CalendarMode.DAY) {
                    DayList(state.visible, dayListState, onOpen, onPlay)
                } else if (state.mode == CalendarMode.WEEK) {
                    WeekGrid(state, weekListState, onOpen, onPlay)
                } else {
                    val byKey = state.visible.associateBy { it.key }
                    MonthGrid(
                        from = state.from,
                        to = state.to,
                        itemsByDay = state.visible.groupBy({ it.releaseDate }, { MonthItem(it.key, it.showName + " " + it.code, dimmed = !it.verdict.passed) }),
                        listState = monthListState,
                        onOpen = { key -> byKey[key]?.let(onOpen) },
                        onLongPress = { key -> byKey[key]?.let(onPlay) },
                        onOpenDay = viewModel::openDay,
                    )
                }
            }
        }
    }
}

@Composable
private fun Summary(state: CalendarUiState, result: CalendarResult) {
    val parts = buildList {
        if (state.loading) add("se actualizează…")
        add("${result.entries.size} episoade")
        add("ore în ora României (≈ = estimat)")
        if (result.rejected.isNotEmpty()) add("${result.rejected.size} sub prag")
        if (result.hiddenByCategory > 0) add("${result.hiddenByCategory} seriale din categorii ascunse")
        if (result.hiddenByUser > 0) add("${result.hiddenByUser} ascunse de tine")
        if (state.onlyInstalledApps && result.notOnMyApps > 0) add("${result.notOnMyApps} pe aplicații pe care nu le ai")
        add(if (result.usedImdb) "note IMDb (OMDb)" else "note TMDB (pune o cheie OMDb pentru IMDb)")
    }
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun DayList(entries: List<EpisodeEntry>, listState: LazyListState, onOpen: (EpisodeEntry) -> Unit, onPlay: (EpisodeEntry) -> Unit) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(entries, key = { it.key }) { entry -> EpisodeRow(entry, onClick = { onOpen(entry) }, onLongClick = { onPlay(entry) }) }
    }
}

@Composable
private fun WeekGrid(state: CalendarUiState, listState: LazyListState, onOpen: (EpisodeEntry) -> Unit, onPlay: (EpisodeEntry) -> Unit) {
    val byDay = state.visible.groupBy { it.releaseDate }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        state.days.forEach { day ->
            val entries = byDay[day].orEmpty()
            item(key = "day-$day") {
                Text(
                    text = dayLabel(day) + if (entries.isEmpty()) " · nimic" else " · ${entries.size} episoade",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (entries.isNotEmpty()) {
                item(key = "row-$day") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                    ) {
                        items(entries, key = { it.key }) { entry -> PosterCard(entry, onClick = { onOpen(entry) }, onLongClick = { onPlay(entry) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(entry: EpisodeEntry, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (entry.posterUrl != null) {
            AsyncImage(
                model = entry.posterUrl,
                contentDescription = entry.showName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(entry.showName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun PosterCard(entry: EpisodeEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val dim = if (entry.verdict.passed) 1f else 0.45f
    Card(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.width(150.dp).alpha(dim)) {
        Column {
            Poster(entry, Modifier.fillMaxWidth().height(200.dp))
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.showName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.code + " · " + timeLabel(entry), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = entry.watchOn?.let { "${it.name} ${entry.watchRegion?.code}" } ?: "Nu ai aplicația",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(entry: EpisodeEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val dim = if (entry.verdict.passed) 1f else 0.45f
    Card(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth().alpha(dim)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Poster(entry, Modifier.width(80.dp).height(120.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.showName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(entry.code, entry.episodeTitle?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = timeLabel(entry) + " · " + whereToWatch(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                val line = buildList {
                    addAll(badges(entry))
                    ratingLabel(entry).takeIf { it.isNotBlank() }?.let { add(it) }
                    entry.categories.forEach { add(it.label) }
                    if (!entry.verdict.passed) add("Filtrat: ${entry.verdict.label}")
                }
                if (line.isNotEmpty()) {
                    Text(
                        text = line.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
