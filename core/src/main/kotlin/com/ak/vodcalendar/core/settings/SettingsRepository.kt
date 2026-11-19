package com.ak.vodcalendar.core.settings

import com.ak.vodcalendar.core.model.AppSettings
import kotlinx.coroutines.flow.Flow

/** Persistent user settings. The Android implementation uses DataStore. */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
