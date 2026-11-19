@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.movies

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
import com.ak.vodcalendar.core.model.MovieCalendarResult
import com.ak.vodcalendar.core.model.MovieEntry
import com.ak.vodcalendar.core.model.MovieReleaseKind
import com.ak.vodcalendar.ui.calendar.CalendarMode
import com.ak.vodcalendar.ui.common.InfoMessage
import com.ak.vodcalendar.ui.common.NavButton
import com.ak.vodcalendar.ui.common.dayLabel
import com.ak.vodcalendar.ui.common.movieBadges
import com.ak.vodcalendar.ui.common.movieKindLabel
import com.ak.vodcalendar.ui.common.movieRatingLabel
import com.ak.vodcalendar.ui.common.movieTimeLabel
import com.ak.vodcalendar.ui.common.movieWhere
import com.ak.vodcalendar.ui.common.weekLabel

@Composable
fun MovieCalendarScreen(container: AppContainer, onOpen: (MovieEntry) -> Unit) {
    val viewModel: MovieCalendarViewModel = viewModel(factory = MovieCalendarViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dayListState = rememberLazyListState()
    val weekListState = rememberLazyListState()
    var shownPeriod by rememberSaveable { mutableStateOf("") }
    val period = "${state.mode}-${state.from}"
    LaunchedEffect(period) {
        if (shownPeriod.isNotEmpty() && shownPeriod != period) {
            dayListState.scrollToItem(0)
            weekListState.scrollToItem(0)
        }
        shownPeriod = period
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::previous) { Text("◀") }
            Text(
                text = if (state.mode == CalendarMode.DAY) dayLabel(state.from) else "Săptămâna " + weekLabel(state.from, state.to),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier.width(380.dp),
            )
            OutlinedButton(onClick = viewModel::next) { Text("▶") }
            OutlinedButton(onClick = viewModel::goToToday) { Text("Azi") }
            CalendarMode.entries.forEach { mode ->
                NavButton(label = mode.label, selected = state.mode == mode, onClick = { if (state.mode != mode) viewModel.toggleMode() })
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            MovieReleaseKind.entries.forEach { kind ->
                val on = kind in state.kinds
                NavButton(
                    label = if (on) "${kind.short} ✓" else kind.short,
                    selected = on,
                    onClick = { viewModel.toggleKind(kind) },
                )
            }
            OutlinedButton(onClick = viewModel::reload) { Text("Reîncarcă") }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            NavButton(
                label = if (state.onlyInstalledApps) "Streaming doar pe aplicațiile mele ✓" else "Streaming doar pe aplicațiile mele",
                selected = state.onlyInstalledApps,
                onClick = viewModel::toggleOnlyInstalledApps,
            )
            NavButton(
                label = if (state.showRejected) "Arată și ce nu trece pragurile ✓" else "Arată și ce nu trece pragurile",
                selected = state.showRejected,
                onClick = viewModel::toggleShowRejected,
            )
        }

        when {
            state.missingKey -> InfoMessage(
                "Lipsește cheia TMDB. Adaug-o în Setări sau la build (TMDB_API_KEY în local.properties).",
            )
            state.kinds.isEmpty() -> InfoMessage("Alege cel puțin un tip de lansare: Streaming, Digital, Cinema US sau Cinema RO.")
            state.loading && state.result == null -> InfoMessage(
                if (state.mode == CalendarMode.WEEK) "Se încarcă filmele săptămânii… (prima dată durează mai mult)" else "Se încarcă filmele…",
            )
            state.error != null -> InfoMessage("Eroare: ${state.error}")
            else -> {
                val result = state.result ?: MovieCalendarResult(emptyList())
                Summary(state, result)
                if (state.visible.isEmpty()) {
                    InfoMessage("Niciun film în perioada asta după filtrele tale.")
                } else if (state.mode == CalendarMode.DAY) {
                    DayList(state.visible, dayListState, onOpen)
                } else {
                    WeekGrid(state, weekListState, onOpen)
                }
            }
        }
    }
}

@Composable
private fun Summary(state: MovieCalendarUiState, result: MovieCalendarResult) {
    val parts = buildList {
        if (state.loading) add("se actualizează…")
        add("${result.entries.size} lansări")
        add("ore în ora României (≈ = estimat)")
        if (result.rejected.isNotEmpty()) add("${result.rejected.size} sub prag")
        if (result.hiddenByCategory > 0) add("${result.hiddenByCategory} filme din categorii ascunse")
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
private fun DayList(entries: List<MovieEntry>, listState: LazyListState, onOpen: (MovieEntry) -> Unit) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(entries, key = { it.key }) { entry -> MovieRow(entry, onClick = { onOpen(entry) }) }
    }
}

@Composable
private fun WeekGrid(state: MovieCalendarUiState, listState: LazyListState, onOpen: (MovieEntry) -> Unit) {
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
                    text = dayLabel(day) + if (entries.isEmpty()) " · nimic" else " · ${entries.size} lansări",
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
                        items(entries, key = { it.key }) { entry -> MoviePosterCard(entry, onClick = { onOpen(entry) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePoster(entry: MovieEntry, modifier: Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (entry.posterUrl != null) {
            AsyncImage(
                model = entry.posterUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(entry.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
        }
    }
}

@Composable
private fun MoviePosterCard(entry: MovieEntry, onClick: () -> Unit) {
    val dim = if (entry.verdict.passed) 1f else 0.45f
    Card(onClick = onClick, modifier = Modifier.width(150.dp).alpha(dim)) {
        Column {
            MoviePoster(entry, Modifier.fillMaxWidth().height(200.dp))
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(movieKindLabel(entry) + " · " + movieTimeLabel(entry), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(
                    text = when (entry.kind) {
                        MovieReleaseKind.STREAMING -> entry.watchOn?.let { "${it.name} ${entry.watchRegion?.code}" }
                            ?: entry.service?.let { "${it.name} · nu o ai" }
                            ?: "Streaming"
                        MovieReleaseKind.DIGITAL -> "Închiriere / cumpărare"
                        else -> "Cinema"
                    },
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
private fun MovieRow(entry: MovieEntry, onClick: () -> Unit) {
    val dim = if (entry.verdict.passed) 1f else 0.45f
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().alpha(dim)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MoviePoster(entry, Modifier.width(80.dp).height(120.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(
                        movieKindLabel(entry),
                        entry.runtimeMinutes?.let { "$it min" },
                        entry.genreNames.take(3).joinToString(", ").takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = movieTimeLabel(entry) + " · " + movieWhere(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val line = buildList {
                    addAll(movieBadges(entry))
                    movieRatingLabel(entry).takeIf { it.isNotBlank() }?.let { add(it) }
                    entry.categories.forEach { add(it.label) }
                    if (!entry.verdict.passed) add("Filtrat: ${entry.verdict.movieLabel}")
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
