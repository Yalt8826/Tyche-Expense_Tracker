package dev.yashas.expensetracker.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType

/**
 * The single source of truth ledger row (04-TECH §3).
 *
 * - amountPaise: Long paise, never floats (§3a).
 * - timestamp: SMS/manual entry time (epochMillis). valueDate: spend date (epochDay),
 *   parsed from the SMS body when present, else derived from timestamp at insert.
 * - Dedup: unique (accountId, utrRef) where present; SQLite treats NULLs as distinct,
 *   so manual/no-UTR rows are unconstrained.
 * - Transfers: both legs share transferGroupId. Refunds: refundOfTxnId links the source.
 * - No hard FKs on the ledger (deletions are user actions; netting JOINs tolerate orphans).
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["accountId", "utrRef"], unique = true),
        Index(value = ["valueDate"]),
        Index(value = ["type"]),
        Index(value = ["provenance"]),
        Index(value = ["transferGroupId"]),
        Index(value = ["refundOfTxnId"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val valueDate: Long?,
    val amountPaise: Long,
    val type: TxnType,
    val accountId: Long,
    val merchantName: String?,
    val vpa: String?,
    val categoryKey: String?,
    val note: String?,
    val provenance: Provenance,
    val sourceSmsId: Long?,
    val refundOfTxnId: Long?,
    val transferGroupId: String?,
    val utrRef: String?,
    val ruleAppliedKey: String?,
) {
    companion object {
        /** Milliseconds per day, UTC-anchored — used only where valueDate is absent pre-insert. */
        const val MILLIS_PER_DAY: Long = 86_400_000L
    }
}
