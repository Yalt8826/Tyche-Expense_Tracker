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
 * Transactions + Review Inbox state (S9/S10/S11), post-audit:
 * - month strip filter (R2)
 * - Review Inbox grouped by payee, one-tap confirm chips, batch confirm + undo (R1)
 */
class LedgerViewModel(private val repo: TxnRepository) : ViewModel() {

    data class ReviewGroupUi(
        val key: String,
        val displayName: String,
        val count: Int,
        val totalPaise: Long,
        val latestDayLabel: String,
        val suggestion: SuggestionEngine.Suggestion?,
        val ids: List<Long>,
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
        val periodKey: String = LedgerFilters.THIS_MONTH,
        /** Custom From/To (epochDay, end-exclusive) set from the calendar picker. */
        val customFrom: Long? = null,
        val customTo: Long? = null,
        val reviewGroups: List<ReviewGroupUi> = emptyList(),
        val suspectCards: List<SuspectCard> = emptyList(),
        val transferCards: List<TransferPairCard> = emptyList(),
        val lastUndoneCount: Int = 0,
    ) {
        val reviewCount: Int get() = reviewGroups.sumOf { it.count }

        val presetOptions: List<LedgerFilters.MonthOption> get() = LedgerFilters.presetOptions()

        /** Bounds for the active selection: presets use their fixed window; RANGE uses custom. */
        val activeBounds: Pair<Long, Long>?
            get() = when (periodKey) {
                LedgerFilters.RANGE -> {
                    val f = customFrom
                    val t = customTo
                    if (f != null && t != null && t > f) f to t else null
                }
                else -> presetOptions.firstOrNull { it.key == periodKey }?.let { opt ->
                    val s = opt.start
                    val e = opt.end
                    if (s != null && e != null) s to e else null
                }
            }

        val periodFiltered: List<TransactionEntity>
            get() {
                val (start, end) = activeBounds ?: return all
                return all.filter { (it.valueDate ?: 0) >= start && (it.valueDate ?: 0) < end }
            }

        val filtered: List<TransactionEntity>
            get() {
                val base = periodFiltered
                val q = query.trim()
                if (q.isEmpty()) return base
                val ql = q.lowercase()
                return base.filter { txn ->
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
    private var lastBatchIds: List<Long>? = null

    init {
        viewModelScope.launch {
            repo.observeAll().collect { rows ->
                val byId = rows.associateBy { it.id }
                val inbox = rows.filter { it.provenance == Provenance.AUTO_REVIEW }
                val groups = ReviewGrouper.group(
                    inbox.map { ReviewGrouper.Item(it, repo.suggestions.suggest("", it.merchantName, it.vpa)) },
                ).map { g ->
                    val first = g.items.first()
                    ReviewGroupUi(
                        key = g.key,
                        displayName = g.displayName,
                        count = g.items.size,
                        totalPaise = g.totalPaise,
                        latestDayLabel = LedgerFilters.dayLabel(first.valueDate ?: (first.timestamp / 86_400_000L)),
                        suggestion = g.suggestion,
                        ids = g.items.map { it.id },
                    )
                }
                _state.update { s ->
                    s.copy(
                        all = rows,
                        loading = false,
                        reviewGroups = groups,
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

    fun setPeriod(key: String) = _state.update { it.copy(periodKey = key, customFrom = null, customTo = null) }

    /** Custom From/To from the calendar picker (end-exclusive), switches to RANGE mode. */
    fun setCustomRange(fromEpochDay: Long, toEpochDay: Long) {
        if (toEpochDay > fromEpochDay) {
            _state.update { it.copy(periodKey = LedgerFilters.RANGE, customFrom = fromEpochDay, customTo = toEpochDay) }
        }
    }

    /** Label for the active selection, shown next to the calendar button. */
    fun activeRangeLabel(): String {
        val s = state.value
        return when (s.periodKey) {
            LedgerFilters.RANGE -> {
                val f = s.customFrom
                val t = s.customTo
                if (f != null && t != null) LedgerFilters.rangeLabel(f, t) else "Custom range"
            }
            else -> s.presetOptions.firstOrNull { it.key == s.periodKey }?.label ?: ""
        }
    }

    /** Confirm one item. Creates a rule so the whole payee family confirms next time. */
    fun confirm(id: Long, categoryKey: String) {
        viewModelScope.launch { repo.confirmById(id, categoryKey, createRule = true) }
    }

    /** Confirm every item in a group under one category; remembered for undo. */
    fun confirmGroup(group: ReviewGroupUi, categoryKey: String) {
        lastBatchIds = group.ids
        _state.update { it.copy(lastUndoneCount = group.ids.size) }
        viewModelScope.launch { group.ids.forEach { repo.confirmById(it, categoryKey, createRule = true) } }
    }

    /** Undo the last batch: rows return to the review queue. */
    fun undoLastBatch() {
        val ids = lastBatchIds ?: return
        viewModelScope.launch {
            ids.forEach { repo.setProvenance(it, Provenance.AUTO_REVIEW) }
            lastBatchIds = null
            _state.update { it.copy(lastUndoneCount = 0) }
        }
    }

    /** Snackbar dismissed without undo. */
    fun dismissUndo() {
        lastBatchIds = null
        _state.update { it.copy(lastUndoneCount = 0) }
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
