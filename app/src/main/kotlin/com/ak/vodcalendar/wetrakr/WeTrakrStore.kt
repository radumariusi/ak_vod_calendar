package com.ak.vodcalendar.wetrakr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ak.vodcalendar.simkl.SimklAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.wetrakrStore: DataStore<Preferences> by preferencesDataStore(name = "wetrakr")

/**
 * The WeTrakr login (access + refresh token) and what the user watched. The account has
 * the same shape as Simkl's, so the "văzut" marks of both services are read alike.
 */
class WeTrakrStore(private val context: Context) {
    private val token = stringPreferencesKey("token")
    private val refresh = stringPreferencesKey("refresh_token")
    private val user = stringPreferencesKey("user")
    private val lastSync = longPreferencesKey("last_sync")
    private val lastError = stringPreferencesKey("last_error")
    private val episodes = stringSetPreferencesKey("watched_episodes")
    private val movies = stringSetPreferencesKey("watched_movies")

    private val prefs: Flow<Preferences> = context.wetrakrStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val account: Flow<SimklAccount> = prefs
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

    suspend fun refreshToken(): String? = prefs.first()[refresh]

    suspend fun login(accessToken: String, refreshToken: String?, userName: String?) {
        context.wetrakrStore.edit {
            it.clear()
            it[token] = accessToken
            if (refreshToken != null) it[refresh] = refreshToken
            if (userName != null) it[user] = userName
        }
    }

    /** Keeps the watched marks; only the tokens change. */
    suspend fun saveTokens(accessToken: String, refreshToken: String?) {
        context.wetrakrStore.edit {
            it[token] = accessToken
            if (refreshToken != null) it[refresh] = refreshToken
        }
    }

    suspend fun logout() {
        context.wetrakrStore.edit { it.clear() }
    }

    suspend fun saveSync(watchedEpisodes: Set<String>, watchedMovies: Set<Int>, at: Long, userName: String?) {
        context.wetrakrStore.edit {
            it[episodes] = watchedEpisodes
            it[movies] = watchedMovies.map(Int::toString).toSet()
            it[lastSync] = at
            it.remove(lastError)
            if (userName != null) it[user] = userName
        }
    }

    suspend fun saveError(message: String?) {
        context.wetrakrStore.edit { if (message == null) it.remove(lastError) else it[lastError] = message }
    }

    suspend fun setEpisodeWatched(key: String, watched: Boolean) {
        context.wetrakrStore.edit { it[episodes] = it[episodes].orEmpty().let { set -> if (watched) set + key else set - key } }
    }

    suspend fun setMovieWatched(movieId: Int, watched: Boolean) {
        context.wetrakrStore.edit {
            val id = movieId.toString()
            it[movies] = it[movies].orEmpty().let { set -> if (watched) set + id else set - id }
        }
    }
}
