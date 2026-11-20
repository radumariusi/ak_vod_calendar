package com.ak.vodcalendar.core.wetrakr

import com.ak.vodcalendar.core.model.SavedShow
import com.ak.vodcalendar.core.simkl.SimklLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The code shown on the TV while the user approves the app on wetrakr.com/activate. */
data class WeTrakrDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

data class WeTrakrTokens(val accessToken: String, val refreshToken: String?, val userName: String? = null)

sealed interface WeTrakrPollResult {
    data class Authorized(val tokens: WeTrakrTokens) : WeTrakrPollResult
    /** The user hasn't typed the code yet. */
    data object Pending : WeTrakrPollResult
    /** Polled too fast: wait longer. */
    data object SlowDown : WeTrakrPollResult
    /** Expired, denied or unknown code: stop and show [message]. */
    data class Failed(val message: String) : WeTrakrPollResult
}

class WeTrakrException(message: String, val code: Int = 0) : Exception(message) {
    /** The token is no longer valid: refresh it, or log in again. */
    val unauthorized: Boolean get() = code == 401
}

/**
 * WeTrakr API (https://api.wetrakr.com, docs at https://api.wetrakr.com/#/): device-code
 * login for TVs, reading the user's tracking lists and marking things watched.
 *
 * Every call carries `wetrakr-api-version: 1` and the app key (`wetrakr-api-key`).
 * Titles are sent and read by TMDB id (`ids.tmdb`), never by WeTrakr id.
 */
class WeTrakrClient(
    private val http: OkHttpClient,
    baseUrl: String = DEFAULT_BASE_URL,
    private val appVersion: String = "1",
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    /** Step 1 of the TV login. */
    suspend fun requestDeviceCode(clientId: String): WeTrakrDeviceCode {
        val response = call(url("oauth/device/code"), clientId, null, buildJsonObject { put("client_id", clientId) })
        if (!response.ok) {
            throw WeTrakrException(
                when (response.code) {
                    401 -> "WeTrakr nu recunoaște cheia aplicației. Verifică „Cheie aplicație WeTrakr”."
                    423 -> "Aplicația WeTrakr e suspendată."
                    429 -> "Prea multe coduri cerute. Mai încearcă peste 15 minute."
                    else -> response.errorMessage()
                },
                response.code,
            )
        }
        val obj = response.body as? JsonObject ?: throw WeTrakrException("WeTrakr nu a dat un cod")
        return WeTrakrDeviceCode(
            deviceCode = obj.string("device_code") ?: throw WeTrakrException("WeTrakr nu a dat un cod"),
            userCode = obj.string("user_code") ?: throw WeTrakrException("WeTrakr nu a dat un cod"),
            verificationUrl = obj.string("verification_url") ?: obj.string("verification_uri") ?: DEFAULT_VERIFICATION_URL,
            expiresInSeconds = obj.int("expires_in") ?: 600,
            intervalSeconds = (obj.int("interval") ?: 5).coerceAtLeast(1),
        )
    }

    /** Step 2: ask, every [WeTrakrDeviceCode.intervalSeconds], whether the user approved. */
    suspend fun pollDeviceCode(clientId: String, deviceCode: String): WeTrakrPollResult {
        val body = buildJsonObject {
            put("code", deviceCode)
            put("device_code", deviceCode)
            put("client_id", clientId)
        }
        val response = call(url("oauth/device/token"), clientId, null, body)
        if (response.ok) {
            val tokens = (response.body as? JsonObject)?.tokens()
                ?: return WeTrakrPollResult.Failed("WeTrakr nu a trimis tokenul")
            return WeTrakrPollResult.Authorized(tokens)
        }
        return when (response.code) {
            400 -> {
                val error = response.errorCode().orEmpty()
                if (error.isBlank() || error.contains("pending", ignoreCase = true)) WeTrakrPollResult.Pending
                else WeTrakrPollResult.Failed(response.errorMessage())
            }
            429 -> WeTrakrPollResult.SlowDown
            410 -> WeTrakrPollResult.Failed("Codul a expirat. Încearcă din nou.")
            418 -> WeTrakrPollResult.Failed("Ai refuzat accesul pe WeTrakr.")
            404 -> WeTrakrPollResult.Failed("WeTrakr nu recunoaște codul. Încearcă din nou.")
            else -> WeTrakrPollResult.Failed(response.errorMessage())
        }
    }

    /** New access + refresh token. Throws [WeTrakrException] with code 401 when the user must log in again. */
    suspend fun refresh(clientId: String, refreshToken: String): WeTrakrTokens {
        val response = call(url("oauth/token/refresh"), clientId, null, buildJsonObject { put("refresh_token", refreshToken) })
        if (!response.ok) throw WeTrakrException(response.errorMessage(), if (response.code in 400..401) 401 else response.code)
        return (response.body as? JsonObject)?.tokens() ?: throw WeTrakrException("WeTrakr nu a trimis tokenul", 401)
    }

    /** The account name, to show in Settings. */
    suspend fun userName(clientId: String, token: String): String? {
        val obj = get(url("account/settings"), clientId, token) as? JsonObject ?: return null
        val user = obj["user"] as? JsonObject ?: obj
        return user.string("username") ?: user.string("name") ?: user.string("display_name")
    }

    /**
     * Series in "watching" / "waiting" / "planning", movies in "planning", plus the watched
     * episodes and movies. Same shape as Simkl's, so the app treats both alike.
     */
    suspend fun library(clientId: String, token: String): SimklLibrary {
        val followedShows = LinkedHashMap<Int, SavedShow>()
        for (status in FOLLOWED_SHOW_STATUSES) {
            for (row in list(clientId, token, status, "shows")) {
                val media = row.media("show")
                val tmdb = media.tmdbId() ?: continue
                followedShows.putIfAbsent(tmdb, SavedShow(tmdb, media.string("title") ?: "TMDB $tmdb"))
            }
        }
        val followedMovies = LinkedHashMap<Int, SavedShow>()
        for (row in list(clientId, token, "planning", "movies")) {
            val media = row.media("movie")
            val tmdb = media.tmdbId() ?: continue
            followedMovies.putIfAbsent(tmdb, SavedShow(tmdb, media.string("title") ?: "TMDB $tmdb"))
        }

        val watchedEpisodes = HashSet<String>()
        for (row in compactList(clientId, token, "watched", "episodes")) {
            val showTmdb = (row["show_ids"] as? JsonObject)?.int("tmdb")
                ?: (row["show"] as? JsonObject)?.tmdbId()
                ?: continue
            val season = row.int("season_number") ?: (row["episode"] as? JsonObject)?.int("season_number") ?: continue
            val number = row.int("number") ?: (row["episode"] as? JsonObject)?.int("number") ?: continue
            watchedEpisodes += SimklLibrary.episodeKey(showTmdb, season, number)
        }
        val watchedMovies = HashSet<Int>()
        for (row in compactList(clientId, token, "watched", "movies")) {
            row.media("movie").tmdbId()?.let { watchedMovies += it }
        }
        return SimklLibrary(followedShows.values.toList(), followedMovies.values.toList(), watchedEpisodes, watchedMovies)
    }

    suspend fun markEpisodeWatched(clientId: String, token: String, showTmdbId: Int, imdbId: String?, season: Int, episode: Int, watched: Boolean = true) {
        val body = buildJsonObject {
            put("shows", buildJsonArray {
                add(buildJsonObject {
                    put("ids", ids(showTmdbId, imdbId))
                    put("seasons", buildJsonArray {
                        add(buildJsonObject {
                            put("number", season)
                            put("episodes", buildJsonArray {
                                add(buildJsonObject {
                                    put("number", episode)
                                    put("status", "watched")
                                })
                            })
                        })
                    })
                })
            })
        }
        write(if (watched) "sync/tracking" else "sync/tracking/remove", clientId, token, body)
    }

    suspend fun markMovieWatched(clientId: String, token: String, tmdbId: Int, imdbId: String?, watched: Boolean = true) {
        val body = buildJsonObject {
            put("movies", buildJsonArray {
                add(buildJsonObject {
                    put("ids", ids(tmdbId, imdbId))
                    put("status", "watched")
                })
            })
        }
        write(if (watched) "sync/tracking" else "sync/tracking/remove", clientId, token, body)
    }

    /** Puts a followed series or movie on the "planning" list (plan to watch). */
    suspend fun addToList(clientId: String, token: String, isMovie: Boolean, tmdbId: Int, imdbId: String?) {
        val body = buildJsonObject {
            put(if (isMovie) "movies" else "shows", buildJsonArray {
                add(buildJsonObject {
                    put("ids", ids(tmdbId, imdbId))
                    put("status", "planning")
                })
            })
        }
        write("sync/tracking", clientId, token, body)
    }

    /** All rows of a list, 100 per page. */
    private suspend fun list(clientId: String, token: String, status: String, target: String): List<JsonObject> {
        val rows = ArrayList<JsonObject>()
        var page = 1
        while (page <= MAX_PAGES) {
            val response = call(
                url("sync/tracking/$status/$target") {
                    addQueryParameter("page", page.toString())
                    addQueryParameter("limit", "100")
                },
                clientId, token, null,
            ).orThrow()
            val items = response.body.rows()
            rows += items
            val pageCount = response.header("X-Pagination-Page-Count")?.toIntOrNull()
            if (items.isEmpty() || (pageCount != null && page >= pageCount) || (pageCount == null && items.size < 100)) break
            page++
        }
        return rows
    }

    /** Compact rows (type, id, ids and the list's fields), up to 5,000 per page, walked with `after`. */
    private suspend fun compactList(clientId: String, token: String, status: String, target: String): List<JsonObject> {
        val rows = ArrayList<JsonObject>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val response = call(
                url("sync/tracking/$status/$target") {
                    addQueryParameter("compact", "true")
                    addQueryParameter("limit", "5000")
                    after?.let { addQueryParameter("after", it) }
                },
                clientId, token, null,
            ).orThrow()
            rows += response.body.rows()
            after = response.header("X-Pagination-Next")?.takeIf { it.isNotBlank() } ?: return rows
        }
        return rows
    }

    private fun ids(tmdbId: Int, imdbId: String?): JsonObject = buildJsonObject {
        put("tmdb", tmdbId)
        if (!imdbId.isNullOrBlank()) put("imdb", imdbId)
    }

    private fun url(path: String, extra: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder().addPathSegments(path).apply(extra).build()

    private suspend fun get(url: HttpUrl, clientId: String, token: String): JsonElement =
        call(url, clientId, token, null).orThrow().body

    private suspend fun write(path: String, clientId: String, token: String, body: JsonElement) {
        call(url(path), clientId, token, body).orThrow()
    }

    private class Response(val code: Int, val body: JsonElement, private val headers: okhttp3.Headers) {
        val ok: Boolean get() = code in 200..299
        fun header(name: String): String? = headers[name]

        /** `error` is a string in older answers and `{ code, message }` in newer ones. */
        fun errorCode(): String? = (body as? JsonObject)?.let { obj ->
            obj.string("error") ?: (obj["error"] as? JsonObject)?.string("code")
        }

        fun errorMessage(): String {
            val obj = body as? JsonObject
            val detail = obj?.string("message")
                ?: (obj?.get("error") as? JsonObject)?.string("message")
                ?: obj?.string("error")
            return "WeTrakr: eroare $code" + (detail?.let { " ($it)" } ?: "")
        }

        fun orThrow(): Response {
            if (ok) return this
            val message = when (code) {
                401 -> "WeTrakr: autentificarea a expirat, conectează-te din nou"
                420, 426 -> "WeTrakr: funcția cere cont VIP"
                423 -> "WeTrakr: cheia aplicației e suspendată"
                429 -> "WeTrakr: prea multe cereri, încearcă mai târziu"
                else -> errorMessage()
            }
            throw WeTrakrException(message, code)
        }
    }

    private suspend fun call(url: HttpUrl, clientId: String, token: String?, body: JsonElement?): Response = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("wetrakr-api-version", "1")
            .header("wetrakr-api-key", clientId)
            .header("User-Agent", "$APP_NAME/$appVersion")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .apply {
                if (body != null) post(body.toString().toRequestBody(JSON)) else get()
            }
            .build()
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val parsed = if (text.isBlank()) JsonNull else runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
            Response(r.code, parsed, r.headers)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.wetrakr.com/"
        const val DEFAULT_VERIFICATION_URL = "https://wetrakr.com/activate"
        const val APP_NAME = "akvodcalendar"
        private const val MAX_PAGES = 50
        private val FOLLOWED_SHOW_STATUSES = listOf("watching", "waiting", "planning")
        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** List answers are arrays; tolerate a wrapper object too. */
private fun JsonElement.rows(): List<JsonObject> {
    val array = this as? JsonArray
        ?: (this as? JsonObject)?.let { obj -> listOf("items", "data", "results").firstNotNullOfOrNull { obj[it] as? JsonArray } }
        ?: return emptyList()
    return array.mapNotNull { it as? JsonObject }
}

/** A list row is the media object itself; older shapes nest it under `movie` / `show`. */
private fun JsonObject.media(kind: String): JsonObject = (this[kind] as? JsonObject)?.takeIf { it.tmdbId() != null } ?: this

private fun JsonObject.tokens(): WeTrakrTokens? {
    val access = string("access_token")?.takeIf { it.isNotBlank() } ?: return null
    val user = (this["user"] as? JsonObject)?.let { it.string("username") ?: it.string("name") }
    return WeTrakrTokens(access, string("refresh_token"), user ?: string("username"))
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
private fun JsonObject.tmdbId(): Int? = (this["ids"] as? JsonObject)?.int("tmdb")?.takeIf { it > 0 }
