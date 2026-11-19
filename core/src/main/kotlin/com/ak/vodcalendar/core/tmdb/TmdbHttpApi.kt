package com.ak.vodcalendar.core.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate

/**
 * TMDB v3 over OkHttp. Accepts either a v3 API key (sent as `api_key`) or a
 * v4 read access token (a JWT, sent as a Bearer header).
 */
class TmdbHttpApi(
    private val http: OkHttpClient,
    private val apiKey: String,
    private val language: String = "en-US",
    baseUrl: String = DEFAULT_BASE_URL,
) : TmdbApi {

    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val isBearer = apiKey.startsWith("eyJ")

    override suspend fun discoverTvAiring(from: LocalDate, to: LocalDate, page: Int): PagedResponse<TvSummaryDto> =
        get(PagedResponse.serializer(TvSummaryDto.serializer()), "discover/tv") {
            addQueryParameter("air_date.gte", from.toString())
            addQueryParameter("air_date.lte", to.toString())
            addQueryParameter("timezone", "Europe/Bucharest")
            addQueryParameter("sort_by", "popularity.desc")
            addQueryParameter("include_null_first_air_dates", "false")
            addQueryParameter("page", page.toString())
        }

    override suspend fun discoverTvPremieres(from: LocalDate, to: LocalDate, page: Int): PagedResponse<TvSummaryDto> =
        get(PagedResponse.serializer(TvSummaryDto.serializer()), "discover/tv") {
            addQueryParameter("first_air_date.gte", from.toString())
            addQueryParameter("first_air_date.lte", to.toString())
            addQueryParameter("sort_by", "popularity.desc")
            addQueryParameter("page", page.toString())
        }

    override suspend fun tvDetails(showId: Int): TvDetailsDto =
        get(TvDetailsDto.serializer(), "tv/$showId") {
            addQueryParameter("append_to_response", "watch/providers,external_ids")
        }

    override suspend fun season(showId: Int, seasonNumber: Int): SeasonDto =
        get(SeasonDto.serializer(), "tv/$showId/season/$seasonNumber")

    override suspend fun tvWatchProviders(showId: Int): WatchProvidersDto =
        get(WatchProvidersDto.serializer(), "tv/$showId/watch/providers")

    override suspend fun tvProviderList(region: String): ProviderListDto =
        get(ProviderListDto.serializer(), "watch/providers/tv") {
            addQueryParameter("watch_region", region)
        }

    private suspend fun <T> get(
        deserializer: DeserializationStrategy<T>,
        path: String,
        params: HttpUrl.Builder.() -> Unit = {},
    ): T {
        if (apiKey.isBlank()) throw TmdbException("Lipsește cheia TMDB")
        val url = base.newBuilder()
            .addPathSegments(path)
            .addQueryParameter("language", language)
            .apply(params)
            .apply { if (!isBearer) addQueryParameter("api_key", apiKey) }
            .build()
        val request = Request.Builder().url(url).get()
            .header("Accept", "application/json")
            .apply { if (isBearer) header("Authorization", "Bearer $apiKey") }
            .build()

        return withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val reason = if (response.code == 401) "cheie TMDB invalidă" else "HTTP ${response.code}"
                        throw TmdbException("TMDB: $reason", response.code)
                    }
                    json.decodeFromString(deserializer, body)
                }
            } catch (e: IOException) {
                throw TmdbException("TMDB indisponibil: ${e.message}", cause = e)
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3/"
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
