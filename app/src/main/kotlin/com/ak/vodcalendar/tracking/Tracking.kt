package com.ak.vodcalendar.tracking

import com.ak.vodcalendar.simkl.SimklManager
import com.ak.vodcalendar.wetrakr.WeTrakrManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/** What was watched, on any connected service. */
data class WatchedMarks(
    /** At least one service is connected (the "văzut" buttons show only then). */
    val connected: Boolean = false,
    val episodes: Set<String> = emptySet(),
    val movies: Set<Int> = emptySet(),
)

/**
 * Simkl and WeTrakr together: the "văzut" marks are the union of both, and marking
 * something watched (or following it) goes to every connected service.
 */
class Tracking(val simkl: SimklManager, val wetrakr: WeTrakrManager) {

    val watched: Flow<WatchedMarks> = combine(simkl.account, wetrakr.account) { s, w ->
        WatchedMarks(
            connected = s.connected || w.connected,
            episodes = s.watchedEpisodes + w.watchedEpisodes,
            movies = s.watchedMovies + w.watchedMovies,
        )
    }.distinctUntilChanged()

    suspend fun currentWatched(): WatchedMarks = watched.first()

    fun setEpisodeWatched(showId: Int, imdbId: String?, season: Int, episode: Int, key: String, watched: Boolean) {
        simkl.setEpisodeWatched(showId, imdbId, season, episode, key, watched)
        wetrakr.setEpisodeWatched(showId, imdbId, season, episode, key, watched)
    }

    fun setMovieWatched(movieId: Int, imdbId: String?, watched: Boolean) {
        simkl.setMovieWatched(movieId, imdbId, watched)
        wetrakr.setMovieWatched(movieId, imdbId, watched)
    }

    fun addToList(isMovie: Boolean, tmdbId: Int, imdbId: String?) {
        simkl.addToList(isMovie, tmdbId, imdbId)
        wetrakr.addToList(isMovie, tmdbId, imdbId)
    }

    /** Syncs every connected service that is older than [maxAgeMillis]. False if any sync failed. */
    suspend fun sync(force: Boolean = false, maxAgeMillis: Long = 60 * 60 * 1000L): Boolean {
        val a = simkl.sync(force, maxAgeMillis)
        val b = wetrakr.sync(force, maxAgeMillis)
        return a && b
    }
}
