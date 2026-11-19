@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.ui.common.NavButton
import com.ak.vodcalendar.ui.settings.SettingsScreen
import com.ak.vodcalendar.ui.today.TodayScreen

enum class Screen(val label: String) {
    TODAY("Azi"),
    SETTINGS("Setări"),
}

@Composable
fun AppRoot(container: AppContainer) {
    var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }

    BackHandler(enabled = screen != Screen.TODAY) { screen = Screen.TODAY }

    Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AK VOD Calendar", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(24.dp))
                Screen.entries.forEach { item ->
                    NavButton(label = item.label, selected = item == screen, onClick = { screen = item })
                }
            }
            Spacer(Modifier.padding(top = 16.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.TODAY -> TodayScreen(container)
                    Screen.SETTINGS -> SettingsScreen(container)
                }
            }
        }
    }
}
