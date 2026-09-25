package dev.yashas.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.domain.model.TxnType
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home dashboard state (01-SCREENS S6): hero month total, review badge, recent list.
 * Month total EXCLUDES transfers and nets refunds of the month's rows (ledger rules).
 */
class HomeViewModel(private val repo: TxnRepository) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val reviewCount: Int = 0,
        val monthNetPaise: Long = 0,
        val monthLabel: String = "",
        val recent: List<TransactionEntity> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeReviewInbox().collect { inbox ->
                _state.update { it.copy(reviewCount = inbox.size, loading = false) }
            }
        }
        viewModelScope.launch {
            val monthStart = YearMonth.now().atDay(1).toEpochDay()
            val monthEnd = YearMonth.now().plusMonths(1).atDay(1).toEpochDay()
            repo.observeAll().collect { rows ->
                val monthRows = rows.filter {
                    (it.valueDate ?: it.timestamp / TransactionEntity.MILLIS_PER_DAY) in monthStart until monthEnd
                }
                val net = LedgerMath.netSpendPaise(monthRows)
                _state.update {
                    it.copy(
                        monthNetPaise = net,
                        monthLabel = MonthLabel.current(),
                        recent = rows.sortedByDescending { r -> r.timestamp }.take(5),
                    )
                }
            }
        }
    }

    fun confirmReview(txn: TransactionEntity, categoryKey: String, createRule: Boolean) {
        viewModelScope.launch { repo.confirmReview(txn, categoryKey, createRule) }
    }

    fun dismissReview(txn: TransactionEntity) {
        // dismissal = manual-delete with no undo surface here (Review Inbox UX refined in later P4 batch)
        viewModelScope.launch { repo.deleteForUndo(txn.id) }
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
