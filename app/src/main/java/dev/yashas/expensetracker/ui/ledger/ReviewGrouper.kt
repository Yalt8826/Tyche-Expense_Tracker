package dev.yashas.expensetracker.ui.ledger

import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.data.rules.SuggestionEngine

/**
 * Groups pending-review rows by payee so one tap confirms a whole family of
 * transactions (UI-AUDIT R1: 84 items must not take 250+ taps). Pure + tested.
 */
object ReviewGrouper {

    data class Item(val txn: TransactionEntity, val suggestion: SuggestionEngine.Suggestion?)

    data class Group(
        val key: String,
        val displayName: String,
        /** Most recent first. */
        val items: List<TransactionEntity>,
        val totalPaise: Long,
        /** First non-null suggestion among the group's items. */
        val suggestion: SuggestionEngine.Suggestion?,
    )

    fun keyOf(txn: TransactionEntity): String =
        (txn.merchantName ?: txn.vpa ?: "unknown").trim().lowercase()

    fun group(items: List<Item>): List<Group> =
        items.groupBy { keyOf(it.txn) }.map { (_, list) ->
            val sorted = list.sortedByDescending { it.txn.timestamp }
            val first = sorted.first().txn
            Group(
                key = keyOf(first),
                displayName = first.merchantName ?: first.vpa ?: "Unknown",
                items = sorted.map { it.txn },
                totalPaise = sorted.sumOf { it.txn.amountPaise },
                suggestion = sorted.firstOrNull { it.suggestion != null }?.suggestion,
            )
        }.sortedByDescending { it.items.first().timestamp }
}
