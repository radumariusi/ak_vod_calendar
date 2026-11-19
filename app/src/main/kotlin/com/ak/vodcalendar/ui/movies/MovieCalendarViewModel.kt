package com.ak.vodcalendar.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ak.vodcalendar.AppContainer
import com.ak.vodcalendar.core.model.AppSettings
import com.ak.vodcalendar.core.model.MovieCalendarResult
import com.ak.vodcalendar.core.model.MovieEntry
import com.ak.vodcalendar.core.model.MovieReleaseKind
import com.ak.vodcalendar.core.time.RomaniaTime
import com.ak.vodcalendar.ui.calendar.CalendarMode
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
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class MovieCalendarUiState(
    val mode: CalendarMode,
    val from: LocalDate,
    val to: LocalDate,
    val loading: Boolean = true,
    val result: MovieCalendarResult? = null,
    val error: String? = null,
    val missingKey: Boolean = false,
    val kinds: Set<MovieReleaseKind> = MovieReleaseKind.entries.toSet(),
    val onlyInstalledApps: Boolean = false,
    val showRejected: Boolean = false,
) {
    /** Releases to draw: the ones that passed, plus the rejected ones when asked. */
    val visible: List<MovieEntry>
        get() {
            val r = result ?: return emptyList()
            val all = if (showRejected) r.entries + r.rejected else r.entries
            return all.sortedWith(com.ak.vodcalendar.core.calendar.MovieCalendarRepository.movieOrder)
        }

    val days: List<LocalDate> get() = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
}

private data class MovieRequest(
    val mode: CalendarMode,
    val anchor: LocalDate,
    val settings: AppSettings,
    val refresh: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MovieCalendarViewModel(private val container: AppContainer) : ViewModel() {

    private val anchor = MutableStateFlow(RomaniaTime.today())
    private val mode = MutableStateFlow(CalendarMode.WEEK)
    private val refresh = MutableStateFlow(0)
    private val showRejected = MutableStateFlow(false)
    @Volatile private var lastResult: Pair<Triple<CalendarMode, LocalDate, LocalDate>, MovieCalendarResult>? = null

    private val loaded = combine(mode, anchor, container.settingsRepository.settings, refresh) { m, a, s, r ->
        MovieRequest(m, a, s, r)
    }.flatMapLatest { load(it) }

    val state: StateFlow<MovieCalendarUiState> = combine(loaded, showRejected) { s, rejected -> s.copy(showRejected = rejected) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initial())

    private fun initial(): MovieCalendarUiState {
        val (from, to) = range(mode.value, anchor.value)
        return MovieCalendarUiState(mode.value, from, to)
    }

    private fun range(mode: CalendarMode, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (mode) {
        CalendarMode.DAY -> anchor to anchor
        CalendarMode.WEEK -> {
            val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday to monday.plusDays(6)
        }
        CalendarMode.MONTH -> anchor.withDayOfMonth(1) to anchor.with(TemporalAdjusters.lastDayOfMonth())
    }

    private fun load(request: MovieRequest) = flow {
        val (from, to) = range(request.mode, request.anchor)
        val base = MovieCalendarUiState(
            mode = request.mode,
            from = from,
            to = to,
            kinds = request.settings.movieKinds,
            onlyInstalledApps = request.settings.onlyInstalledApps,
        )
        val key = container.tmdbKey(request.settings)
        if (key.isBlank()) {
            emit(base.copy(loading = false, missingKey = true))
            return@flow
        }
        val previous = lastResult?.takeIf { it.first == Triple(request.mode, from, to) }?.second
        emit(base.copy(result = previous))
        try {
            val result = container.movieCalendar.moviesBetween(
                from, to, request.settings, key, container.omdbKey(request.settings),
            )
            lastResult = Triple(request.mode, from, to) to result
            emit(base.copy(loading = false, result = result))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(base.copy(loading = false, error = e.message ?: e.toString()))
        }
    }

    private fun move(date: LocalDate, forward: Boolean): LocalDate {
        val sign = if (forward) 1L else -1L
        return when (mode.value) {
            CalendarMode.DAY -> date.plusDays(sign)
            CalendarMode.WEEK -> date.plusWeeks(sign)
            CalendarMode.MONTH -> date.withDayOfMonth(1).plusMonths(sign)
        }
    }

    fun previous() = anchor.update { move(it, forward = false) }
    fun next() = anchor.update { move(it, forward = true) }
    fun goToToday() = anchor.update { RomaniaTime.today() }
    fun setMode(newMode: CalendarMode) = mode.update { newMode }

    /** From the month grid: show that day. */
    fun openDay(date: LocalDate) {
        anchor.update { date }
        mode.update { CalendarMode.DAY }
    }
    fun toggleShowRejected() = showRejected.update { !it }

    fun toggleKind(kind: MovieReleaseKind) {
        viewModelScope.launch {
            container.settingsRepository.update { s ->
                val kinds = if (kind in s.movieKinds) s.movieKinds - kind else s.movieKinds + kind
                s.copy(movieKinds = kinds)
            }
        }
    }

    fun toggleOnlyInstalledApps() {
        viewModelScope.launch { container.settingsRepository.update { it.copy(onlyInstalledApps = !it.onlyInstalledApps) } }
    }

    fun reload() {
        container.clearCaches()
        refresh.update { it + 1 }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { MovieCalendarViewModel(container) }
        }
    }
}
