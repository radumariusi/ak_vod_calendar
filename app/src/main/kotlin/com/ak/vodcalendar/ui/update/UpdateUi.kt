@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ak.vodcalendar.ui.touch.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.ak.vodcalendar.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import com.ak.vodcalendar.update.UpdateManager
import com.ak.vodcalendar.update.UpdateState

/** Button in the top bar, shown only while a new version is available or being installed. */
@Composable
fun UpdateBadge(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is UpdateState.Available -> Button(onClick = { manager.downloadAndInstall(s.release) }) {
            Text("⬆ Actualizează la ${s.release.versionName}")
        }
        is UpdateState.Downloading -> OutlinedButton(onClick = {}) { Text(downloadLabel(s)) }
        is UpdateState.NeedsPermission -> Button(onClick = { manager.downloadAndInstall(s.release) }) {
            Text("⬆ Instalează ${s.release.versionName}")
        }
        else -> Unit
    }
}

/** "Actualizări" section in Settings. */
@Composable
fun UpdateSettings(manager: UpdateManager) {
    val state by manager.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Versiunea instalată: ${manager.currentVersion}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (val s = state) {
                is UpdateState.Available -> Button(onClick = { manager.downloadAndInstall(s.release) }) {
                    Text("Descarcă și instalează ${s.release.versionName}")
                }
                is UpdateState.NeedsPermission -> Button(onClick = { manager.downloadAndInstall(s.release) }) {
                    Text("Am permis, instalează ${s.release.versionName}")
                }
                is UpdateState.Failed -> Button(onClick = { s.release?.let(manager::downloadAndInstall) ?: manager.check() }) {
                    Text(if (s.release != null) "Încearcă din nou" else "Caută actualizări")
                }
                is UpdateState.Downloading -> OutlinedButton(onClick = {}) { Text(downloadLabel(s)) }
                is UpdateState.Checking -> OutlinedButton(onClick = {}) { Text("Se verifică…") }
                else -> OutlinedButton(onClick = { manager.check() }) { Text("Caută actualizări") }
            }
        }
        val message = when (val s = state) {
            UpdateState.Idle, UpdateState.Checking -> "Actualizările vin din „Latest release” pe GitHub. Aplicația verifică singură la pornire."
            is UpdateState.UpToDate -> if (s.latest == null) "Nu există încă nicio versiune publicată." else "Ai ultima versiune (${s.latest})."
            is UpdateState.Available -> "Versiune nouă: ${s.release.title}" + s.release.notes.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
            is UpdateState.Downloading -> "Se descarcă ${s.release.versionName}…"
            is UpdateState.NeedsPermission ->
                "Android cere voie: în ecranul deschis, permite „Instalează aplicații necunoscute” pentru AK VOD Calendar, apoi apasă din nou. " +
                    "Dacă ecranul nu s-a deschis: Setări TV → Aplicații → Acces special → Instalare aplicații necunoscute."
            is UpdateState.Installing -> "S-a deschis instalarea. Confirmă pe ecran; aplicația se repornește cu noua versiune."
            is UpdateState.Failed -> s.message
        }
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
        )
    }
}

private fun downloadLabel(s: UpdateState.Downloading): String =
    s.progress?.let { "Se descarcă ${(it * 100).toInt()}%" } ?: "Se descarcă…"
