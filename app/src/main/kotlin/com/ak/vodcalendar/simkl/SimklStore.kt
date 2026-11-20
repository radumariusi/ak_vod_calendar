package com.ak.vodcalendar.simkl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.simklStore: DataStore<Preferences> by preferencesDataStore(name = "simkl")

/** The Simkl login and what the user watched, kept apart from the settings JSON. */
data class SimklAccount(
    val token: String? = null,
    val userName: String? = null,
    val lastSyncMillis: Long = 0L,
    val lastError: String? = null,
    /** Watched episodes as `EpisodeEntry.key` ("showId-season-episode"). */
    val watchedEpisodes: Set<String> = emptySet(),
    val watchedMovies: Set<Int> = emptySet(),
) {
    val connected: Boolean get() = !token.isNullOrBlank()
}

class SimklStore(private val context: Context) {
    private val token = stringPreferencesKey("token")
    private val user = stringPreferencesKey("user")
    private val lastSync = longPreferencesKey("last_sync")
    private val lastError = stringPreferencesKey("last_error")
    private val episodes = stringSetPreferencesKey("watched_episodes")
    private val movies = stringSetPreferencesKey("watched_movies")

    val account: Flow<SimklAccount> = context.simklStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            SimklAccount(
                token = p[token],
                userName = p[user],
                lastSyncMillis = p[lastSync] ?: 0L,
                lastError = p[lastError],
                watchedEpisodes = p[episodes].orEmpty(),
                watchedMovies = p[movies].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
            )
        }
        .distinctUntilChanged()

    suspend fun current(): SimklAccount = account.first()

    suspend fun login(accessToken: String, userName: String?) {
        context.simklStore.edit {
            it.clear()
            it[token] = accessToken
            if (userName != null) it[user] = userName
        }
    }

    suspend fun logout() {
        context.simklStore.edit { it.clear() }
    }

    suspend fun saveSync(watchedEpisodes: Set<String>, watchedMovies: Set<Int>, at: Long, userName: String?) {
        context.simklStore.edit {
            it[episodes] = watchedEpisodes
            it[movies] = watchedMovies.map(Int::toString).toSet()
            it[lastSync] = at
            it.remove(lastError)
            if (userName != null) it[user] = userName
        }
    }

    suspend fun saveError(message: String?) {
        context.simklStore.edit { if (message == null) it.remove(lastError) else it[lastError] = message }
    }

    suspend fun setEpisodeWatched(key: String, watched: Boolean) {
        context.simklStore.edit { it[episodes] = it[episodes].orEmpty().let { set -> if (watched) set + key else set - key } }
    }

    suspend fun setMovieWatched(movieId: Int, watched: Boolean) {
        context.simklStore.edit {
            val id = movieId.toString()
            it[movies] = it[movies].orEmpty().let { set -> if (watched) set + id else set - id }
        }
    }
}
