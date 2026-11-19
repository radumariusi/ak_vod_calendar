@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.model.Region
import com.ak.vodcalendar.ui.common.badges
import com.ak.vodcalendar.ui.common.dayLabel
import com.ak.vodcalendar.ui.common.ratingLabel
import com.ak.vodcalendar.ui.common.timeLabel
import com.ak.vodcalendar.ui.common.whereToWatch

/**
 * Episode details. "Open in app" / "Open with…" arrive in stage 4; here the user
 * can follow or hide the show.
 */
@Composable
fun EpisodeDetailsScreen(
    entry: EpisodeEntry,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onHide: () -> Unit,
    onBack: () -> Unit,
) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(entry.key) { runCatching { firstButton.requestFocus() } }

    Box(modifier = Modifier.fillMaxSize()) {
        if (entry.backdropUrl != null) {
            AsyncImage(
                model = entry.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.18f),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        val image = entry.posterUrl ?: entry.stillUrl
                        if (image != null) {
                            AsyncImage(
                                model = image,
                                contentDescription = entry.showName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.showName, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = listOfNotNull(
                                entry.code,
                                entry.episodeTitle?.takeIf { it.isNotBlank() },
                                entry.runtimeMinutes?.let { "$it min" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = dayLabel(entry.releaseDate) + " · " + timeLabel(entry) + " (ora României)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = "Unde: " + whereToWatch(entry) +
                                if (entry.delayedOnApp) " · apare aici după difuzarea originală" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        ratingLabel(entry).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        val tags = badges(entry) + entry.categories.map { it.label }
                        if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = (if (entry.verdict.passed) "În calendar: " else "Filtrat: ") + entry.verdict.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Button(onClick = onToggleFollow, modifier = Modifier.focusRequester(firstButton)) {
                                Text(if (isFollowed) "Nu mai urmări" else "Urmăresc serialul")
                            }
                            OutlinedButton(onClick = onHide) { Text("Ascunde serialul") }
                            OutlinedButton(onClick = onBack) { Text("Înapoi") }
                        }
                        Text(
                            text = "Deschiderea direct în aplicație și „Deschide cu…” vin în etapa 4.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                val overview = entry.overview?.takeIf { it.isNotBlank() }
                Section("Despre episod", overview ?: "TMDB nu are încă descrierea episodului.")
                entry.showOverview?.takeIf { it.isNotBlank() }?.let { Section("Despre serial", it) }
                val info = buildList {
                    if (entry.networkNames.isNotEmpty()) add("Rețea: " + entry.networkNames.joinToString(", "))
                    if (entry.genreNames.isNotEmpty()) add("Gen: " + entry.genreNames.joinToString(", "))
                }
                if (info.isNotEmpty()) Section("Info", info.joinToString("\n"))
                Section("Disponibil pe", availability(entry))
            }
        }
    }
}

private fun availability(entry: EpisodeEntry): String {
    if (entry.availability.isEmpty()) return "TMDB nu are încă date de streaming pentru serial."
    return Region.entries.mapNotNull { region ->
        val names = entry.availability.filter { it.region == region }.map { it.providerName }.distinct()
        if (names.isEmpty()) null else "${region.label}: " + names.joinToString(", ")
    }.joinToString("\n")
}

@Composable
private fun Section(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(900.dp))
}
