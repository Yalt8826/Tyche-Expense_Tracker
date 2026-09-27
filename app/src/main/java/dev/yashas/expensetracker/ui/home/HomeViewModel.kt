package dev.yashas.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yashas.expensetracker.data.db.entity.CategoryEntity
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.data.repo.HomeBreakdown
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.data.repo.UserPrefs
import dev.yashas.expensetracker.domain.model.TxnType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home dashboard state (01-SCREENS S6, post-redesign): greeting + avatar, review
 * banner (below hero), hero net month total, tag proportion bar, recent list.
 */
class HomeViewModel(
    private val repo: TxnRepository,
    private val prefs: UserPrefs,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val userName: String = UserPrefs.DEFAULT_NAME,
        val reviewCount: Int = 0,
        val monthNetPaise: Long = 0,
        val monthLabel: String = "",
        val recent: List<TransactionEntity> = emptyList(),
        val categories: Map<String, CategoryEntity> = emptyMap(),
        val slices: List<HomeBreakdown.Slice> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeReviewInbox().collect { inbox ->
                _state.update { it.copy(reviewCount = inbox.size, loading = false) }
            }
        }

        // COMBINED flows: slices always recompute with current categories (no race).
        viewModelScope.launch {
            val monthStart = YearMonth.now().atDay(1).toEpochDay()
            val monthEnd = YearMonth.now().plusMonths(1).atDay(1).toEpochDay()
            combine(repo.observeAll(), repo.observeCategories(), prefs.nameFlow) { rows, cats, name ->
                Triple(rows, cats.associateBy { it.key }, name)
            }.collect { (rows, categories, name) ->
                val monthRows = rows.filter {
                    (it.valueDate ?: it.timestamp / TransactionEntity.MILLIS_PER_DAY) in monthStart until monthEnd
                }
                _state.update { s ->
                    s.copy(
                        userName = name,
                        categories = categories,
                        monthNetPaise = LedgerMath.netSpendPaise(monthRows),
                        monthLabel = MonthLabel.current(),
                        recent = rows.sortedByDescending { r -> r.timestamp }.take(5),
                        slices = HomeBreakdown.slices(
                            rows = monthRows,
                            nameByKey = categories.mapValues { c -> c.value.name },
                            colorTokenByKey = categories.mapValues { c -> c.value.colorToken },
                        ),
                    )
                }
            }
        }
    }

    fun confirmReview(txn: TransactionEntity, categoryKey: String, createRule: Boolean) {
        viewModelScope.launch { repo.confirmReview(txn, categoryKey, createRule) }
    }

    fun dismissReview(txn: TransactionEntity) {
        viewModelScope.launch { repo.deleteForUndo(txn.id) }
    }

    /** New tag from Home (name + chosen color); keys namespaced user_*. */
    fun createTag(name: String, colorToken: String) {
        viewModelScope.launch { repo.createCategory(name, colorToken) }
    }

    fun renameUser(name: String) {
        viewModelScope.launch { prefs.setName(name) }
    }
}

/** Pure helpers over ledger rows — kept separable for unit tests. */
object LedgerMath {
    fun netSpendPaise(rows: List<TransactionEntity>): Long {
        val expenseIds = rows.filter { it.type == TxnType.EXPENSE }.map { it.id }.toSet()
        val expenses = rows.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountPaise }
        val refunds = rows
            .filter { it.type == TxnType.REFUND && it.refundOfTxnId in expenseIds }
            .sumOf { it.amountPaise }
        return expenses - refunds
    }
}

object MonthLabel {
    fun current(today: LocalDate = LocalDate.now()): String =
        today.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + today.year
}
