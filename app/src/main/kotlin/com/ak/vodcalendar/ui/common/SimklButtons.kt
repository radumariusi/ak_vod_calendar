@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ak.vodcalendar.ui.touch.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.ak.vodcalendar.ui.touch.OutlinedButton
import androidx.tv.material3.Text
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.simkl.SimklLibrary
import com.ak.vodcalendar.core.model.MovieEntry
import com.ak.vodcalendar.tracking.WatchedMarks

/** "Marchează văzut" / "✓ Văzut" for an episode, synced to Simkl / WeTrakr. Hidden when neither is connected. */
@Composable
fun EpisodeWatchedButton(showId: Int, imdbId: String?, season: Int, episode: Int, container: AppContainer) {
    val marks by container.tracking.watched.collectAsStateWithLifecycle(WatchedMarks())
    if (!marks.connected) return
    val key = SimklLibrary.episodeKey(showId, season, episode)
    val watched = key in marks.episodes
    WatchedButton(watched) { container.tracking.setEpisodeWatched(showId, imdbId, season, episode, key, !watched) }
}

@Composable
fun MovieWatchedButton(entry: MovieEntry, container: AppContainer) {
    val marks by container.tracking.watched.collectAsStateWithLifecycle(WatchedMarks())
    if (!marks.connected) return
    val watched = entry.movieId in marks.movies
    WatchedButton(watched) { container.tracking.setMovieWatched(entry.movieId, entry.imdbId, !watched) }
}

@Composable
private fun WatchedButton(watched: Boolean, onClick: () -> Unit) {
    if (watched) {
        Button(onClick = onClick) { Text("✓ Văzut") }
    } else {
        OutlinedButton(onClick = onClick) { Text("Marchează văzut") }
    }
}
