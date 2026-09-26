package dev.yashas.expensetracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.yashas.expensetracker.data.repo.AnalyticsData
import dev.yashas.expensetracker.data.repo.AnalyticsRepository
import dev.yashas.expensetracker.data.repo.AnalyticsMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S12 Analytics dashboard state; loads are period-scoped and deterministic. */
class AnalyticsViewModel(private val repo: AnalyticsRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val period: AnalyticsMath.Period = AnalyticsMath.Period.M3,
        val data: AnalyticsData? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun setPeriod(period: AnalyticsMath.Period) {
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val data = repo.load(_state.value.period)
            _state.update { it.copy(loading = false, data = data) }
        }
    }

    companion object {
        fun factory(repo: AnalyticsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AnalyticsViewModel(repo) as T
            }
    }
}
