@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.time.RomaniaTime
import com.ak.vodcalendar.simkl.SimklAccount
import com.ak.vodcalendar.ui.common.SectionHeader
import com.ak.vodcalendar.wetrakr.WeTrakrLoginState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val weTrakrSyncFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ro"))

/** WeTrakr login and its app key, under Simkl in the sync settings. */
internal fun LazyListScope.weTrakrSettings(settings: AppSettings, viewModel: SettingsViewModel, container: AppContainer) {
    item(key = "wetrakr-header") {
        SectionHeader(
            "WeTrakr",
            "Merge alături de Simkl. Serialele din „Watching”, „Waiting” și „Planning” și filmele din „Planning” apar " +
                "mereu în calendar. Ce marchezi „văzut” aici ajunge pe ambele servicii, dacă sunt conectate.",
        )
    }
    item(key = "wetrakr") { WeTrakrSection(settings, container) }
    item(key = "key-wetrakr") {
        ApiKeySection(
            title = "Cheie aplicație WeTrakr",
            value = settings.wetrakrClientId,
            hasBuiltInKey = container.wetrakr.hasBuiltInClientId,
            missingHint = "Necesară pentru login. Pe wetrakr.com intră la Setări → API, creează o aplicație " +
                "și copiază cheia (Client ID) aici.",
            onSave = viewModel::saveWeTrakrClientId,
        )
    }
}

@Composable
private fun WeTrakrSection(settings: AppSettings, container: AppContainer) {
    val wetrakr = container.wetrakr
    val account by wetrakr.account.collectAsStateWithLifecycle(SimklAccount())
    val login by wetrakr.login.collectAsStateWithLifecycle()
    val syncing by wetrakr.syncing.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val state = login) {
            is WeTrakrLoginState.WaitingForUser -> {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(state) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                val left = ((state.expiresAtMillis - now) / 1000).coerceAtLeast(0)
                Text("Pe telefon sau calculator intră pe", style = MaterialTheme.typography.bodyLarge)
                Text(
                    state.code.verificationUrl.removePrefix("https://").removeSuffix("/"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text("și scrie codul:", style = MaterialTheme.typography.bodyLarge)
                Text(state.code.userCode, style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 8.sp))
                Text(
                    "Aștept confirmarea… expiră în %d:%02d".format(left / 60, left % 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = wetrakr::cancelLogin) { Text("Anulează") }
            }
            else -> {
                if (account.connected) {
                    Text("Conectat" + (account.userName?.let { " ca $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    val last = if (account.lastSyncMillis > 0) {
                        Instant.ofEpochMilli(account.lastSyncMillis).atZone(RomaniaTime.ZONE).format(weTrakrSyncFormat)
                    } else {
                        "niciodată"
                    }
                    Text(
                        "Ultima sincronizare: $last · ${settings.wetrakrShows.size} seriale și ${settings.wetrakrMovies.size} filme " +
                            "pe liste · ${account.watchedEpisodes.size} episoade și ${account.watchedMovies.size} filme văzute",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = wetrakr::syncNow, enabled = !syncing) {
                            Text(if (syncing) "Se sincronizează…" else "Sincronizează acum")
                        }
                        OutlinedButton(onClick = wetrakr::logout) { Text("Deconectează") }
                    }
                } else {
                    Text("Neconectat", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = wetrakr::startLogin, enabled = state !is WeTrakrLoginState.Requesting) {
                        Text(if (state is WeTrakrLoginState.Requesting) "Se cere codul…" else "Conectează WeTrakr")
                    }
                }
                val error = (state as? WeTrakrLoginState.Failed)?.message ?: account.lastError
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
