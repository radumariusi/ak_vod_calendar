package com.ak.vodcalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ak.vodcalendar.ui.AppRoot
import com.ak.vodcalendar.ui.theme.AkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AkApplication).container
        setContent {
            AkTheme {
                AppRoot(container)
            }
        }
    }
}
