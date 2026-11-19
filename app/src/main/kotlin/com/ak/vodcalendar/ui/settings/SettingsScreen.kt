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
import com.ak.vodcalendar.core.model.StreamingCatalog
import com.ak.vodcalendar.ui.common.InfoMessage
import com.ak.vodcalendar.ui.common.SectionHeader
import java.util.Locale

private val ratingSteps = listOf(5.0, 5.5, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5)
private val imdbVoteSteps = listOf(500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000)
private val tmdbVoteSteps = listOf(20, 50, 100, 300, 500, 1_000, 2_000)

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
        contentFilters(current, viewModel)
        item { TmdbKeySection(current, container.hasBuiltInTmdbKey, viewModel::saveTmdbKey) }
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
            "Apasă pe un rând ca să schimbi valoarea. Se aplică din etapa 2 (IMDb prin OMDb).",
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
    item(key = "th-anticipated") {
        ValueRow("Seriale noi doar din listele „mult așteptate”", if (t.newShowsOnlyIfAnticipated) "Da" else "Nu") {
            viewModel.updateThresholds { it.copy(newShowsOnlyIfAnticipated = !it.newShowsOnlyIfAnticipated) }
        }
    }
}

private fun LazyListScope.contentFilters(settings: AppSettings, viewModel: SettingsViewModel) {
    item { SectionHeader("Ascunde din calendar", "Categoriile ascunse nu mai apar deloc în calendar.") }
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
private fun TmdbKeySection(settings: AppSettings, hasBuiltInKey: Boolean, onSave: (String) -> Unit) {
    var draft by remember(settings.tmdbApiKey) { mutableStateOf(settings.tmdbApiKey) }
    SectionHeader(
        "Cheie TMDB",
        when {
            settings.tmdbApiKey.isNotBlank() -> "Folosești cheia introdusă aici."
            hasBuiltInKey -> "Folosești cheia inclusă la build. Poți pune alta aici."
            else -> "Nu există cheie. Introdu cheia API v3 sau tokenul v4 de pe themoviedb.org."
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
        if (settings.tmdbApiKey.isNotBlank()) {
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
