@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.AlternativePlayers
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.ContentFilter
import com.ak.vodcalendar.core.model.MovieReleaseKind
import com.ak.vodcalendar.core.model.StreamingCatalog
import com.ak.vodcalendar.ui.common.InfoMessage
import com.ak.vodcalendar.ui.common.SectionHeader
import java.util.Locale

private val ratingSteps = listOf(5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5)
private val imdbVoteSteps = listOf(500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000)
private val tmdbVoteSteps = listOf(20, 50, 100, 300, 500, 1_000, 2_000)
private val tmdbMovieVoteSteps = listOf(50, 100, 200, 300, 500, 1_000, 2_000)

private fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1).mod(size)]

@Composable
fun SettingsScreen(container: AppContainer) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container.settingsRepository))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings
    if (current == null) {
        InfoMessage("Se încarcă setările…")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        streamingApps(current, viewModel)
        alternativePlayer(current, viewModel)
        thresholds(current, viewModel)
        movies(current, viewModel)
        contentFilters(current, viewModel)
        savedShows(current, viewModel)
        savedMovies(current, viewModel)
        item(key = "key-tmdb") {
            ApiKeySection(
                title = "Cheie TMDB",
                value = current.tmdbApiKey,
                hasBuiltInKey = container.hasBuiltInTmdbKey,
                missingHint = "Nu există cheie. Introdu cheia API v3 sau tokenul v4 de pe themoviedb.org.",
                onSave = viewModel::saveTmdbKey,
            )
        }
        item(key = "key-omdb") {
            ApiKeySection(
                title = "Cheie OMDb (note IMDb)",
                value = current.omdbApiKey,
                hasBuiltInKey = container.hasBuiltInOmdbKey,
                missingHint = "Opțional. Fără ea se folosesc notele TMDB. Cheie gratuită pe omdbapi.com (1.000 de cereri pe zi).",
                onSave = viewModel::saveOmdbKey,
            )
        }
    }
}

private fun LazyListScope.streamingApps(settings: AppSettings, viewModel: SettingsViewModel) {
    item {
        SectionHeader(
            "Aplicații de streaming",
            "Bifează ce ai instalat. Țara e biblioteca pe care o folosești (US dacă intri cu VPN). " +
                "Episoadele apar după data din țara aleasă, în ora României.",
        )
    }
    items(settings.apps, key = { "app-${it.appId}" }) { user ->
        val app = StreamingCatalog.byId(user.appId) ?: return@items
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ListItem(
                selected = user.installed,
                onClick = { viewModel.toggleInstalled(user.appId) },
                headlineContent = { Text(app.name) },
                trailingContent = { Text(if (user.installed) "✓ Instalată" else "Nu o am") },
                modifier = Modifier.weight(1f),
            )
            if (user.installed) {
                Button(onClick = { viewModel.toggleRegion(user.appId) }, modifier = Modifier.width(140.dp)) {
                    Text("Țara: ${user.region.code}")
                }
            } else {
                OutlinedButton(onClick = { viewModel.toggleInstalled(user.appId) }, modifier = Modifier.width(140.dp)) {
                    Text("Adaugă")
                }
            }
        }
    }
}

private fun LazyListScope.alternativePlayer(settings: AppSettings, viewModel: SettingsViewModel) {
    item {
        SectionHeader(
            "Player alternativ implicit",
            "Folosit la „Deschide cu…”, mai ales când nu ai aplicația oficială. Lansarea efectivă vine în etapa 4.",
        )
    }
    item(key = "player-none") {
        ListItem(
            selected = settings.alternativePlayerId == null,
            onClick = { viewModel.selectAlternativePlayer(null) },
            headlineContent = { Text("Întreabă de fiecare dată") },
            trailingContent = { if (settings.alternativePlayerId == null) Text("●") },
        )
    }
    items(AlternativePlayers.all, key = { "player-${it.id}" }) { player ->
        ListItem(
            selected = settings.alternativePlayerId == player.id,
            onClick = { viewModel.selectAlternativePlayer(player.id) },
            headlineContent = { Text(player.name) },
            trailingContent = { if (settings.alternativePlayerId == player.id) Text("●") },
        )
    }
}

private fun LazyListScope.thresholds(settings: AppSettings, viewModel: SettingsViewModel) {
    val t = settings.thresholds
    item {
        SectionHeader(
            "Praguri de calitate pentru seriale",
            "Apasă pe un rând ca să schimbi valoarea. Serialele în curs trebuie să treacă pragurile IMDb " +
                "(dacă ai cheie OMDb), altfel pe cele TMDB. Serialele noi intră dacă sunt pe lista celor mult " +
                "așteptate (cele mai populare premiere din TMDB) sau dacă trec deja pragurile.",
        )
    }
    item(key = "th-imdb-rating") {
        ValueRow("Notă minimă IMDb", "%.1f".format(Locale.US, t.minImdbRating)) {
            viewModel.updateThresholds { it.copy(minImdbRating = ratingSteps.after(it.minImdbRating)) }
        }
    }
    item(key = "th-imdb-votes") {
        ValueRow("Voturi minime IMDb", t.minImdbVotes.toString()) {
            viewModel.updateThresholds { it.copy(minImdbVotes = imdbVoteSteps.after(it.minImdbVotes)) }
        }
    }
    item(key = "th-tmdb-rating") {
        ValueRow("Notă minimă TMDB", "%.1f".format(Locale.US, t.minTmdbRating)) {
            viewModel.updateThresholds { it.copy(minTmdbRating = ratingSteps.after(it.minTmdbRating)) }
        }
    }
    item(key = "th-tmdb-votes") {
        ValueRow("Voturi minime TMDB", t.minTmdbVotes.toString()) {
            viewModel.updateThresholds { it.copy(minTmdbVotes = tmdbVoteSteps.after(it.minTmdbVotes)) }
        }
    }
    item(key = "th-only-mine") {
        ValueRow("Doar episoadele de pe aplicațiile mele", if (settings.onlyInstalledApps) "Da" else "Nu") {
            viewModel.toggleOnlyInstalledApps()
        }
    }
    item(key = "th-anticipated") {
        ValueRow("Seriale noi doar din listele „mult așteptate”", if (t.newShowsOnlyIfAnticipated) "Da" else "Nu") {
            viewModel.updateThresholds { it.copy(newShowsOnlyIfAnticipated = !it.newShowsOnlyIfAnticipated) }
        }
    }
}

private fun LazyListScope.movies(settings: AppSettings, viewModel: SettingsViewModel) {
    val t = settings.movieThresholds
    item {
        SectionHeader(
            "Filme",
            "Ce lansări apar în calendarul de filme. Streaming: premierele pe platforme, cu data din țara setată " +
                "pentru aplicație. Digital: închiriere / cumpărare în SUA. Cinema: separat SUA și România.",
        )
    }
    items(MovieReleaseKind.entries, key = { "movie-kind-${it.name}" }) { kind ->
        val on = kind in settings.movieKinds
        ListItem(
            selected = on,
            onClick = { viewModel.toggleMovieKind(kind) },
            headlineContent = { Text(kind.label) },
            trailingContent = { Text(if (on) "Afișat" else "Ascuns") },
        )
    }
    item {
        SectionHeader(
            "Praguri de calitate pentru filme",
            "Un film cu destule voturi trebuie să treacă pragurile IMDb (dacă ai cheie OMDb), altfel pe cele TMDB. " +
                "Unul încă fără voturi (în cinema sau abia lansat pe streaming) intră dacă e printre cele mai " +
                "așteptate (cele mai populare filme noi din TMDB).",
        )
    }
    item(key = "mth-imdb-rating") {
        ValueRow("Notă minimă IMDb", "%.1f".format(Locale.US, t.minImdbRating)) {
            viewModel.updateMovieThresholds { it.copy(minImdbRating = ratingSteps.after(it.minImdbRating)) }
        }
    }
    item(key = "mth-imdb-votes") {
        ValueRow("Voturi minime IMDb", t.minImdbVotes.toString()) {
            viewModel.updateMovieThresholds { it.copy(minImdbVotes = imdbVoteSteps.after(it.minImdbVotes)) }
        }
    }
    item(key = "mth-tmdb-rating") {
        ValueRow("Notă minimă TMDB", "%.1f".format(Locale.US, t.minTmdbRating)) {
            viewModel.updateMovieThresholds { it.copy(minTmdbRating = ratingSteps.after(it.minTmdbRating)) }
        }
    }
    item(key = "mth-tmdb-votes") {
        ValueRow("Voturi minime TMDB", t.minTmdbVotes.toString()) {
            viewModel.updateMovieThresholds { it.copy(minTmdbVotes = tmdbMovieVoteSteps.after(it.minTmdbVotes)) }
        }
    }
    item(key = "mth-anticipated") {
        ValueRow("Filme noi doar din cele „mult așteptate”", if (t.newShowsOnlyIfAnticipated) "Da" else "Nu") {
            viewModel.updateMovieThresholds { it.copy(newShowsOnlyIfAnticipated = !it.newShowsOnlyIfAnticipated) }
        }
    }
}

private fun LazyListScope.savedMovies(settings: AppSettings, viewModel: SettingsViewModel) {
    item {
        SectionHeader(
            "Filme urmărite",
            if (settings.followedMovies.isEmpty()) {
                "Niciunul. Din detaliile unui film apasă „Urmăresc filmul” ca să-i vezi toate lansările, indiferent de praguri."
            } else {
                "Apar mereu, indiferent de praguri și filtre. Apasă pe unul ca să nu-l mai urmărești."
            },
        )
    }
    items(settings.followedMovies, key = { "followed-movie-${it.id}" }) { movie ->
        ValueRow(movie.name, "Nu mai urmări") { viewModel.unfollowMovie(movie.id) }
    }
    item {
        SectionHeader(
            "Filme ascunse",
            if (settings.hiddenMovies.isEmpty()) {
                "Niciunul. Din detaliile unui film apasă „Ascunde filmul”."
            } else {
                "Nu mai apar în calendar. Apasă pe unul ca să-l afișezi din nou."
            },
        )
    }
    items(settings.hiddenMovies, key = { "hidden-movie-${it.id}" }) { movie ->
        ValueRow(movie.name, "Afișează") { viewModel.unhideMovie(movie.id) }
    }
}

private fun LazyListScope.contentFilters(settings: AppSettings, viewModel: SettingsViewModel) {
    item { SectionHeader("Ascunde din calendar", "Categoriile ascunse nu mai apar deloc, nici la seriale, nici la filme.") }
    items(ContentFilter.entries, key = { "filter-${it.name}" }) { filter ->
        val hidden = filter in settings.hiddenCategories
        ListItem(
            selected = hidden,
            onClick = { viewModel.toggleHidden(filter) },
            headlineContent = { Text(filter.label) },
            trailingContent = { Text(if (hidden) "Ascuns" else "Afișat") },
        )
    }
}

private fun LazyListScope.savedShows(settings: AppSettings, viewModel: SettingsViewModel) {
    item {
        SectionHeader(
            "Seriale urmărite",
            if (settings.followedShows.isEmpty()) {
                "Niciunul. Din detaliile unui episod apasă „Urmăresc serialul” ca să apară mereu, indiferent de praguri."
            } else {
                "Apar mereu, indiferent de praguri și filtre. Apasă pe unul ca să nu-l mai urmărești."
            },
        )
    }
    items(settings.followedShows, key = { "followed-${it.id}" }) { show ->
        ValueRow(show.name, "Nu mai urmări") { viewModel.unfollow(show.id) }
    }
    item {
        SectionHeader(
            "Seriale ascunse",
            if (settings.hiddenShows.isEmpty()) {
                "Niciunul. Din detaliile unui episod apasă „Ascunde serialul”."
            } else {
                "Nu mai apar în calendar. Apasă pe unul ca să-l afișezi din nou."
            },
        )
    }
    items(settings.hiddenShows, key = { "hidden-${it.id}" }) { show ->
        ValueRow(show.name, "Afișează") { viewModel.unhide(show.id) }
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        trailingContent = { Text(value) },
    )
}

@Composable
private fun ApiKeySection(
    title: String,
    value: String,
    hasBuiltInKey: Boolean,
    missingHint: String,
    onSave: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    SectionHeader(
        title,
        when {
            value.isNotBlank() -> "Folosești cheia introdusă aici."
            hasBuiltInKey -> "Folosești cheia inclusă la build. Poți pune alta aici."
            else -> missingHint
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Button(onClick = { onSave(draft) }) { Text("Salvează") }
        if (value.isNotBlank()) {
            OutlinedButton(onClick = { onSave("") }) { Text("Șterge") }
        }
    }
    Text(
        text = "Cheia rămâne doar pe dispozitiv.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
