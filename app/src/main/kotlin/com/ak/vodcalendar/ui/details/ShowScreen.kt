@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.details

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.launch.ContentRef
import com.ak.vodcalendar.core.launch.toContentRef
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.model.ProviderAvailability
import com.ak.vodcalendar.core.model.Region
import com.ak.vodcalendar.core.tmdb.CreditsDto
import com.ak.vodcalendar.core.tmdb.EpisodeDto
import com.ak.vodcalendar.core.tmdb.MediaKind
import com.ak.vodcalendar.core.tmdb.TmdbImages
import com.ak.vodcalendar.core.tmdb.VideosDto
import com.ak.vodcalendar.ui.common.badges
import com.ak.vodcalendar.ui.common.dayLabel
import com.ak.vodcalendar.ui.common.ratingLabel
import com.ak.vodcalendar.ui.common.timeLabel
import com.ak.vodcalendar.ui.common.EpisodeWatchedButton
import com.ak.vodcalendar.ui.common.whereToWatch
import com.ak.vodcalendar.ui.launch.QuickPlayer
import com.ak.vodcalendar.ui.launch.WatchSection
import com.ak.vodcalendar.ui.launch.openTrailer
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The show's page, opened from an episode in the calendar: the show (description,
 * ratings, cast, trailer), the chosen episode with its "open" buttons, and every
 * season with its episodes. Click on an episode selects it, long press plays it.
 */
@Composable
fun ShowScreen(
    entry: EpisodeEntry,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onHide: () -> Unit,
    onBack: () -> Unit,
    settings: AppSettings,
    container: AppContainer,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quickPlayer = remember { QuickPlayer(context, container) }
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(entry.key) { runCatching { firstButton.requestFocus() } }

    val api = remember(settings.tmdbApiKey) { container.tmdb(settings) }
    val seasonCount by produceState(entry.seasonNumber, entry.showId) {
        value = runCatching { api?.tvDetails(entry.showId)?.numberOfSeasons }.getOrNull()?.takeIf { it > 0 } ?: entry.seasonNumber
    }
    val credits by produceState(CreditsDto(), entry.showId) {
        value = runCatching { api?.credits(MediaKind.TV, entry.showId) }.getOrNull() ?: CreditsDto()
    }
    val videos by produceState(VideosDto(), entry.showId) {
        value = runCatching { api?.videos(MediaKind.TV, entry.showId) }.getOrNull() ?: VideosDto()
    }
    var season by remember(entry.key) { mutableIntStateOf(entry.seasonNumber) }
    val episodes by produceState<List<EpisodeDto>?>(null, entry.showId, season) {
        value = null
        value = runCatching { api?.season(entry.showId, season)?.episodes }.getOrNull().orEmpty()
    }
    // The selected episode: the calendar's one at first.
    var selected by remember(entry.key) { mutableStateOf(entry.seasonNumber to entry.episodeNumber) }
    val selectedDto = episodes?.firstOrNull { it.seasonNumber == selected.first && it.episodeNumber == selected.second }
    val isCalendarEpisode = selected == (entry.seasonNumber to entry.episodeNumber)
    val ref: ContentRef = entry.toContentRef().copy(season = selected.first, episode = selected.second)

    fun play(s: Int, e: Int) {
        scope.launch {
            quickPlayer.play(entry.toContentRef().copy(season = s, episode = e), entry.wikidataId, entry.availability, entry.watchOn, settings)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (entry.backdropUrl != null) {
            AsyncImage(
                model = entry.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.15f),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "header") {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    PosterBox(entry.posterUrl ?: entry.stillUrl, entry.showName)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.showName, style = MaterialTheme.typography.headlineMedium)
                        val info = listOfNotNull(
                            entry.genreNames.joinToString(", ").takeIf { it.isNotBlank() },
                            entry.networkNames.joinToString(", ").takeIf { it.isNotBlank() },
                            "$seasonCount sezoane".takeIf { seasonCount > 1 },
                        )
                        if (info.isNotEmpty()) Text(info.joinToString(" · "), style = MaterialTheme.typography.titleSmall)
                        ratingLabel(entry).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        val tags = badges(entry) + entry.categories.map { it.label }
                        if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                        Text("Unde: " + whereToWatch(entry), style = MaterialTheme.typography.bodyLarge)
                        entry.showOverview?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(820.dp))
                        }
                    }
                }
            }
            item(key = "episode") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp),
                ) {
                    val code = "S%02dE%02d".format(selected.first, selected.second)
                    Text(
                        listOfNotNull(code, (selectedDto?.name ?: entry.episodeTitle.takeIf { isCalendarEpisode })?.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = if (isCalendarEpisode) {
                            dayLabel(entry.releaseDate) + " · " + timeLabel(entry) + " (ora României)" +
                                if (entry.delayedOnApp) " · apare aici după difuzarea originală" else ""
                        } else {
                            selectedDto?.airDate?.let { runCatching { dayLabel(LocalDate.parse(it)) }.getOrNull() } ?: "Dată necunoscută"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    val overview = (selectedDto?.overview ?: entry.overview.takeIf { isCalendarEpisode })?.takeIf { it.isNotBlank() }
                    Text(
                        overview ?: "TMDB nu are încă descrierea episodului.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(1000.dp),
                    )
                    WatchSection(
                        ref = ref,
                        wikidataId = entry.wikidataId,
                        availability = entry.availability,
                        preferred = entry.watchOn,
                        missingService = null,
                        settings = settings,
                        container = container,
                        firstFocus = firstButton,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                        videos.bestTrailer()?.let { trailer ->
                            OutlinedButton(onClick = { openTrailer(context, trailer.key) }) { Text("▶ Trailer") }
                        }
                        EpisodeWatchedButton(entry.showId, entry.imdbId, selected.first, selected.second, container)
                        OutlinedButton(onClick = onToggleFollow) { Text(if (isFollowed) "Nu mai urmări" else "Urmăresc serialul") }
                        OutlinedButton(onClick = onHide) { Text("Ascunde serialul") }
                        OutlinedButton(onClick = onBack) { Text("Înapoi") }
                    }
                }
            }
            item(key = "seasons") {
                Column {
                    SectionTitle("Sezoane")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items((1..seasonCount).toList(), key = { it }) { n ->
                            if (n == season) {
                                Button(onClick = { season = n }) { Text("Sezonul $n") }
                            } else {
                                OutlinedButton(onClick = { season = n }) { Text("Sezonul $n") }
                            }
                        }
                    }
                }
            }
            val list = episodes
            if (list == null) {
                item(key = "episodes-loading") { Text("Se încarcă episoadele…", style = MaterialTheme.typography.bodyMedium) }
            } else if (list.isEmpty()) {
                item(key = "episodes-empty") { Text("TMDB nu are episoadele sezonului.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(list, key = { "ep-${it.seasonNumber}-${it.episodeNumber}" }) { ep ->
                    EpisodeCard(
                        ep = ep,
                        selected = selected == (ep.seasonNumber to ep.episodeNumber),
                        onClick = { selected = ep.seasonNumber to ep.episodeNumber },
                        onLongClick = { play(ep.seasonNumber, ep.episodeNumber) },
                    )
                }
            }
            if (credits.cast.isNotEmpty()) {
                item(key = "cast") { CastRow(credits) }
            }
            item(key = "availability") {
                SectionTitle("Disponibil pe")
                Text(availabilityText(entry.availability), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Click pe un episod îl alege, click lung îl pornește direct.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(ep: EpisodeDto, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                TmdbImages.still(ep.stillPath)?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    (if (selected) "● " else "") + "E%02d".format(ep.episodeNumber) + (ep.name?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        ep.airDate?.let { runCatching { dayLabel(LocalDate.parse(it)) }.getOrNull() },
                        ep.runtime?.let { "$it min" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                ep.overview?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun PosterBox(url: String?, title: String) {
    Box(
        modifier = Modifier
            .width(200.dp)
            .height(300.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

/** Main cast with photos, in a row. */
@Composable
internal fun CastRow(credits: CreditsDto) {
    Column {
        SectionTitle("Actori")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(credits.cast.sortedBy { it.order }.take(15), key = { it.name + it.order }) { person ->
                Card(onClick = {}, modifier = Modifier.width(120.dp)) {
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(150.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            TmdbImages.profile(person.profilePath)?.let {
                                AsyncImage(model = it, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Column(Modifier.padding(6.dp)) {
                            Text(person.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            person.character?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun availabilityText(availability: List<ProviderAvailability>): String {
    if (availability.isEmpty()) return "Nicio platformă de streaming cunoscută (TMDB / JustWatch)."
    return Region.entries.mapNotNull { region ->
        val names = availability.filter { it.region == region }.map { it.providerName }.distinct()
        if (names.isEmpty()) null else "${region.label}: " + names.joinToString(", ")
    }.joinToString("\n")
}
