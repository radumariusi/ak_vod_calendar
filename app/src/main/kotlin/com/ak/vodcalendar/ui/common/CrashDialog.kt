@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ak.vodcalendar.ui.touch.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Shown at start after a crash, so the user can photograph the error and send it. */
@Composable
fun CrashDialog(report: String, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // The first lines name the error and where it happened; the rest doesn't fit on a TV.
    val short = report.lines().filter { it.isNotBlank() }.take(14).joinToString("\n")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = 1000.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Aplicația s-a închis data trecută din cauza unei erori", style = MaterialTheme.typography.titleLarge)
            Text(
                "Fă o poză acestui ecran și trimite-o, ca eroarea să poată fi reparată.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(short, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDismiss, modifier = Modifier.focusRequester(focus)) { Text("OK") }
        }
    }
}
