package dev.yashas.expensetracker

import android.content.Context
import androidx.room.Room
import dev.yashas.expensetracker.data.capture.CaptureEngine
import dev.yashas.expensetracker.data.capture.SmsIngestor
import dev.yashas.expensetracker.data.db.ExpenseDatabase
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.data.repo.AnalyticsRepository
import dev.yashas.expensetracker.data.repo.BudgetRepository
import dev.yashas.expensetracker.data.rules.CategorySeed
import dev.yashas.expensetracker.data.rules.SuggestionEngine
import kotlinx.coroutines.flow.first

/**
 * Manual DI (04-TECH §1 allows "manual DI if scope stays small — decide at scaffold time";
 * decided at P3: v1 has exactly one DB and two consumers, Hilt would be ceremony).
 * Everything is lazy per-process; the DB is the singleton ledger.
 */
object AppGraph {

    @Volatile
    private var database: ExpenseDatabase? = null

    fun database(context: Context): ExpenseDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                ExpenseDatabase::class.java,
                ExpenseDatabase.NAME,
            ).build().also { database = it }
        }

    fun smsIngestor(context: Context): SmsIngestor =
        SmsIngestor(db = database(context), engine = CaptureEngine())

    suspend fun seedBanksIfNeeded(context: Context) {
        val db = database(context)
        if (db.catalogDao().allSenderAllowlists().isNotEmpty()) return
        // Seed allowlists from the bundled bank set (P3) — rows are real accounts once the
        // user's first SMS arrives and masks resolve (SmsIngestor.resolveAccount).
        dev.yashas.expensetracker.data.capture.BankTemplates.SEEDS.forEach { seed ->
            db.catalogDao().upsertAccount(
                dev.yashas.expensetracker.data.db.entity.AccountEntity(
                    bankName = seed.bankId,
                    last4Mask = "----",
                    smsSenderAllowlist = seed.senderCodes.joinToString(","),
                ),
            )
        }
    }

    /** On first run after install: seed + (no historic import — day-zero start, 00 §5). */
    suspend fun bootstrap(context: Context) {
        val db = database(context)
        CategorySeed.seed(db.catalogDao())
        seedBanksIfNeeded(context)
    }

    fun txnRepository(context: Context): TxnRepository =
        TxnRepository(
            db = database(context),
            suggestions = SuggestionEngine(database(context)),
        )

    fun analyticsRepository(context: Context): AnalyticsRepository =
        AnalyticsRepository(database(context).transactionDao())

    fun budgetRepository(context: Context): BudgetRepository =
        BudgetRepository(
            dao = database(context).transactionDao(),
            catalog = database(context).catalogDao(),
        )
}
