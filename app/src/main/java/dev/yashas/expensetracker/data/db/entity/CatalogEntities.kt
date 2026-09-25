package dev.yashas.expensetracker.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.yashas.expensetracker.domain.model.BudgetPeriod
import dev.yashas.expensetracker.domain.model.RuleSource
import dev.yashas.expensetracker.domain.model.SmsParsedState

/** Bank/payment accounts + their SMS sender codes (04-TECH §3). */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val last4Mask: String,
    /** Comma-separated sender codes, e.g. "AD-HDFCBK,AD-HDFCBK-MS" (funnel splits on ','). */
    val smsSenderAllowlist: String,
)

/** Category tree, one level of subcategories via parentId (04-TECH §3). */
@Entity(tableName = "categories", indices = [Index(value = ["key"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val name: String,
    /** Material icon name the UI maps (kept as data, not drawable refs). */
    val icon: String,
    /** Token name from the theme series palette (02-CHARTS series colors). */
    val colorToken: String,
    val parentId: Long?,
)

/** Free-form user tags, many-to-many with transactions. */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "txn_tags",
    primaryKeys = ["txnId", "tagId"],
    indices = [Index(value = ["tagId"])],
)
data class TxnTagCrossRef(
    val txnId: Long,
    val tagId: Long,
)

/** Deterministic categorization rules; grow from user corrections (04-TECH §4/§5). */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Compact predicate DSL, e.g. "vpa~swiggy" / "merchant~IRCTC" (P3 formalizes). */
    val predicate: String,
    val actionCategoryKey: String,
    val source: RuleSource,
    val hitCount: Long = 0,
)

/** Per-bank SMS templates learned/seeded by digit-masked signature (04-TECH §4). */
@Entity(tableName = "templates", indices = [Index(value = ["signature"], unique = true)])
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankId: String,
    /** Digit-masked SMS pattern; named-group fieldMap extracts the fields. */
    val signature: String,
    val fieldMap: String,
    val version: Int,
    val hitCount: Long = 0,
)

/** Budgets; categoryKey NULL = overall budget. */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryKey: String?,
    val amountPaise: Long,
    val period: BudgetPeriod,
    val rollover: Boolean,
)

/** Raw SMS store; retention purged N days post-parse per Settings (04-TECH §3). */
@Entity(tableName = "sms_raw", indices = [Index(value = ["digest"], unique = true)])
data class SmsRawEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val timestamp: Long,
    val body: String,
    /** SHA-256 of the normalized body — hard dedup of carrier redeliveries. */
    val digest: String,
    val parsedState: SmsParsedState,
)
