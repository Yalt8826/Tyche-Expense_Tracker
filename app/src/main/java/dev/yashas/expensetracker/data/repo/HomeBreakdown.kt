package dev.yashas.expensetracker.data.repo

import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.domain.model.TxnType

/**
 * Home proportion-bar math (new Home widget): this month's confirmed expenses split by
 * tag, each with a fraction of the total and a stable color token. Pure + tested.
 */
object HomeBreakdown {

    data class Slice(
        val categoryKey: String,
        val label: String,
        val colorToken: String,
        val amountPaise: Long,
        /** 0..1 share of the total; slices are ordered largest first. */
        val fraction: Float,
    )

    /**
     * Confirmed EXPENSE rows only (review-pending and transfers/refunds never distort
     * the visual). Uncategorised spend is folded into "Untagged" so the bar always
     * covers 100% of what the hero shows.
     */
    fun slices(
        rows: List<TransactionEntity>,
        nameByKey: Map<String, String>,
        colorTokenByKey: Map<String, String>,
    ): List<Slice> {
        val totals = linkedMapOf<String, Long>()
        for (row in rows) {
            if (row.type != TxnType.EXPENSE) continue
            if (row.provenance != dev.yashas.expensetracker.domain.model.Provenance.AUTO_CONFIRMED &&
                row.provenance != dev.yashas.expensetracker.domain.model.Provenance.MANUAL
            ) continue
            val key = row.categoryKey ?: "untagged"
            totals[key] = (totals[key] ?: 0L) + row.amountPaise
        }
        val total = totals.values.sum().takeIf { it > 0 } ?: return emptyList()
        return totals.entries
            .sortedByDescending { it.value }
            .map { (key, amount) ->
                Slice(
                    categoryKey = key,
                    label = nameByKey[key] ?: if (key == "untagged") "Untagged" else key,
                    colorToken = colorTokenByKey[key] ?: "series_violet",
                    amountPaise = amount,
                    fraction = amount.toFloat() / total,
                )
            }
    }
}
