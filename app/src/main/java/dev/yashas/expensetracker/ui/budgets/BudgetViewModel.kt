package dev.yashas.expensetracker.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.yashas.expensetracker.data.repo.BudgetData
import dev.yashas.expensetracker.data.repo.BudgetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S15 Budget overview state; reloads after every editor save. */
class BudgetViewModel(private val repo: BudgetRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val data: BudgetData? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val data = repo.load()
            _state.update { it.copy(loading = false, data = data) }
        }
    }

    /** Editor save: rupees in, paise stored. categoryKey == null sets the overall budget. */
    fun saveBudget(categoryKey: String?, rupees: Long) {
        viewModelScope.launch {
            repo.saveBudget(categoryKey, rupees * 100)
            refresh()
        }
    }

    companion object {
        fun factory(repo: BudgetRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = BudgetViewModel(repo) as T
            }
    }
}
