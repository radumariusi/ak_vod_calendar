@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.model.MovieEntry
import com.ak.vodcalendar.simkl.SimklAccount

/** "Marchează văzut" / "✓ Văzut" for an episode, synced to Simkl. Hidden when Simkl isn't connected. */
@Composable
fun EpisodeWatchedButton(entry: EpisodeEntry, container: AppContainer) {
    val account by container.simkl.account.collectAsStateWithLifecycle(SimklAccount())
    if (!account.connected) return
    val watched = entry.key in account.watchedEpisodes
    WatchedButton(watched) {
        container.simkl.setEpisodeWatched(entry.showId, entry.imdbId, entry.seasonNumber, entry.episodeNumber, entry.key, !watched)
    }
}

@Composable
fun MovieWatchedButton(entry: MovieEntry, container: AppContainer) {
    val account by container.simkl.account.collectAsStateWithLifecycle(SimklAccount())
    if (!account.connected) return
    val watched = entry.movieId in account.watchedMovies
    WatchedButton(watched) { container.simkl.setMovieWatched(entry.movieId, entry.imdbId, !watched) }
}

@Composable
private fun WatchedButton(watched: Boolean, onClick: () -> Unit) {
    if (watched) {
        Button(onClick = onClick) { Text("✓ Văzut") }
    } else {
        OutlinedButton(onClick = onClick) { Text("Marchează văzut") }
    }
}
