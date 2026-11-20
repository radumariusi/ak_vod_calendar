package com.ak.vodcalendar.wetrakr

import com.ak.vodcalendar.BuildConfig
import com.ak.vodcalendar.core.settings.SettingsRepository
import com.ak.vodcalendar.core.wetrakr.WeTrakrClient
import com.ak.vodcalendar.core.wetrakr.WeTrakrDeviceCode
import com.ak.vodcalendar.core.wetrakr.WeTrakrException
import com.ak.vodcalendar.core.wetrakr.WeTrakrPollResult
import com.ak.vodcalendar.simkl.SimklAccount
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

sealed interface WeTrakrLoginState {
    data object Idle : WeTrakrLoginState
    data object Requesting : WeTrakrLoginState
    /** Show [code] on screen until the user approves it on wetrakr.com/activate. */
    data class WaitingForUser(val code: WeTrakrDeviceCode, val expiresAtMillis: Long) : WeTrakrLoginState
    data class Failed(val message: String) : WeTrakrLoginState
}

/**
 * WeTrakr in the app, next to Simkl: TV login with a code, syncing the "watching" /
 * "waiting" / "planning" lists (they count as followed) and the watched episodes and
 * movies, and marking things watched from the details screens. Access tokens last 7 days
 * and are refreshed on their own with the refresh token.
 */
class WeTrakrManager(
    http: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    val store: WeTrakrStore,
) {
    private val client = WeTrakrClient(http, appVersion = BuildConfig.VERSION_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val syncMutex = Mutex()
    private val refreshMutex = Mutex()
    private var loginJob: Job? = null

    private val _login = MutableStateFlow<WeTrakrLoginState>(WeTrakrLoginState.Idle)
    val login: StateFlow<WeTrakrLoginState> = _login.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val account: Flow<SimklAccount> = store.account

    val hasBuiltInClientId: Boolean get() = BuildConfig.WETRAKR_CLIENT_ID.isNotBlank()

    suspend fun clientId(): String =
        settingsRepository.settings.first().wetrakrClientId.trim().ifBlank { BuildConfig.WETRAKR_CLIENT_ID }

    fun startLogin() {
        loginJob?.cancel()
        loginJob = scope.launch {
            val clientId = clientId()
            if (clientId.isBlank()) {
                _login.value = WeTrakrLoginState.Failed("Lipsește cheia aplicației WeTrakr. Pune-o mai jos, la „Cheie aplicație WeTrakr”.")
                return@launch
            }
            _login.value = WeTrakrLoginState.Requesting
            try {
                val code = client.requestDeviceCode(clientId)
                val expiresAt = System.currentTimeMillis() + code.expiresInSeconds * 1000L
                _login.value = WeTrakrLoginState.WaitingForUser(code, expiresAt)
                var interval = code.intervalSeconds * 1000L
                while (System.currentTimeMillis() < expiresAt) {
                    delay(interval)
                    when (val result = client.pollDeviceCode(clientId, code.deviceCode)) {
                        is WeTrakrPollResult.Authorized -> {
                            val tokens = result.tokens
                            val name = tokens.userName ?: runCatching { client.userName(clientId, tokens.accessToken) }.getOrNull()
                            store.login(tokens.accessToken, tokens.refreshToken, name)
                            _login.value = WeTrakrLoginState.Idle
                            sync(force = true)
                            return@launch
                        }
                        WeTrakrPollResult.SlowDown -> interval += 2_000L
                        WeTrakrPollResult.Pending -> Unit
                        is WeTrakrPollResult.Failed -> {
                            _login.value = WeTrakrLoginState.Failed(result.message)
                            return@launch
                        }
                    }
                }
                _login.value = WeTrakrLoginState.Failed("Codul a expirat. Încearcă din nou.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _login.value = WeTrakrLoginState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        _login.value = WeTrakrLoginState.Idle
    }

    fun logout() {
        scope.launch { clearLogin() }
    }

    private suspend fun clearLogin() {
        store.logout()
        settingsRepository.update { it.copy(wetrakrShows = emptyList(), wetrakrMovies = emptyList()) }
    }

    fun syncNow() {
        scope.launch { sync(force = true) }
    }

    /**
     * Pulls the lists from WeTrakr. Skipped when the last sync is newer than [maxAgeMillis]
     * unless [force]. Returns false only when the sync was tried and failed.
     */
    suspend fun sync(force: Boolean = false, maxAgeMillis: Long = 60 * 60 * 1000L): Boolean = syncMutex.withLock {
        val account = store.current()
        if (account.token == null) return@withLock true
        if (!force && System.currentTimeMillis() - account.lastSyncMillis < maxAgeMillis) return@withLock true
        val clientId = clientId()
        if (clientId.isBlank()) return@withLock true
        _syncing.value = true
        try {
            val library = withToken { client.library(clientId, it) }
            val name = account.userName ?: runCatching { withToken { client.userName(clientId, it) } }.getOrNull()
            settingsRepository.update { it.copy(wetrakrShows = library.followedShows, wetrakrMovies = library.followedMovies) }
            store.saveSync(library.watchedEpisodes, library.watchedMovies, System.currentTimeMillis(), name)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is WeTrakrException && e.unauthorized) clearLogin()
            store.saveError(e.message ?: e.toString())
            false
        } finally {
            _syncing.value = false
        }
    }

    fun setEpisodeWatched(showId: Int, imdbId: String?, season: Int, episode: Int, key: String, watched: Boolean) {
        action(local = { store.setEpisodeWatched(key, it) }, watched = watched) { clientId, token ->
            client.markEpisodeWatched(clientId, token, showId, imdbId, season, episode, watched)
        }
    }

    fun setMovieWatched(movieId: Int, imdbId: String?, watched: Boolean) {
        action(local = { store.setMovieWatched(movieId, it) }, watched = watched) { clientId, token ->
            client.markMovieWatched(clientId, token, movieId, imdbId, watched)
        }
    }

    /** Following a title in the app also puts it on the WeTrakr "planning" list, when connected. */
    fun addToList(isMovie: Boolean, tmdbId: Int, imdbId: String?) {
        scope.launch {
            if (store.current().token == null) return@launch
            try {
                val clientId = clientId()
                withToken { client.addToList(clientId, it, isMovie, tmdbId, imdbId) }
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

    /** Updates the local mark at once, then WeTrakr; puts the mark back if WeTrakr refuses. */
    private fun action(local: suspend (Boolean) -> Unit, watched: Boolean, remote: suspend (String, String) -> Unit) {
        scope.launch {
            if (store.current().token == null) return@launch
            local(watched)
            try {
                val clientId = clientId()
                withToken { remote(clientId, it) }
                _actionError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local(!watched)
                _actionError.value = e.message ?: e.toString()
            }
        }
    }

    /** Runs [block] with the access token; on 401 refreshes it once and tries again. */
    private suspend fun <T> withToken(block: suspend (String) -> T): T {
        val token = store.current().token ?: throw WeTrakrException("WeTrakr nu e conectat", 401)
        return try {
            block(token)
        } catch (e: WeTrakrException) {
            if (!e.unauthorized) throw e
            block(refreshedToken(token))
        }
    }

    private suspend fun refreshedToken(stale: String): String = refreshMutex.withLock {
        // Another call may have refreshed it meanwhile.
        store.current().token?.takeIf { it != stale }?.let { return@withLock it }
        val refresh = store.refreshToken() ?: throw WeTrakrException("WeTrakr: autentificarea a expirat, conectează-te din nou", 401)
        val tokens = client.refresh(clientId(), refresh)
        store.saveTokens(tokens.accessToken, tokens.refreshToken)
        tokens.accessToken
    }
}
