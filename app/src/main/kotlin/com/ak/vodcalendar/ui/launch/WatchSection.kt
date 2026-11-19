@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.launch

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.launch.ContentRef
import com.ak.vodcalendar.core.launch.ContentType
import com.ak.vodcalendar.core.launch.LaunchPlanner
import com.ak.vodcalendar.core.launch.LaunchPrecision
import com.ak.vodcalendar.core.launch.LaunchTarget
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.ProviderAvailability
import com.ak.vodcalendar.core.model.StreamingApp
import com.ak.vodcalendar.launch.ContentLauncher
import com.ak.vodcalendar.launch.LaunchResult

/**
 * "Deschide în Netflix" for each installed app that has the title (in the country set
 * for the app), the default alternative player from Settings, and "Deschide cu…".
 *
 * @param missingService the service the release is on when the user doesn't have it,
 *   so the screen can say why the alternative player is offered first.
 */
@Composable
fun WatchSection(
    ref: ContentRef,
    wikidataId: String?,
    availability: List<ProviderAvailability>,
    preferred: StreamingApp?,
    missingService: StreamingApp?,
    settings: AppSettings,
    container: AppContainer,
    firstFocus: FocusRequester,
) {
    val context = LocalContext.current
    val launcher = remember { ContentLauncher(context) }
    val resolved by produceState(ref, ref, wikidataId) {
        value = ref
        if (wikidataId != null) value = ref.copy(serviceIds = container.streamingIds.ids(wikidataId, ref.type))
    }
    val streaming = LaunchPlanner.streamingTargets(resolved, settings, availability, preferred).take(3)
    val defaultPlayer = LaunchPlanner.defaultPlayerTarget(resolved, settings)
    var chooserOpen by remember { mutableStateOf(false) }

    fun run(target: LaunchTarget) {
        val message = try {
            when (val result = launcher.launch(target)) {
                is LaunchResult.Opened -> openedMessage(result, resolved)
                is LaunchResult.NotAvailable -> "${target.label} nu e instalată pe TV (sau nu acceptă linkul)."
            }
        } catch (e: Throwable) {
            // A bad link must never close the calendar.
            "Nu am putut deschide ${target.label}: ${e.javaClass.simpleName}"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
        // The first button is the most useful one: an installed app, else the default player, else the chooser.
        val buttons = buildList {
            streaming.forEach { add(WatchButton("▶ ${it.label}", primary = true) { run(it) }) }
            if (defaultPlayer != null) {
                add(WatchButton(if (streaming.isEmpty()) "▶ ${defaultPlayer.label}" else defaultPlayer.label, primary = streaming.isEmpty()) { run(defaultPlayer) })
            }
            add(WatchButton("Deschide cu…", primary = isEmpty()) { chooserOpen = true })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            buttons.forEachIndexed { index, b ->
                val modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                if (b.primary) {
                    Button(onClick = b.onClick, modifier = modifier) { Text(b.label) }
                } else {
                    OutlinedButton(onClick = b.onClick, modifier = modifier) { Text(b.label) }
                }
            }
        }
        Text(
            text = watchNote(resolved, streaming, defaultPlayer, missingService),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(900.dp),
        )
    }

    if (chooserOpen) {
        ChooserDialog(
            targets = LaunchPlanner.chooserTargets(resolved),
            launcher = launcher,
            onPick = { chooserOpen = false; run(it) },
            onDismiss = { chooserOpen = false },
        )
    }
}

private class WatchButton(val label: String, val primary: Boolean, val onClick: () -> Unit)

@Composable
private fun ChooserDialog(
    targets: List<LaunchTarget>,
    launcher: ContentLauncher,
    onPick: (LaunchTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    // Installed apps first; the check runs once per dialog.
    val rows = remember(targets) { targets.map { it to launcher.canLaunch(it) }.sortedByDescending { it.second } }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Deschide cu…", style = MaterialTheme.typography.titleLarge)
            Text(
                "Playerul implicit se alege din Setări.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows, key = { it.first.id }) { (target, available) ->
                    val modifier = if (target == rows.first().first) Modifier.focusRequester(first) else Modifier
                    ListItem(
                        selected = false,
                        onClick = { onPick(target) },
                        headlineContent = { Text(target.label) },
                        supportingContent = {
                            Text(if (available) target.hint else "nu e instalată")
                        },
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

private fun openedMessage(result: LaunchResult.Opened, ref: ContentRef): String {
    val label = result.target.label
    val episode = ref.episodeCode?.let { " $it" }.orEmpty()
    return when (result.precision) {
        LaunchPrecision.EPISODE -> "$label: deschis la episod$episode."
        LaunchPrecision.TITLE -> if (ref.isEpisode) "$label: pagina serialului. Alege$episode." else "$label: pagina titlului."
        LaunchPrecision.SEARCH -> "$label: căutare după „${ref.title}”."
        LaunchPrecision.APP -> "Am deschis $label. Caută „${ref.title}”$episode."
    }
}

private fun watchNote(
    ref: ContentRef,
    streaming: List<LaunchTarget>,
    defaultPlayer: LaunchTarget?,
    missingService: StreamingApp?,
): String {
    val what = if (ref.type == ContentType.MOVIE) "filmul" else "serialul"
    val parts = buildList {
        if (streaming.isEmpty()) {
            add(
                if (missingService != null) "E pe ${missingService.name}, pe care nu o ai (sau e setată pe altă țară)."
                else "Nu e pe aplicațiile tale, după TMDB / JustWatch.",
            )
        } else {
            streaming.forEach { t ->
                add("${t.label}: " + if (t.bestPrecision == LaunchPrecision.TITLE) "deschide pagina titlului" else "se deschide aplicația, cauți $what")
            }
        }
        if (defaultPlayer != null) add("${defaultPlayer.label}: ${defaultPlayer.hint}")
        else if (streaming.isEmpty()) add("Alege un player alternativ (implicit se setează în Setări)")
    }
    return parts.joinToString(" · ")
}
