package com.ak.vodcalendar.core.tmdb

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps TMDB answers in memory for [ttlMillis], so moving between days and
 * weeks does not fetch the same shows and seasons again.
 */
class CachingTmdbApi(
    private val delegate: TmdbApi,
    private val ttlMillis: Long = 30 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) : TmdbApi {

    private val cache = ConcurrentHashMap<String, Pair<Long, Any>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        cache[key]?.let { (at, value) -> if (now() - at < ttlMillis) return value as T }
        return load().also { cache[key] = now() to it }
    }

    fun clear() = cache.clear()

    override suspend fun discoverTvAiring(from: LocalDate, to: LocalDate, page: Int) =
        cached("airing:$from:$to:$page") { delegate.discoverTvAiring(from, to, page) }

    override suspend fun discoverTvPremieres(from: LocalDate, to: LocalDate, page: Int) =
        cached("premieres:$from:$to:$page") { delegate.discoverTvPremieres(from, to, page) }

    override suspend fun tvDetails(showId: Int) = cached("tv:$showId") { delegate.tvDetails(showId) }

    override suspend fun season(showId: Int, seasonNumber: Int) =
        cached("season:$showId:$seasonNumber") { delegate.season(showId, seasonNumber) }

    override suspend fun tvWatchProviders(showId: Int) =
        cached("providers:$showId") { delegate.tvWatchProviders(showId) }

    override suspend fun tvProviderList(region: String) =
        cached("providerList:$region") { delegate.tvProviderList(region) }

    override suspend fun discoverMovieReleases(region: String, releaseTypes: Set<Int>, from: LocalDate, to: LocalDate, page: Int) =
        cached("movieReleases:$region:${releaseTypes.sorted()}:$from:$to:$page") {
            delegate.discoverMovieReleases(region, releaseTypes, from, to, page)
        }

    override suspend fun discoverMoviePremieres(from: LocalDate, to: LocalDate, page: Int) =
        cached("moviePremieres:$from:$to:$page") { delegate.discoverMoviePremieres(from, to, page) }

    override suspend fun movieDetails(movieId: Int) = cached("movie:$movieId") { delegate.movieDetails(movieId) }

    override suspend fun credits(type: MediaKind, id: Int) =
        cached("credits:${type.path}:$id") { delegate.credits(type, id) }

    override suspend fun videos(type: MediaKind, id: Int) =
        cached("videos:${type.path}:$id") { delegate.videos(type, id) }
}
