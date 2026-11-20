package com.ak.vodcalendar.simkl

import com.ak.vodcalendar.BuildConfig
import com.ak.vodcalendar.core.settings.SettingsRepository
import com.ak.vodcalendar.core.simkl.SimklClient
import com.ak.vodcalendar.core.simkl.SimklDeviceCode
import com.ak.vodcalendar.core.simkl.SimklException
import com.ak.vodcalendar.core.simkl.SimklPollResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

sealed interface SimklLoginState {
    data object Idle : SimklLoginState
    data object Requesting : SimklLoginState
    /** Show [code] on screen until the user approves it on simkl.com/pin. */
    data class WaitingForUser(val code: SimklDeviceCode, val expiresAtMillis: Long) : SimklLoginState
    data class Failed(val message: String) : SimklLoginState
}

/**
 * Simkl in the app: TV login with a PIN, syncing the "Watching" / "Plan to watch"
 * lists (they count as followed) and the watched episodes and movies, and marking
 * things watched from the details screens.
 */
class SimklManager(
    http: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    val store: SimklStore,
) {
    private val client = SimklClient(http, appVersion = BuildConfig.VERSION_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val syncMutex = Mutex()
    private var loginJob: Job? = null

    private val _login = MutableStateFlow<SimklLoginState>(SimklLoginState.Idle)
    val login: StateFlow<SimklLoginState> = _login.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Last error of a "mark watched" / "add to list" action, for a short message on screen. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val account: Flow<SimklAccount> = store.account

    val hasBuiltInClientId: Boolean get() = BuildConfig.SIMKL_CLIENT_ID.isNotBlank()

    suspend fun clientId(): String =
        settingsRepository.settings.first().simklClientId.trim().ifBlank { BuildConfig.SIMKL_CLIENT_ID }

    fun startLogin() {
        loginJob?.cancel()
        loginJob = scope.launch {
            val clientId = clientId()
            if (clientId.isBlank()) {
                _login.value = SimklLoginState.Failed("Lipsește Client ID-ul Simkl. Pune-l mai jos, la „Client ID Simkl”.")
                return@launch
            }
            _login.value = SimklLoginState.Requesting
            try {
                val code = client.requestDeviceCode(clientId)
                val expiresAt = System.currentTimeMillis() + code.expiresInSeconds * 1000L
                _login.value = SimklLoginState.WaitingForUser(code, expiresAt)
                var interval = code.intervalSeconds * 1000L
                while (System.currentTimeMillis() < expiresAt) {
                    delay(interval)
                    when (val result = client.pollDeviceCode(clientId, code.userCode)) {
                        is SimklPollResult.Authorized -> {
                            val name = runCatching { client.userName(clientId, result.accessToken) }.getOrNull()
                            store.login(result.accessToken, name)
                            _login.value = SimklLoginState.Idle
                            sync(force = true)
                            return@launch
                        }
                        SimklPollResult.SlowDown -> interval += 2_000L
                        SimklPollResult.Pending -> Unit
                    }
                }
                _login.value = SimklLoginState.Failed("Codul a expirat. Încearcă din nou.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _login.value = SimklLoginState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        _login.value = SimklLoginState.Idle
    }

    fun logout() {
        scope.launch {
            store.logout()
            settingsRepository.update { it.copy(simklShows = emptyList(), simklMovies = emptyList()) }
        }
    }

    fun syncNow() {
        scope.launch { sync(force = true) }
    }

    /**
     * Pulls the lists from Simkl. Skipped when the last sync is newer than [maxAgeMillis]
     * unless [force]. Returns false only when the sync was tried and failed.
     */
    suspend fun sync(force: Boolean = false, maxAgeMillis: Long = 60 * 60 * 1000L): Boolean = syncMutex.withLock {
        val account = store.current()
        val token = account.token ?: return@withLock true
        if (!force && System.currentTimeMillis() - account.lastSyncMillis < maxAgeMillis) return@withLock true
        val clientId = clientId()
        if (clientId.isBlank()) return@withLock true
        _syncing.value = true
        try {
            val library = client.library(clientId, token)
            val name = account.userName ?: runCatching { client.userName(clientId, token) }.getOrNull()
            settingsRepository.update { it.copy(simklShows = library.followedShows, simklMovies = library.followedMovies) }
            store.saveSync(library.watchedEpisodes, library.watchedMovies, System.currentTimeMillis(), name)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is SimklException && e.unauthorized) {
                store.logout()
                settingsRepository.update { it.copy(simklShows = emptyList(), simklMovies = emptyList()) }
            }
            store.saveError(e.message ?: e.toString())
            false
        } finally {
            _syncing.value = false
        }
    }

    fun setEpisodeWatched(showId: Int, imdbId: String?, season: Int, episode: Int, key: String, watched: Boolean) {
        action(
            local = { store.setEpisodeWatched(key, it) },
            watched = watched,
        ) { clientId, token -> client.markEpisodeWatched(clientId, token, showId, imdbId, season, episode, watched) }
    }

    fun setMovieWatched(movieId: Int, imdbId: String?, watched: Boolean) {
        action(
            local = { store.setMovieWatched(movieId, it) },
            watched = watched,
        ) { clientId, token -> client.markMovieWatched(clientId, token, movieId, imdbId, watched) }
    }

    /** Following a title in the app also puts it on the Simkl list, when connected. */
    fun addToList(isMovie: Boolean, tmdbId: Int, imdbId: String?) {
        scope.launch {
            val token = store.current().token ?: return@launch
            try {
                client.addToList(clientId(), token, isMovie, tmdbId, imdbId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionError.value = e.message
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    /** Updates the local mark at once, then Simkl; puts the mark back if Simkl refuses. */
    private fun action(local: suspend (Boolean) -> Unit, watched: Boolean, remote: suspend (String, String) -> Unit) {
        scope.launch {
            val token = store.current().token ?: return@launch
            local(watched)
            try {
                remote(clientId(), token)
                _actionError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local(!watched)
                _actionError.value = e.message ?: e.toString()
            }
        }
    }
}
