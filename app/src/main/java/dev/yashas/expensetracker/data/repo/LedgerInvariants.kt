package dev.yashas.expensetracker.data.repo

import dev.yashas.expensetracker.domain.model.TxnType

/**
 * Ledger invariants as pure functions (04-TECH §8 "Ledger invariants"):
 * transfer exclusion, refund netting, paise math — all unit-testable without Android.
 *
 * Rules (00-MASTER §4):
 * - Transfers are not expenses: excluded from every spend surface.
 * - Refunds reverse expenses: netted against their source's category, never counted as income.
 * - Amounts are paise Longs everywhere; no rounding exists in the ledger path.
 */
object LedgerInvariants {

    /** Gross spend: plain sum over expense rows (transfers/refunds never qualify). */
    fun grossExpensePaise(rows: List<Row>): Long =
        rows.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountPaise }

    /** Net spend: expenses minus refunds whose SOURCE expense is in the same window/row-set. */
    fun netExpensePaise(rows: List<Row>): Long {
        val expenseIds = rows.filter { it.type == TxnType.EXPENSE }.map { it.id }.toSet()
        val expenses = rows.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountPaise }
        val refundsNetted = rows
            .filter { it.type == TxnType.REFUND && it.refundOfTxnId != null && it.refundOfTxnId in expenseIds }
            .sumOf { it.amountPaise }
        return expenses - refundsNetted
    }

    /** Category totals with refunds netted into their source category; unlinked refunds excluded. */
    fun netByCategory(rows: List<Row>): Map<String, Long> {
        val sourceCategoryById = rows.filter { it.type == TxnType.EXPENSE }.associate { it.id to it.categoryKey }
        val expenses = mutableMapOf<String, Long>()
        val refunds = mutableMapOf<String, Long>()
        for (row in rows) {
            when (row.type) {
                TxnType.EXPENSE -> row.categoryKey?.let { expenses.merge(it, row.amountPaise, Long::plus) }
                TxnType.REFUND -> sourceCategoryById[row.refundOfTxnId]?.let { refunds.merge(it, row.amountPaise, Long::plus) }
                else -> Unit
            }
        }
        return (expenses.keys + refunds.keys)
            .associateWith { key -> (expenses[key] ?: 0L) - (refunds[key] ?: 0L) }
            .filterValues { it != 0L }
    }

    /**
     * Transfer grouping: same amount, distinct accounts, opposite tentative direction,
     * within the window. Only unpaired rows participate; callers mark both legs TRANSFER
     * with a shared transferGroupId (04-TECH §4).
     */
    fun detectTransferPairs(
        rows: List<Row>,
        windowMillis: Long = DEFAULT_TRANSFER_WINDOW_MILLIS,
    ): List<Pair<Long, Long>> {
        val debits = rows.filter { it.type == TxnType.EXPENSE && it.transferGroupId == null }
        val credits = rows.filter { it.type == TxnType.INCOME && it.transferGroupId == null }
        val pairs = mutableListOf<Pair<Long, Long>>()
        val used = mutableSetOf<Long>()
        for (debit in debits) {
            if (debit.id in used) continue
            val match = credits.firstOrNull { credit ->
                credit.id !in used &&
                    credit.accountId != debit.accountId &&
                    credit.amountPaise == debit.amountPaise &&
                    kotlin.math.abs(credit.timestamp - debit.timestamp) <= windowMillis
            } ?: continue
            pairs += debit.id to match.id
            used += debit.id
            used += match.id
        }
        return pairs
    }

    /**
     * Duplicate suspicion: same account + same amount + same counterparty within the window
     * with differing UTRs. Surfaces a [Keep both]/[Merge] review card — the ledger never
     * auto-drops (00-MASTER §4); identical UTRs are already blocked by the DB unique index.
     */
    fun duplicateSuspects(rows: List<Row>, windowMillis: Long = 120_000L): List<Pair<Long, Long>> {
        val sorted = rows.sortedBy { it.timestamp }
        val pairs = mutableListOf<Pair<Long, Long>>()
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (b.timestamp - a.timestamp > windowMillis) break
                if (a.accountId == b.accountId &&
                    a.amountPaise == b.amountPaise &&
                    a.merchantName == b.merchantName &&
                    a.utrRef != b.utrRef
                ) {
                    pairs += a.id to b.id
                }
            }
        }
        return pairs
    }

    /** One ledger row in invariant math (id-stable, minimal fields). */
    data class Row(
        val id: Long,
        val type: TxnType,
        val accountId: Long,
        val amountPaise: Long,
        val timestamp: Long,
        val categoryKey: String?,
        val merchantName: String?,
        val utrRef: String?,
        val refundOfTxnId: Long?,
        val transferGroupId: String?,
    )

    const val DEFAULT_TRANSFER_WINDOW_MILLIS: Long = 30 * 60 * 1000L
}
