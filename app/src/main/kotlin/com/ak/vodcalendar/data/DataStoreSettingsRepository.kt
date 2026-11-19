package com.ak.vodcalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Stores [AppSettings] as one JSON value, so new fields only need a default. */
class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private val key = stringPreferencesKey("app_settings_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    override val settings: Flow<AppSettings> = context.settingsStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[key]) }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val updated = transform(decode(prefs[key])).normalized()
            prefs[key] = json.encodeToString(AppSettings.serializer(), updated)
        }
    }

    private fun decode(raw: String?): AppSettings {
        val parsed = raw?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
        return (parsed ?: AppSettings()).normalized()
    }
}
