@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ak.vodcalendar.ui.touch.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.ak.vodcalendar.ui.touch.ListItem
import androidx.tv.material3.MaterialTheme
import com.ak.vodcalendar.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.NotificationMode
import com.ak.vodcalendar.simkl.SimklAccount
import com.ak.vodcalendar.simkl.SimklLoginState
import com.ak.vodcalendar.today.TodayChannel
import com.ak.vodcalendar.today.TodayLoader
import com.ak.vodcalendar.today.TodayNotifier
import com.ak.vodcalendar.ui.common.SectionHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.ak.vodcalendar.core.time.RomaniaTime

private val notificationHours = listOf(7, 8, 9, 10, 12, 14, 16, 18, 20)
private val syncTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ro"))

/** Stage 5 settings: Simkl, notifications and the TV channel. */
internal fun LazyListScope.syncSettings(settings: AppSettings, viewModel: SettingsViewModel, container: AppContainer) {
    item(key = "simkl-header") {
        SectionHeader(
            "Simkl",
            "Serialele din „Watching” și „Plan to watch” și filmele din „Plan to watch” apar mereu în calendar, " +
                "ca cele urmărite. Episoadele și filmele văzute sunt marcate și nu mai apar în notificări și în canalul TV.",
        )
    }
    item(key = "simkl") { SimklSection(settings, container) }
    item(key = "key-simkl") {
        ApiKeySection(
            title = "Client ID Simkl",
            value = settings.simklClientId,
            hasBuiltInKey = container.simkl.hasBuiltInClientId,
            missingHint = "Necesar pentru login. Creează o aplicație pe simkl.com/settings/developer/new " +
                "(Redirect URI: urn:ietf:wg:oauth:2.0:oob) și copiază Client ID-ul aici.",
            onSave = viewModel::saveSimklClientId,
        )
    }

    item(key = "notify-header") {
        SectionHeader(
            "Notificări și canal TV",
            "O notificare pe zi, la ora aleasă (ora României), cu ce apare azi. Canalul „Azi apare” de pe ecranul " +
                "principal arată episoadele și filmele zilei, cele urmărite primele.",
        )
    }
    item(key = "notify") { NotificationSection(settings, viewModel, container) }
}

@Composable
private fun SimklSection(settings: AppSettings, container: AppContainer) {
    val simkl = container.simkl
    val account by simkl.account.collectAsStateWithLifecycle(SimklAccount())
    val login by simkl.login.collectAsStateWithLifecycle()
    val syncing by simkl.syncing.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (val state = login) {
            is SimklLoginState.WaitingForUser -> {
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
                OutlinedButton(onClick = simkl::cancelLogin) { Text("Anulează") }
            }
            else -> {
                if (account.connected) {
                    Text("Conectat" + (account.userName?.let { " ca $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    val last = if (account.lastSyncMillis > 0) {
                        Instant.ofEpochMilli(account.lastSyncMillis).atZone(RomaniaTime.ZONE).format(syncTimeFormat)
                    } else {
                        "niciodată"
                    }
                    Text(
                        "Ultima sincronizare: $last · ${settings.simklShows.size} seriale și ${settings.simklMovies.size} filme " +
                            "pe liste · ${account.watchedEpisodes.size} episoade și ${account.watchedMovies.size} filme văzute",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = simkl::syncNow, enabled = !syncing) {
                            Text(if (syncing) "Se sincronizează…" else "Sincronizează acum")
                        }
                        OutlinedButton(onClick = simkl::logout) { Text("Deconectează") }
                    }
                } else {
                    Text("Neconectat", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = simkl::startLogin, enabled = state !is SimklLoginState.Requesting) {
                        Text(if (state is SimklLoginState.Requesting) "Se cere codul…" else "Conectează Simkl")
                    }
                }
                val error = (state as? SimklLoginState.Failed)?.message ?: account.lastError
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun NotificationSection(settings: AppSettings, viewModel: SettingsViewModel, container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canNotify by remember { mutableStateOf(TodayNotifier.canNotify(context)) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = TodayNotifier.canNotify(context)
    }
    val n = settings.notifications

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ListItem(
            selected = false,
            onClick = {
                val next = NotificationMode.entries[(n.mode.ordinal + 1) % NotificationMode.entries.size]
                viewModel.setNotificationMode(next)
                if (next != NotificationMode.OFF && !canNotify && Build.VERSION.SDK_INT >= 33) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            headlineContent = { Text("Notificări") },
            supportingContent = {
                Text(
                    when (n.mode) {
                        NotificationMode.OFF -> "Nicio notificare."
                        NotificationMode.FOLLOWED -> "Câte una pentru fiecare serial / film urmărit (aici sau pe Simkl) care apare azi."
                        NotificationMode.ALL -> "Câte una pentru ce urmărești, plus un rezumat cu tot calendarul zilei."
                    },
                )
            },
            trailingContent = { Text(n.mode.label) },
        )
        if (n.mode != NotificationMode.OFF) {
            ListItem(
                selected = false,
                onClick = { viewModel.setNotificationHour(notificationHours.after(n.hour)) },
                headlineContent = { Text("Ora notificării (ora României)") },
                trailingContent = { Text("%02d:00".format(n.hour)) },
            )
            if (!canNotify) {
                Text(
                    "Notificările sunt blocate pentru aplicație. Permite-le din setările Android ale aplicației.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        ListItem(
            selected = false,
            onClick = viewModel::toggleNotificationMovies,
            headlineContent = { Text("Include filmele (notificări și canal TV)") },
            trailingContent = { Text(if (n.includeMovies) "Da" else "Nu") },
        )
        ListItem(
            selected = false,
            onClick = viewModel::toggleTvChannel,
            headlineContent = { Text("Canalul „Azi apare” pe ecranul principal") },
            supportingContent = {
                Text(
                    if (TodayChannel.isSupported(context)) {
                        "Pe Google TV apare când stai pe iconița aplicației în rândul de aplicații; pe Android TV clasic e un rând separat."
                    } else {
                        "Dispozitivul nu e Android TV."
                    },
                )
            },
            trailingContent = { Text(if (settings.tvChannel) "Da" else "Nu") },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                testMessage = "Se încarcă ziua de azi…"
                scope.launch {
                    testMessage = try {
                        val digest = withContext(Dispatchers.IO) {
                            TodayLoader(container).load(settings)?.also { TodayChannel.update(context, it, settings.tvChannel) }
                        }
                        when {
                            digest == null -> "Lipsește cheia TMDB."
                            else -> {
                                val mode = if (n.mode == NotificationMode.OFF) NotificationMode.ALL else n.mode
                                TodayNotifier.post(context, digest, mode)
                                canNotify = TodayNotifier.canNotify(context)
                                if (digest.isEmpty) "Azi nu apare nimic din calendar." else
                                    "Azi: ${digest.episodes.size} episoade (${digest.followedEpisodes.size} urmărite), ${digest.movies.size} filme." +
                                        if (canNotify) "" else " Notificările sunt blocate, am actualizat doar canalul."
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "Eroare: ${e.message ?: e}"
                    }
                }
            }) { Text("Arată acum ce apare azi") }
            testMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp)) }
        }
    }
}
