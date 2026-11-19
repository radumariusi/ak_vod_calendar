package com.ak.vodcalendar.core.tmdb

import java.time.LocalDate

/** The TMDB calls the app needs. Implemented over HTTP by [TmdbHttpApi]. */
interface TmdbApi {
    /** Shows that have at least one episode airing between [from] and [to] (inclusive). */
    suspend fun discoverTvAiring(from: LocalDate, to: LocalDate, page: Int): PagedResponse<TvSummaryDto>

    /** Show details with `watch/providers` and `external_ids` appended. */
    suspend fun tvDetails(showId: Int): TvDetailsDto

    suspend fun season(showId: Int, seasonNumber: Int): SeasonDto

    suspend fun tvWatchProviders(showId: Int): WatchProvidersDto

    /** All TV providers TMDB knows for a region, for Settings. */
    suspend fun tvProviderList(region: String): ProviderListDto
}

class TmdbException(message: String, val httpCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String?): String? = path?.let { BASE + "w342" + it }
    fun still(path: String?): String? = path?.let { BASE + "w300" + it }
    fun logo(path: String?): String? = path?.let { BASE + "w92" + it }
}
