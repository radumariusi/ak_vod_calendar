@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.movies

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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.ak.vodcalendar.core.tmdb.CreditsDto
import com.ak.vodcalendar.core.tmdb.MediaKind
import com.ak.vodcalendar.core.tmdb.VideosDto
import com.ak.vodcalendar.ui.details.CastRow
import com.ak.vodcalendar.ui.launch.openTrailer
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.launch.toContentRef
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.ui.launch.WatchSection
import com.ak.vodcalendar.core.model.MovieEntry
import com.ak.vodcalendar.core.model.Region
import com.ak.vodcalendar.ui.common.dayLabel
import com.ak.vodcalendar.ui.common.movieBadges
import com.ak.vodcalendar.ui.common.movieKindLabel
import com.ak.vodcalendar.ui.common.movieRatingLabel
import com.ak.vodcalendar.ui.common.movieReleaseLine
import com.ak.vodcalendar.ui.common.movieTimeLabel
import com.ak.vodcalendar.ui.common.movieWhere

/**
 * Movie page: description, ratings, cast, trailer, every known release (cinema US/RO,
 * digital, streaming), where it streams, "open in app" and "Deschide cu…".
 */
@Composable
fun MovieDetailsScreen(
    entry: MovieEntry,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onHide: () -> Unit,
    onBack: () -> Unit,
    settings: AppSettings,
    container: AppContainer,
) {
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(entry.key) { runCatching { firstButton.requestFocus() } }
    val context = LocalContext.current
    val api = remember(settings.tmdbApiKey) { container.tmdb(settings) }
    val credits by produceState(CreditsDto(), entry.movieId) {
        value = runCatching { api?.credits(MediaKind.MOVIE, entry.movieId) }.getOrNull() ?: CreditsDto()
    }
    val videos by produceState(VideosDto(), entry.movieId) {
        value = runCatching { api?.videos(MediaKind.MOVIE, entry.movieId) }.getOrNull() ?: VideosDto()
    }

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
                        if (entry.posterUrl != null) {
                            AsyncImage(
                                model = entry.posterUrl,
                                contentDescription = entry.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.title, style = MaterialTheme.typography.headlineMedium)
                        val subtitle = listOfNotNull(
                            entry.originalTitle,
                            entry.runtimeMinutes?.let { "$it min" },
                            entry.genreNames.joinToString(", ").takeIf { it.isNotBlank() },
                        )
                        if (subtitle.isNotEmpty()) Text(subtitle.joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = movieKindLabel(entry) + " · " + dayLabel(entry.releaseDate) + " · " + movieTimeLabel(entry) +
                                if (entry.hasTime) " (ora României)" else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text("Unde: " + movieWhere(entry), style = MaterialTheme.typography.bodyLarge)
                        movieRatingLabel(entry).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        val tags = movieBadges(entry) + entry.categories.map { it.label }
                        if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = (if (entry.verdict.passed) "În calendar: " else "Filtrat: ") + entry.verdict.movieLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        entry.overview?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(820.dp))
                        }
                        WatchSection(
                            ref = entry.toContentRef(),
                            wikidataId = entry.wikidataId,
                            availability = entry.availability,
                            preferred = entry.watchOn,
                            missingService = entry.service.takeIf { entry.watchOn == null },
                            settings = settings,
                            container = container,
                            firstFocus = firstButton,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            videos.bestTrailer()?.let { trailer ->
                                OutlinedButton(onClick = { openTrailer(context, trailer.key) }) { Text("▶ Trailer") }
                            }
                            OutlinedButton(onClick = onToggleFollow) {
                                Text(if (isFollowed) "Nu mai urmări" else "Urmăresc filmul")
                            }
                            OutlinedButton(onClick = onHide) { Text("Ascunde filmul") }
                            OutlinedButton(onClick = onBack) { Text("Înapoi") }
                        }
                    }
                }
            }
            if (credits.cast.isNotEmpty()) {
                item { CastRow(credits) }
            }
            item {
                entry.tagline?.let { Section("", "„$it”") }
                Section("Despre film", entry.overview?.takeIf { it.isNotBlank() } ?: "TMDB nu are încă descrierea filmului.")
                Section(
                    "Lansări",
                    entry.releases.joinToString("\n") { movieReleaseLine(it) }.ifBlank { "TMDB nu are încă date de lansare." },
                )
                Section("Disponibil acum pe", availability(entry))
            }
        }
    }
}

private fun availability(entry: MovieEntry): String {
    if (entry.availability.isEmpty()) return "Încă nu e pe nicio platformă de streaming (după TMDB / JustWatch)."
    return Region.entries.mapNotNull { region ->
        val names = entry.availability.filter { it.region == region }.map { it.providerName }.distinct()
        if (names.isEmpty()) null else "${region.label}: " + names.joinToString(", ")
    }.joinToString("\n")
}

@Composable
private fun Section(title: String, body: String) {
    if (title.isNotBlank()) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
    } else {
        Box(Modifier.padding(top = 16.dp))
    }
    Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(900.dp))
}
