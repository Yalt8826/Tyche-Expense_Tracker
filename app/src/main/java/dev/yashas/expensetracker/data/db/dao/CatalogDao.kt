package dev.yashas.expensetracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.yashas.expensetracker.data.db.entity.AccountEntity
import dev.yashas.expensetracker.data.db.entity.BudgetEntity
import dev.yashas.expensetracker.data.db.entity.CategoryEntity
import dev.yashas.expensetracker.data.db.entity.RuleEntity
import dev.yashas.expensetracker.data.db.entity.SmsRawEntity
import dev.yashas.expensetracker.data.db.entity.TagEntity
import dev.yashas.expensetracker.data.db.entity.TemplateEntity
import dev.yashas.expensetracker.data.db.entity.TxnTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    // Accounts
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAccount(account: AccountEntity): Long

    @Query("SELECT * FROM accounts ORDER BY bankName")
    fun observeAccounts(): Flow<List<AccountEntity>>

    /** Sender allowlist for the funnel stage 1 (04-TECH §4). */
    @Query("SELECT smsSenderAllowlist FROM accounts")
    suspend fun allSenderAllowlists(): List<String>

    // Categories
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE key = :key")
    suspend fun categoryByKey(key: String): CategoryEntity?

    // Tags
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun tagTxn(crossRef: TxnTagCrossRef)

    // Rules
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Query("SELECT * FROM rules ORDER BY source DESC, hitCount DESC")
    suspend fun allRules(): List<RuleEntity>

    @Query("UPDATE rules SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun bumpRuleHit(id: Long)

    // Templates
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertTemplate(template: TemplateEntity): Long

    @Query("SELECT * FROM templates WHERE signature = :signature")
    suspend fun templateBySignature(signature: String): TemplateEntity?

    @Query("UPDATE templates SET hitCount = hitCount + 1 WHERE id = :id")
    suspend fun bumpTemplateHit(id: Long)

    // Budgets
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity): Long

    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: Long)

    // Raw SMS
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSms(sms: SmsRawEntity): Long

    @Query("SELECT * FROM sms_raw WHERE digest = :digest")
    suspend fun smsByDigest(digest: String): SmsRawEntity?

    @Query("SELECT * FROM sms_raw WHERE id = :id")
    suspend fun smsById(id: Long): SmsRawEntity?

    @Query("DELETE FROM sms_raw WHERE parsedState = 'PARSED' AND timestamp < :cutoff")
    suspend fun purgeParsedOlderThan(cutoff: Long): Int
}
