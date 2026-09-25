package dev.yashas.expensetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.yashas.expensetracker.data.db.dao.CatalogDao
import dev.yashas.expensetracker.data.db.dao.TransactionDao
import dev.yashas.expensetracker.data.db.entity.AccountEntity
import dev.yashas.expensetracker.data.db.entity.BudgetEntity
import dev.yashas.expensetracker.data.db.entity.CategoryEntity
import dev.yashas.expensetracker.data.db.entity.RuleEntity
import dev.yashas.expensetracker.data.db.entity.SmsRawEntity
import dev.yashas.expensetracker.data.db.entity.TagEntity
import dev.yashas.expensetracker.data.db.entity.TemplateEntity
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.data.db.entity.TxnTagCrossRef

/**
 * Ledger database. Exported schemas are REQUIRED from day one (04-TECH §3);
 * migrations are appended here as versions evolve, never rewritten.
 */
@Database(
    version = 1,
    exportSchema = true,
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        TxnTagCrossRef::class,
        RuleEntity::class,
        BudgetEntity::class,
        TemplateEntity::class,
        SmsRawEntity::class,
    ],
)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun catalogDao(): CatalogDao

    companion object {
        const val NAME = "expense-tracker.db"

        /** Migration template for v1 → v2; kept so the pattern is established from day one. */
        val MIGRATION_1_2_PLACEHOLDER = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                throw UnsupportedOperationException("v2 schema not defined yet")
            }
        }
    }
}
