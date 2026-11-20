package com.ak.vodcalendar

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.ak.vodcalendar.today.TodayWorker
import com.ak.vodcalendar.ui.AppRoot
import com.ak.vodcalendar.ui.theme.AkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Phones: draw behind light-on-dark system bars (the app is always dark). No effect on TV.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as AkApplication).container
        if (savedInstanceState == null) {
            // Fresh "Azi apare" channel and Simkl lists (if older than an hour) on every start.
            runCatching { TodayWorker.refreshNow(this) }
        }
        setContent {
            AkTheme {
                AppRoot(container)
            }
        }
    }
}
