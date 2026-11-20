package com.ak.vodcalendar.core.simkl

import com.ak.vodcalendar.core.model.SavedShow
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The code shown on the TV while the user approves the app on simkl.com/pin. */
data class SimklDeviceCode(
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

sealed interface SimklPollResult {
    data class Authorized(val accessToken: String) : SimklPollResult
    /** The user hasn't typed the code yet. */
    data object Pending : SimklPollResult
    /** Simkl asks to poll less often. */
    data object SlowDown : SimklPollResult
}

/**
 * What the calendar takes from the user's Simkl lists, by TMDB id (titles without
 * a TMDB id on Simkl are skipped).
 */
data class SimklLibrary(
    /** Series in "Watching" or "Plan to watch": always shown in the calendar. */
    val followedShows: List<SavedShow> = emptyList(),
    /** Movies in "Plan to watch": always shown in the movie calendar. */
    val followedMovies: List<SavedShow> = emptyList(),
    /** Watched episodes, as [episodeKey]. */
    val watchedEpisodes: Set<String> = emptySet(),
    /** TMDB ids of watched movies. */
    val watchedMovies: Set<Int> = emptySet(),
) {
    companion object {
        /** Same shape as `EpisodeEntry.key`. */
        fun episodeKey(showTmdbId: Int, season: Int, episode: Int): String = "$showTmdbId-$season-$episode"
    }
}

class SimklException(message: String, val code: Int = 0) : Exception(message) {
    /** The token was revoked or expired: the user has to log in again. */
    val unauthorized: Boolean get() = code == 401
}

/**
 * Simkl API (https://simkl.docs.apiary.io): PIN login for TVs, reading the user's
 * lists and marking things watched. Trakt is deliberately not used.
 *
 * Every call carries the app's client id (`simkl-api-key` header plus the
 * `client_id`, `app-name` and `app-version` URL parameters Simkl asks apps to send).
 */
class SimklClient(
    private val http: OkHttpClient,
    baseUrl: String = DEFAULT_BASE_URL,
    private val appVersion: String = "1",
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    /** Step 1 of the TV login: get a short code for simkl.com/pin. */
    suspend fun requestDeviceCode(clientId: String): SimklDeviceCode {
        val body = get(url("oauth/pin", clientId) { addQueryParameter("redirect", "urn:ietf:wg:oauth:2.0:oob") }, clientId, null)
        val obj = body.jsonObject
        val userCode = obj.string("user_code") ?: throw SimklException(obj.string("message") ?: obj.string("error") ?: "Simkl nu a dat un cod")
        return SimklDeviceCode(
            userCode = userCode,
            verificationUrl = obj.string("verification_url") ?: "https://simkl.com/pin/",
            expiresInSeconds = obj.int("expires_in") ?: 900,
            intervalSeconds = (obj.int("interval") ?: 5).coerceAtLeast(1),
        )
    }

    /** Step 2: ask, every few seconds, whether the user approved the code. */
    suspend fun pollDeviceCode(clientId: String, userCode: String): SimklPollResult {
        val obj = get(url("oauth/pin/$userCode", clientId), clientId, null).jsonObject
        obj.string("access_token")?.takeIf { it.isNotBlank() }?.let { return SimklPollResult.Authorized(it) }
        val message = obj.string("message").orEmpty()
        return if (message.contains("slow", ignoreCase = true)) SimklPollResult.SlowDown else SimklPollResult.Pending
    }

    /** The account name, to show in Settings. */
    suspend fun userName(clientId: String, token: String): String? {
        val obj = send(url("users/settings", clientId), clientId, token, JsonObject(emptyMap())).jsonObjectOrNull() ?: return null
        return obj["user"]?.jsonObjectOrNull()?.string("name")
    }

    /** All series, anime and movies in the user's lists, with what was watched. */
    suspend fun library(clientId: String, token: String): SimklLibrary {
        val followedShows = LinkedHashMap<Int, SavedShow>()
        val followedMovies = LinkedHashMap<Int, SavedShow>()
        val watchedEpisodes = HashSet<String>()
        val watchedMovies = HashSet<Int>()

        for (type in listOf("shows", "anime")) {
            val items = allItems(clientId, token, type)
            for (item in items) {
                val show = item["show"]?.jsonObjectOrNull() ?: continue
                val tmdbId = show.tmdbId() ?: continue
                val status = item.string("status").orEmpty()
                if (status in FOLLOWED_SHOW_STATUSES) {
                    followedShows.putIfAbsent(tmdbId, SavedShow(tmdbId, show.string("title") ?: "TMDB $tmdbId"))
                }
                item["seasons"]?.arrayOrEmpty()?.forEach { seasonEl ->
                    val season = seasonEl.jsonObjectOrNull() ?: return@forEach
                    val number = season.int("number") ?: return@forEach
                    season["episodes"]?.arrayOrEmpty()?.forEach { epEl ->
                        val ep = epEl.jsonObjectOrNull()?.int("number") ?: return@forEach
                        watchedEpisodes += SimklLibrary.episodeKey(tmdbId, number, ep)
                    }
                }
            }
        }

        for (item in allItems(clientId, token, "movies")) {
            val movie = item["movie"]?.jsonObjectOrNull() ?: continue
            val tmdbId = movie.tmdbId() ?: continue
            when (item.string("status")) {
                "plantowatch" -> followedMovies.putIfAbsent(tmdbId, SavedShow(tmdbId, movie.string("title") ?: "TMDB $tmdbId"))
                "completed" -> watchedMovies += tmdbId
            }
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
                            put("episodes", buildJsonArray { add(buildJsonObject { put("number", episode) }) })
                        })
                    })
                })
            })
        }
        send(url(if (watched) "sync/history" else "sync/history/remove", clientId), clientId, token, body)
    }

    suspend fun markMovieWatched(clientId: String, token: String, tmdbId: Int, imdbId: String?, watched: Boolean = true) {
        val body = buildJsonObject {
            put("movies", buildJsonArray { add(buildJsonObject { put("ids", ids(tmdbId, imdbId)) }) })
        }
        send(url(if (watched) "sync/history" else "sync/history/remove", clientId), clientId, token, body)
    }

    /** Puts a series in "Watching" (or a movie in "Plan to watch") when the user follows it in the app. */
    suspend fun addToList(clientId: String, token: String, isMovie: Boolean, tmdbId: Int, imdbId: String?) {
        val body = buildJsonObject {
            put(if (isMovie) "movies" else "shows", buildJsonArray {
                add(buildJsonObject {
                    put("to", if (isMovie) "plantowatch" else "watching")
                    put("ids", ids(tmdbId, imdbId))
                })
            })
        }
        send(url("sync/add-to-list", clientId), clientId, token, body)
    }

    private suspend fun allItems(clientId: String, token: String, type: String): List<JsonObject> {
        val url = url("sync/all-items/$type/", clientId) {
            addQueryParameter("extended", "full")
            addQueryParameter("episode_watched_at", "yes")
        }
        val body = get(url, clientId, token).jsonObjectOrNull() ?: return emptyList()
        return body[type]?.arrayOrEmpty()?.mapNotNull { it.jsonObjectOrNull() }.orEmpty()
    }

    private fun ids(tmdbId: Int, imdbId: String?): JsonObject = buildJsonObject {
        put("tmdb", tmdbId)
        if (!imdbId.isNullOrBlank()) put("imdb", imdbId)
    }

    private fun url(path: String, clientId: String, extra: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder()
            .addPathSegments(path.trimEnd('/'))
            .apply { if (path.endsWith("/")) addPathSegment("") }
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("app-name", APP_NAME)
            .addQueryParameter("app-version", appVersion)
            .apply(extra)
            .build()

    private suspend fun get(url: HttpUrl, clientId: String, token: String?): JsonElement =
        execute(request(url, clientId, token).get().build())

    private suspend fun send(url: HttpUrl, clientId: String, token: String?, body: JsonElement): JsonElement =
        execute(request(url, clientId, token).post(body.toString().toRequestBody(JSON)).build())

    private fun request(url: HttpUrl, clientId: String, token: String?): Request.Builder =
        Request.Builder().url(url)
            .header("simkl-api-key", clientId)
            .header("Content-Type", "application/json")
            .header("User-Agent", "$APP_NAME/$appVersion")
            .apply { if (token != null) header("Authorization", "Bearer $token") }

    private suspend fun execute(request: Request): JsonElement = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val message = when (r.code) {
                    401 -> "Simkl: autentificarea a expirat, conectează-te din nou"
                    403 -> "Simkl: Client ID greșit sau aplicația nu are acces"
                    else -> "Simkl: eroare ${r.code}"
                }
                throw SimklException(message, r.code)
            }
            if (text.isBlank() || text == "null") JsonNull else json.parseToJsonElement(text)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.simkl.com/"
        const val APP_NAME = "akvodcalendar"
        private val FOLLOWED_SHOW_STATUSES = setOf("watching", "plantowatch")
        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
private fun JsonElement.arrayOrEmpty(): List<JsonElement> = (this as? JsonArray) ?: emptyList()
private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(name: String): Int? = (this[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

/** Simkl sends TMDB ids as text or as numbers. */
private fun JsonObject.tmdbId(): Int? = this["ids"]?.jsonObjectOrNull()?.int("tmdb")?.takeIf { it > 0 }
