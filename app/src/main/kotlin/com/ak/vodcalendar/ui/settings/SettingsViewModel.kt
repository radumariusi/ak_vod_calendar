package com.ak.vodcalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.ContentFilter
import com.ak.vodcalendar.core.model.MovieReleaseKind
import com.ak.vodcalendar.core.model.NotificationMode
import com.ak.vodcalendar.core.model.QualityThresholds
import com.ak.vodcalendar.core.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun toggleInstalled(appId: String) = update { s -> s.updateApp(appId) { it.copy(installed = !it.installed) } }

    fun toggleRegion(appId: String) = update { s -> s.updateApp(appId) { it.copy(region = it.region.toggled()) } }

    fun selectAlternativePlayer(id: String?) = update { it.copy(alternativePlayerId = id) }

    fun updateThresholds(transform: (QualityThresholds) -> QualityThresholds) =
        update { it.copy(thresholds = transform(it.thresholds)) }

    fun toggleHidden(filter: ContentFilter) = update { s ->
        val hidden = if (filter in s.hiddenCategories) s.hiddenCategories - filter else s.hiddenCategories + filter
        s.copy(hiddenCategories = hidden)
    }

    fun saveTmdbKey(key: String) = update { it.copy(tmdbApiKey = key.trim()) }

    fun saveOmdbKey(key: String) = update { it.copy(omdbApiKey = key.trim()) }

    fun toggleOnlyInstalledApps() = update { it.copy(onlyInstalledApps = !it.onlyInstalledApps) }

    fun unfollow(showId: Int) = update { it.unfollow(showId) }

    fun unhide(showId: Int) = update { it.unhide(showId) }

    fun updateMovieThresholds(transform: (QualityThresholds) -> QualityThresholds) =
        update { it.copy(movieThresholds = transform(it.movieThresholds)) }

    fun toggleMovieKind(kind: MovieReleaseKind) = update { s ->
        s.copy(movieKinds = if (kind in s.movieKinds) s.movieKinds - kind else s.movieKinds + kind)
    }

    fun unfollowMovie(movieId: Int) = update { it.unfollowMovie(movieId) }

    fun unhideMovie(movieId: Int) = update { it.unhideMovie(movieId) }

    fun saveSimklClientId(id: String) = update { it.copy(simklClientId = id.trim()) }

    fun setNotificationMode(mode: NotificationMode) = update { it.copy(notifications = it.notifications.copy(mode = mode)) }

    fun setNotificationHour(hour: Int) = update { it.copy(notifications = it.notifications.copy(hour = hour)) }

    fun toggleNotificationMovies() =
        update { it.copy(notifications = it.notifications.copy(includeMovies = !it.notifications.includeMovies)) }

    fun toggleTvChannel() = update { it.copy(tvChannel = !it.tvChannel) }

    companion object {
        fun factory(repository: SettingsRepository) = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
