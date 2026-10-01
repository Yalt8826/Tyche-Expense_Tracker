package dev.yashas.expensetracker.data.export

import androidx.room.withTransaction
import dev.yashas.expensetracker.data.db.ExpenseDatabase
import dev.yashas.expensetracker.data.db.entity.AccountEntity
import dev.yashas.expensetracker.data.db.entity.BudgetEntity
import dev.yashas.expensetracker.data.db.entity.CategoryEntity
import dev.yashas.expensetracker.data.db.entity.RuleEntity
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.data.db.entity.TxnTagCrossRef
import dev.yashas.expensetracker.domain.model.BudgetPeriod
import dev.yashas.expensetracker.domain.model.Direction
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.RuleSource
import dev.yashas.expensetracker.domain.model.SmsParsedState
import dev.yashas.expensetracker.domain.model.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * P6 backup/restore: a single JSON file carries EVERYTHING needed to move the app
 * between phones — ledger, accounts, categories, budgets, rules, tags, prefs — with
 * `exportedAt` + `appVersion` for the import preview. Restore is replace-all inside
 * one transaction; IDs are preserved so refund/transfer links survive.
 * Zero-network: reading/writing a user-picked file only, via SAF URIs.
 */
object BackupCodec {

    @Serializable
    data class Backup(
        val appVersion: Int,
        val exportedAt: Long,
        val schema: Int = 1,
        val displayName: String? = null,
        val transactions: List<Txn> = emptyList(),
        val accounts: List<Account> = emptyList(),
        val categories: List<Category> = emptyList(),
        val budgets: List<Budget> = emptyList(),
        val rules: List<Rule> = emptyList(),
        val tags: List<Tag> = emptyList(),
        val txnTags: List<TxnTag> = emptyList(),
    )

    @Serializable
    data class Txn(
        val id: Long,
        val timestamp: Long,
        val valueDate: Long?,
        val amountPaise: Long,
        val type: String,
        val accountId: Long,
        val merchantName: String?,
        val vpa: String?,
        val categoryKey: String?,
        val note: String?,
        val provenance: String,
        val refundOfTxnId: Long?,
        val transferGroupId: String?,
        val utrRef: String?,
        val ruleAppliedKey: String?,
    )

    @Serializable
    data class Account(val id: Long, val bankName: String, val last4Mask: String, val smsSenderAllowlist: String)

    @Serializable
    data class Category(val id: Long, val key: String, val name: String, val icon: String, val colorToken: String, val parentId: Long?)

    @Serializable
    data class Budget(val categoryKey: String?, val amountPaise: Long, val period: String, val rollover: Boolean)

    @Serializable
    data class Rule(val predicate: String, val actionCategoryKey: String, val source: String, val hitCount: Long)

    @Serializable
    data class Tag(val id: Long, val name: String)

    @Serializable
    data class TxnTag(val txnId: Long, val tagId: Long)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // ── export ──

    suspend fun export(db: ExpenseDatabase, displayName: String?, appVersion: Int): Backup {
        val txnDao = db.transactionDao()
        val catalog = db.catalogDao()
        val txns = txnDao.observeAll().first()
        return Backup(
            appVersion = appVersion,
            exportedAt = System.currentTimeMillis(),
            displayName = displayName,
            transactions = txns.map { t ->
                Txn(
                    t.id, t.timestamp, t.valueDate, t.amountPaise, t.type.name, t.accountId,
                    t.merchantName, t.vpa, t.categoryKey, t.note, t.provenance.name,
                    t.refundOfTxnId, t.transferGroupId, t.utrRef, t.ruleAppliedKey,
                )
            },
            accounts = catalog.observeAccounts().first().map { Account(it.id, it.bankName, it.last4Mask, it.smsSenderAllowlist) },
            categories = catalog.observeCategories().first().map { Category(it.id, it.key, it.name, it.icon, it.colorToken, it.parentId) },
            budgets = catalog.observeBudgets().first().map { Budget(it.categoryKey, it.amountPaise, it.period.name, it.rollover) },
            rules = catalog.allRules().map { Rule(it.predicate, it.actionCategoryKey, it.source.name, it.hitCount) },
            tags = db.catalogDao().observeTags().first().map { Tag(it.id, it.name) },
            txnTags = db.catalogDao().observeTxnTags().first().map { TxnTag(it.txnId, it.tagId) },
        )
    }

    fun encode(backup: Backup): String = json.encodeToString(Backup.serializer(), backup)

    fun decode(text: String): Backup = json.decodeFromString(Backup.serializer(), text)

    /** Human-readable summary for the import preview dialog. */
    fun summarize(b: Backup): String =
        "${b.transactions.size} transactions · ${b.accounts.size} accounts · ${b.categories.size} categories · " +
            "${b.budgets.size} budgets · ${b.rules.size} rules" +
            (b.displayName?.let { " · $it" } ?: "")

    // ── restore (replace-all, single transaction) ──

    suspend fun restore(db: ExpenseDatabase, backup: Backup) {
        val txnDao = db.transactionDao()
        val catalog = db.catalogDao()

        db.withTransaction {
            // wipe current contents (categories re-seeded from the backup itself)
            txnDao.deleteAllTransactions()
            catalog.clearAccounts()
            catalog.clearCategories()
            catalog.clearBudgets()
            catalog.clearRules()
            catalog.clearTags()

            catalog.upsertAccountAll(backup.accounts.map { AccountEntity(it.id, it.bankName, it.last4Mask, it.smsSenderAllowlist) })
            catalog.upsertCategoryAll(backup.categories.map { CategoryEntity(it.id, it.key, it.name, it.icon, it.colorToken, it.parentId) })
            backup.budgets.forEach {
                catalog.upsertBudget(
                    BudgetEntity(categoryKey = it.categoryKey, amountPaise = it.amountPaise, period = BudgetPeriod.valueOf(it.period), rollover = it.rollover),
                )
            }
            backup.rules.forEach {
                catalog.insertRule(RuleEntity(predicate = it.predicate, actionCategoryKey = it.actionCategoryKey, source = RuleSource.valueOf(it.source), hitCount = it.hitCount))
            }
            backup.tags.forEach { catalog.upsertTag(dev.yashas.expensetracker.data.db.entity.TagEntity(it.id, it.name)) }
            backup.txnTags.forEach { catalog.tagTxn(TxnTagCrossRef(it.txnId, it.tagId)) }

            txnDao.insertAllPreservingIds(
                backup.transactions.map { t ->
                    TransactionEntity(
                        id = t.id,
                        timestamp = t.timestamp,
                        valueDate = t.valueDate,
                        amountPaise = t.amountPaise,
                        type = TxnType.valueOf(t.type),
                        accountId = t.accountId,
                        merchantName = t.merchantName,
                        vpa = t.vpa,
                        categoryKey = t.categoryKey,
                        note = t.note,
                        provenance = Provenance.valueOf(t.provenance),
                        sourceSmsId = null,
                        refundOfTxnId = t.refundOfTxnId,
                        transferGroupId = t.transferGroupId,
                        utrRef = t.utrRef,
                        ruleAppliedKey = t.ruleAppliedKey,
                    )
                },
            )
        }
    }
}
