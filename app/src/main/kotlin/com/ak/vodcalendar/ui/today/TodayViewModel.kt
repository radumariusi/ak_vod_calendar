package com.ak.vodcalendar.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.EpisodeEntry
import com.ak.vodcalendar.core.time.RomaniaTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate,
    val loading: Boolean = true,
    val entries: List<EpisodeEntry> = emptyList(),
    val error: String? = null,
    val missingKey: Boolean = false,
    val hasInstalledApps: Boolean = true,
)

private data class Request(val date: LocalDate, val settings: AppSettings, val refresh: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val date = MutableStateFlow(RomaniaTime.today())
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<TodayUiState> =
        combine(date, container.settingsRepository.settings, refresh) { d, s, r -> Request(d, s, r) }
            .flatMapLatest { request -> load(request) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(date.value))

    private fun load(request: Request) = flow {
        val base = TodayUiState(
            date = request.date,
            hasInstalledApps = request.settings.installedApps().isNotEmpty(),
        )
        val key = container.tmdbKey(request.settings)
        if (key.isBlank()) {
            emit(base.copy(loading = false, missingKey = true))
            return@flow
        }
        emit(base)
        try {
            val entries = container.episodeCalendar.episodesBetween(request.date, request.date, request.settings, key)
            emit(base.copy(loading = false, entries = entries))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(base.copy(loading = false, error = e.message ?: e.toString()))
        }
    }

    fun previousDay() = date.update { it.minusDays(1) }
    fun nextDay() = date.update { it.plusDays(1) }
    fun goToToday() = date.update { RomaniaTime.today() }
    fun reload() = refresh.update { it + 1 }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { TodayViewModel(container) }
        }
    }
}
