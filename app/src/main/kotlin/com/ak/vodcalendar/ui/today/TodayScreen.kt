@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.today

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.time.RomaniaTime
import com.ak.vodcalendar.ui.common.InfoMessage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val romanian: Locale = Locale.forLanguageTag("ro-RO")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM", romanian)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", romanian)

@Composable
fun TodayScreen(container: AppContainer) {
    val viewModel: TodayViewModel = viewModel(factory = TodayViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = viewModel::previousDay) { Text("◀") }
            Text(
                text = dayLabel(state.date),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(360.dp),
            )
            OutlinedButton(onClick = viewModel::nextDay) { Text("▶") }
            OutlinedButton(onClick = viewModel::goToToday) { Text("Azi") }
            OutlinedButton(onClick = viewModel::reload) { Text("Reîncarcă") }
        }

        when {
            state.missingKey -> InfoMessage(
                "Lipsește cheia TMDB. Adaug-o în Setări sau la build (TMDB_API_KEY în local.properties).",
            )
            state.loading -> InfoMessage("Se încarcă episoadele…")
            state.error != null -> InfoMessage("Eroare: ${state.error}")
            state.entries.isEmpty() -> InfoMessage("Niciun episod în ziua asta după filtrele tale.")
            else -> {
                if (!state.hasInstalledApps) {
                    InfoMessage("Sfat: bifează în Setări aplicațiile pe care le ai și țara fiecăreia.")
                }
                Text(
                    text = "${state.entries.size} episoade · ore în ora României (≈ = estimat)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.entries, key = { "${it.showId}-${it.seasonNumber}-${it.episodeNumber}" }) { entry ->
                        EpisodeCard(entry)
                    }
                }
            }
        }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = RomaniaTime.today()
    val prefix = when (date) {
        today -> "Azi · "
        today.plusDays(1) -> "Mâine · "
        today.minusDays(1) -> "Ieri · "
        else -> ""
    }
    return prefix + date.format(dayFormat)
}

@Composable
private fun EpisodeCard(entry: EpisodeEntry) {
    // Details screen and "open in app" arrive in stages 2 and 4.
    Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (entry.posterUrl != null) {
                    AsyncImage(
                        model = entry.posterUrl,
                        contentDescription = entry.showName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.showName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(entry.code, entry.episodeTitle?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = (if (entry.timeIsApproximate) "≈ " else "") + entry.releaseAt.format(timeFormat) + " · " + whereToWatch(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                val badges = buildList {
                    if (entry.isNewShow && entry.isSeasonPremiere) add("Serial nou") else if (entry.isSeasonPremiere) add("Premieră de sezon")
                    if (entry.tmdbVotes > 0) add("TMDB %.1f (%s voturi)".format(romanian, entry.tmdbRating, compact(entry.tmdbVotes)))
                    entry.categories.forEach { add(it.label) }
                }
                if (badges.isNotEmpty()) {
                    Text(
                        text = badges.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun whereToWatch(entry: EpisodeEntry): String {
    val app = entry.watchOn
    if (app != null) return "${app.name} (${entry.watchRegion?.code})"
    if (entry.availability.isEmpty()) return "Fără info de streaming"
    val elsewhere = entry.availability.take(3).joinToString(", ") { "${it.providerName} ${it.region.code}" }
    return "Nu ai aplicația · $elsewhere"
}

private fun compact(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(romanian, n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(romanian, n / 1_000.0)
    else -> n.toString()
}
