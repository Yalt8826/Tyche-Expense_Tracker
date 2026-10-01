package dev.yashas.expensetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.yashas.expensetracker.data.db.entity.SmsRawEntity
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType
import kotlinx.coroutines.flow.Flow

/**
 * Ledger DAO. Invariant-critical aggregates (spend excluding transfers, refund netting)
 * live in LedgerInvariants (pure Kotlin, unit-tested) and SQL here mirrors those rules.
 */
@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(txn: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(txns: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(txn: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    /** Dedup guard: (account, utr) unique index makes this the insert conflict path. */
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId AND utrRef = :utr")
    suspend fun countByUtr(accountId: Long, utr: String): Int

    // --- Aggregates. EXPENSE only; refunds net in LedgerInvariants/SQL below; ---

    @Query(
        """
        SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
        WHERE type = 'EXPENSE' AND (:from IS NULL OR valueDate >= :from)
          AND (:to IS NULL OR valueDate < :to)
        """
    )
    suspend fun grossExpensePaise(from: Long?, to: Long?): Long

    @Query(
        """
        SELECT valueDate AS day, type, SUM(amountPaise) AS amountPaise
        FROM transactions
        WHERE type IN ('EXPENSE', 'INCOME') AND valueDate IS NOT NULL
          AND (:from IS NULL OR valueDate >= :from) AND (:to IS NULL OR valueDate < :to)
        GROUP BY valueDate, type
        """
    )
    suspend fun dailySeriesByType(from: Long?, to: Long?): List<DailyTypeRow>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE (:from IS NULL OR valueDate >= :from) AND (:to IS NULL OR valueDate < :to)
        """
    )
    suspend fun countInRange(from: Long?, to: Long?): Int

    /** Demo-seed marker: rows tagged ruleAppliedKey='demo'. */
    @Query("SELECT COUNT(*) FROM transactions WHERE ruleAppliedKey = 'demo'")
    suspend fun countDemo(): Int

    /** P6 demo-wipe: remove every demo-seeded row. */
    @Query("DELETE FROM transactions WHERE ruleAppliedKey = 'demo'")
    suspend fun deleteDemo(): Int

    /** P6 wipe-all: the irreversible reset behind the double-confirm dialog. */
    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions(): Int

    /** Overall budget limit (budgets.categoryKey IS NULL), 0 when none. */
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM budgets WHERE categoryKey IS NULL")
    suspend fun overallBudgetPaise(): Long?

    /** Per-category limits as (categoryKey, limitPaise). */
    @Query("SELECT categoryKey AS label, amountPaise AS amountPaise, 0 AS txnCount FROM budgets WHERE categoryKey IS NOT NULL")
    suspend fun categoryLimits(): List<CategoryTotalRow>

    /** Refund netting: refunds joined to their source expense's category (04 §4). */
    @Query(
        """
        SELECT COALESCE(SUM(r.amountPaise), 0) FROM transactions r
        WHERE r.type = 'REFUND' AND (:from IS NULL OR r.valueDate >= :from)
          AND (:to IS NULL OR r.valueDate < :to)
          AND (:category IS NULL OR EXISTS (
                SELECT 1 FROM transactions s WHERE s.id = r.refundOfTxnId AND s.categoryKey = :category))
        """
    )
    suspend fun refundNetPaise(from: Long?, to: Long?, category: String?): Long

    @Query(
        """
        SELECT categoryKey AS label, SUM(amountPaise) AS amountPaise, COUNT(*) AS txnCount
        FROM transactions
        WHERE type = 'EXPENSE' AND categoryKey IS NOT NULL
          AND (:from IS NULL OR valueDate >= :from) AND (:to IS NULL OR valueDate < :to)
        GROUP BY categoryKey ORDER BY SUM(amountPaise) DESC
        """
    )
    suspend fun categoryBreakdown(from: Long?, to: Long?): List<CategoryTotalRow>

    @Query(
        """
        SELECT valueDate AS day, SUM(amountPaise) AS amountPaise
        FROM transactions
        WHERE type = 'EXPENSE' AND valueDate IS NOT NULL
          AND (:from IS NULL OR valueDate >= :from) AND (:to IS NULL OR valueDate < :to)
        GROUP BY valueDate ORDER BY day
        """
    )
    suspend fun dailySeries(from: Long?, to: Long?): List<DailyTotalRow>

    @Query(
        """
        SELECT COALESCE(merchantName, '(unknown)') AS label, SUM(amountPaise) AS amountPaise, COUNT(*) AS txnCount
        FROM transactions
        WHERE type = 'EXPENSE'
          AND (:from IS NULL OR valueDate >= :from) AND (:to IS NULL OR valueDate < :to)
        GROUP BY COALESCE(merchantName, '(unknown)') ORDER BY SUM(amountPaise) DESC LIMIT :limit
        """
    )
    suspend fun topMerchants(from: Long?, to: Long?, limit: Int): List<CategoryTotalRow>

    /** Potential duplicate pair: same account+amount inside the window, kept for review. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE type = :type AND accountId = :accountId AND amountPaise = :amountPaise
          AND timestamp BETWEEN :windowStart AND :windowEnd
        """
    )
    suspend fun findSimilarInWindow(
        type: TxnType,
        accountId: Long,
        amountPaise: Long,
        windowStart: Long,
        windowEnd: Long,
    ): List<TransactionEntity>

    @Query("UPDATE transactions SET provenance = :provenance, categoryKey = :categoryKey WHERE id = :id")
    suspend fun confirmWithCategory(id: Long, provenance: Provenance, categoryKey: String?)

    @Query("UPDATE transactions SET provenance = :provenance WHERE id = :id")
    suspend fun updateProvenance(id: Long, provenance: Provenance)

    @Query("UPDATE transactions SET type = 'TRANSFER', transferGroupId = :groupId WHERE id = :id")
    suspend fun markTransfer(id: Long, groupId: String)

    @Query("SELECT * FROM transactions WHERE transferGroupId = :groupId")
    suspend fun transferLegs(groupId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE provenance = 'AUTO_REVIEW' ORDER BY timestamp DESC")
    fun observeReviewInbox(): Flow<List<TransactionEntity>>

    /** History-based suggestion source: most frequent category for one merchant/vpa identity. */
    @Query(
        """
        SELECT categoryKey AS label, SUM(amountPaise) AS amountPaise, COUNT(*) AS txnCount
        FROM transactions
        WHERE type = 'EXPENSE' AND categoryKey IS NOT NULL
          AND (LOWER(COALESCE(merchantName, '')) = :identity OR LOWER(COALESCE(vpa, '')) = :identity)
        GROUP BY categoryKey ORDER BY COUNT(*) DESC LIMIT 1
        """
    )
    suspend fun historyCategoryCounts(identity: String): List<CategoryTotalRow>
}

data class CategoryTotalRow(val label: String, val amountPaise: Long, val txnCount: Int)

data class DailyTotalRow(val day: Long, val amountPaise: Long)

data class DailyTypeRow(val day: Long, val type: TxnType, val amountPaise: Long)
