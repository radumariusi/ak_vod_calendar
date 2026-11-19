package com.ak.vodcalendar.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.ContentFilter
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

    companion object {
        fun factory(repository: SettingsRepository) = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
