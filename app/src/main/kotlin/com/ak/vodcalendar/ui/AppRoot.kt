@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ak.vodcalendar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.model.MovieEntry
import com.ak.vodcalendar.core.model.SavedShow
import com.ak.vodcalendar.ui.calendar.CalendarScreen
import com.ak.vodcalendar.ui.common.NavButton
import com.ak.vodcalendar.ui.details.EpisodeDetailsScreen
import com.ak.vodcalendar.ui.movies.MovieCalendarScreen
import com.ak.vodcalendar.ui.movies.MovieDetailsScreen
import com.ak.vodcalendar.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

enum class Screen(val label: String) {
    SERIES("Seriale"),
    MOVIES("Filme"),
    SETTINGS("Setări"),
}

@Composable
fun AppRoot(container: AppContainer) {
    var screen by rememberSaveable { mutableStateOf(Screen.SERIES) }
    var openEntry by remember { mutableStateOf<EpisodeEntry?>(null) }
    var openMovie by remember { mutableStateOf<MovieEntry?>(null) }
    val stateHolder = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        scope.launch { container.settingsRepository.update(transform) }
    }

    BackHandler(enabled = openEntry != null || openMovie != null || screen != Screen.SERIES) {
        when {
            openEntry != null -> openEntry = null
            openMovie != null -> openMovie = null
            else -> screen = Screen.SERIES
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            val entry = openEntry
            if (entry != null) {
                val followed = settings.isFollowed(entry.showId)
                EpisodeDetailsScreen(
                    entry = entry,
                    isFollowed = followed,
                    onToggleFollow = {
                        updateSettings { s ->
                            if (followed) s.unfollow(entry.showId) else s.follow(SavedShow(entry.showId, entry.showName))
                        }
                    },
                    onHide = {
                        updateSettings { it.hide(SavedShow(entry.showId, entry.showName)) }
                        openEntry = null
                    },
                    onBack = { openEntry = null },
                )
                return@Column
            }
            val movie = openMovie
            if (movie != null) {
                val followed = settings.isMovieFollowed(movie.movieId)
                MovieDetailsScreen(
                    entry = movie,
                    isFollowed = followed,
                    onToggleFollow = {
                        updateSettings { s ->
                            if (followed) s.unfollowMovie(movie.movieId) else s.followMovie(SavedShow(movie.movieId, movie.title))
                        }
                    },
                    onHide = {
                        updateSettings { it.hideMovie(SavedShow(movie.movieId, movie.title)) }
                        openMovie = null
                    },
                    onBack = { openMovie = null },
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("AK VOD Calendar", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(24.dp))
                Screen.entries.forEach { item ->
                    NavButton(label = item.label, selected = item == screen, onClick = { screen = item })
                }
            }
            Spacer(Modifier.padding(top = 16.dp))
            Box(modifier = Modifier.fillMaxSize()) {
                // Keeps each tab's scroll position when opening details or switching tabs.
                stateHolder.SaveableStateProvider(screen.name) {
                    when (screen) {
                        Screen.SERIES -> CalendarScreen(container, onOpen = { openEntry = it })
                        Screen.MOVIES -> MovieCalendarScreen(container, onOpen = { openMovie = it })
                        Screen.SETTINGS -> SettingsScreen(container)
                    }
                }
            }
        }
    }
}
