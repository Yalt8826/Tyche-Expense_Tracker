package dev.yashas.expensetracker.data.repo

import dev.yashas.expensetracker.data.db.ExpenseDatabase
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.data.rules.SuggestionEngine
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType
import kotlinx.coroutines.flow.Flow

/**
 * Ledger repository — all UI surfaces read through here; transactions are the
 * single source of truth (00-MASTER §2.1).
 */
class TxnRepository(
    private val db: ExpenseDatabase,
    val suggestions: SuggestionEngine,
) {
    fun observeAll(): Flow<List<TransactionEntity>> = db.transactionDao().observeAll()

    fun observeCategories(): Flow<List<dev.yashas.expensetracker.data.db.entity.CategoryEntity>> =
        db.catalogDao().observeCategories()

    fun observeReviewInbox(): Flow<List<TransactionEntity>> = db.transactionDao().observeReviewInbox()

    suspend fun byId(id: Long): TransactionEntity? = db.transactionDao().byId(id)

    /** Review confirm: human owns meaning — category set, row becomes confirmed. */
    suspend fun confirmReview(txn: TransactionEntity, categoryKey: String, createRule: Boolean) {
        db.transactionDao().confirmWithCategory(txn.id, Provenance.AUTO_CONFIRMED, categoryKey)
        if (createRule) {
            suggestions.createRuleFromCorrection(txn.merchantName, txn.vpa, categoryKey)
        }
    }

    /** Confirm by id — the review flow works from group snapshots, not live entities. */
    suspend fun confirmById(id: Long, categoryKey: String, createRule: Boolean) {
        val txn = db.transactionDao().byId(id) ?: return
        confirmReview(txn, categoryKey, createRule)
    }

    /** Undo path for batch review: back to the review queue. */
    suspend fun setProvenance(id: Long, provenance: Provenance) {
        db.transactionDao().updateProvenance(id, provenance)
    }

    /**
     * User-created tag from Home (color chosen in-app). Keys are namespaced `user_*`
     * so seeded categories stay stable; duplicate names reuse the existing row.
     */
    suspend fun createCategory(name: String, colorToken: String): dev.yashas.expensetracker.data.db.entity.CategoryEntity {
        val clean = name.trim().take(24)
        val key = dev.yashas.expensetracker.data.rules.CategorySeed.slugFor(clean)
        val existing = db.catalogDao().categoryByKey(key)
        if (existing != null) return existing
        val entity = dev.yashas.expensetracker.data.db.entity.CategoryEntity(
            key = key,
            name = clean,
            icon = "category",
            colorToken = colorToken,
            parentId = null,
        )
        db.catalogDao().upsertCategory(entity)
        return db.catalogDao().categoryByKey(key) ?: entity
    }

    /** Quick-add / manual add (01-SCREENS S7): cash + anything the funnel misses. */
    suspend fun quickAddExpense(
        amountPaise: Long,
        categoryKey: String?,
        merchant: String?,
        note: String?,
        valueDateEpochDay: Long,
        nowMillis: Long,
    ): Long = db.transactionDao().insert(
        TransactionEntity(
            timestamp = nowMillis,
            valueDate = valueDateEpochDay,
            amountPaise = amountPaise,
            type = TxnType.EXPENSE,
            accountId = MANUAL_ACCOUNT_ID,
            merchantName = merchant,
            vpa = null,
            categoryKey = categoryKey,
            note = note,
            provenance = Provenance.MANUAL,
            sourceSmsId = null,
            refundOfTxnId = null,
            transferGroupId = null,
            utrRef = null,
            ruleAppliedKey = "manual",
        ),
    )

    /** Transfer pairing from the review flow: both legs marked, excluded from spend. */
    suspend fun markTransferPair(debitId: Long, creditId: Long) {
        val groupId = "tg-$debitId-$creditId"
        db.transactionDao().markTransfer(debitId, groupId)
        db.transactionDao().markTransfer(creditId, groupId)
    }

    /** Delete + undo pattern: caller re-inserts the returned row on undo. */
    suspend fun deleteForUndo(id: Long): TransactionEntity? {
        val row = db.transactionDao().byId(id) ?: return null
        db.transactionDao().delete(id)
        return row
    }

    suspend fun restore(row: TransactionEntity) {
        db.transactionDao().insert(row)
    }

    companion object {
        /** Synthetic account for manual/cash entries (no bank SMS behind them). */
        const val MANUAL_ACCOUNT_ID = 0L
    }
}
