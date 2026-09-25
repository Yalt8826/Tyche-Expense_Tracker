package dev.yashas.expensetracker.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.yashas.expensetracker.data.db.entity.CategoryEntity
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.data.repo.LedgerInvariants
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.data.rules.SuggestionEngine
import dev.yashas.expensetracker.domain.model.Provenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Entity → invariant-math row (04 §3 fields that invariants read). */
private fun TransactionEntity.toLedgerRow() = LedgerInvariants.Row(
    id = id,
    type = type,
    accountId = accountId,
    amountPaise = amountPaise,
    timestamp = timestamp,
    categoryKey = categoryKey,
    merchantName = merchantName,
    utrRef = utrRef,
    refundOfTxnId = refundOfTxnId,
    transferGroupId = transferGroupId,
)

/**
 * Transactions + Review Inbox state (S9/S10/S11). Review Inbox is a FILTERED MODE of
 * the ledger (00-MASTER §7) — same rows, provenance == AUTO_REVIEW — plus derived
 * review cards for duplicate/transfer suspicion.
 */
class LedgerViewModel(private val repo: TxnRepository) : ViewModel() {

    data class ReviewCard(
        val txn: TransactionEntity,
        val suggestion: SuggestionEngine.Suggestion?,
    )

    data class SuspectCard(
        val keepId: Long,
        val mergeId: Long,
        val amountPaise: Long,
        val merchant: String?,
    )

    data class TransferPairCard(
        val debitId: Long,
        val creditId: Long,
        val amountPaise: Long,
    )

    data class UiState(
        val loading: Boolean = true,
        val all: List<TransactionEntity> = emptyList(),
        val categories: Map<String, CategoryEntity> = emptyMap(),
        val query: String = "",
        val reviewCards: List<ReviewCard> = emptyList(),
        val suspectCards: List<SuspectCard> = emptyList(),
        val transferCards: List<TransferPairCard> = emptyList(),
    ) {
        val reviewCount: Int get() = reviewCards.size

        val filtered: List<TransactionEntity>
            get() {
                val q = query.trim()
                if (q.isEmpty()) return all
                val ql = q.lowercase()
                return all.filter { txn ->
                    txn.merchantName?.lowercase()?.contains(ql) == true ||
                        txn.vpa?.lowercase()?.contains(ql) == true ||
                        txn.categoryKey?.lowercase()?.contains(ql) == true ||
                        txn.note?.lowercase()?.contains(ql) == true
                }
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var lastDeleted: TransactionEntity? = null

    init {
        viewModelScope.launch {
            repo.observeAll().collect { rows ->
                val byId = rows.associateBy { it.id }
                val inbox = rows.filter { it.provenance == Provenance.AUTO_REVIEW }
                _state.update { s ->
                    s.copy(
                        all = rows,
                        loading = false,
                        reviewCards = inbox.map { txn ->
                            ReviewCard(
                                txn = txn,
                                suggestion = repo.suggestions.suggest(
                                    sender = "",
                                    merchant = txn.merchantName,
                                    vpa = txn.vpa,
                                ),
                            )
                        },
                        suspectCards = LedgerInvariants.duplicateSuspects(inbox.map { it.toLedgerRow() }).mapNotNull { (a, b) ->
                            val first = byId[a] ?: return@mapNotNull null
                            SuspectCard(keepId = a, mergeId = b, amountPaise = first.amountPaise, merchant = first.merchantName)
                        },
                        transferCards = LedgerInvariants.detectTransferPairs(inbox.map { it.toLedgerRow() }).mapNotNull { (d, c) ->
                            val debit = byId[d] ?: return@mapNotNull null
                            TransferPairCard(debitId = d, creditId = c, amountPaise = debit.amountPaise)
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            repo.observeCategories().collect { cats ->
                _state.update { it.copy(categories = cats.associateBy { c -> c.key }) }
            }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun confirm(txn: TransactionEntity, categoryKey: String, createRule: Boolean) {
        viewModelScope.launch { repo.confirmReview(txn, categoryKey, createRule) }
    }

    fun markTransfer(card: TransferPairCard) {
        viewModelScope.launch { repo.markTransferPair(card.debitId, card.creditId) }
    }

    /** Merge duplicates: keep the first, drop the second. */
    fun mergeSuspect(card: SuspectCard) {
        viewModelScope.launch { lastDeleted = repo.deleteForUndo(card.mergeId) }
    }

    fun deleteWithUndo(id: Long) {
        viewModelScope.launch { lastDeleted = repo.deleteForUndo(id) }
    }

    fun undoDelete() {
        val row = lastDeleted ?: return
        viewModelScope.launch {
            repo.restore(row)
            lastDeleted = null
        }
    }

    companion object {
        fun factory(repo: TxnRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = LedgerViewModel(repo) as T
            }
    }
}
