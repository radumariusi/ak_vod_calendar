package com.ak.vodcalendar.today

import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.model.MovieEntry
import com.ak.vodcalendar.core.time.RomaniaTime
import com.ak.vodcalendar.core.today.TodayDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Today's calendar (series + movies) in Romania time, minus what was watched on Simkl. */
class TodayLoader(private val container: AppContainer) {

    /** Null when there is no TMDB key yet. Throws when TMDB can't be reached at all. */
    suspend fun load(settings: AppSettings? = null): TodayDigest? {
        val s = settings ?: container.settingsRepository.settings.first()
        val key = container.tmdbKey(s)
        if (key.isBlank()) return null
        val today = RomaniaTime.today()
        val omdb = container.omdbKey(s)

        var failure: Exception? = null
        val episodes: List<EpisodeEntry> = try {
            container.episodeCalendar.episodesBetween(today, today, s, key, omdb).entries
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
            emptyList()
        }
        val movies: List<MovieEntry> = if (!s.notifications.includeMovies) emptyList() else try {
            container.movieCalendar.moviesBetween(today, today, s, key, omdb).entries
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = failure ?: e
            emptyList()
        }
        if (episodes.isEmpty() && movies.isEmpty() && failure != null) throw failure

        val account = container.simkl.store.current()
        return TodayDigest.build(today, episodes, movies, account.watchedEpisodes, account.watchedMovies)
    }
}
