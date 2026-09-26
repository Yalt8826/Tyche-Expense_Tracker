package dev.yashas.expensetracker.data.repo

import dev.yashas.expensetracker.BuildConfig
import dev.yashas.expensetracker.data.db.ExpenseDatabase
import dev.yashas.expensetracker.data.db.entity.BudgetEntity
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.domain.model.BudgetPeriod
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * DEBUG-ONLY demo seeder: 40 days of synthetic, deterministic transactions so the UI
 * can be evaluated with a full ledger (hero, charts, donut, budgets, review inbox).
 * Every demo row is tagged ruleAppliedKey="demo" + note="demo" so it can be wiped
 * wholesale later (P6 data-management settings). Real SMS capture is unaffected.
 */
object DemoData {

    private data class Menu(
        val merchant: String,
        val category: String,
        val minPaise: Long,
        val maxPaise: Long,
        val weight: Int,
    )

    private val MENU = listOf(
        Menu("Swiggy", "food.restaurants", 18_000, 52_000, 5),
        Menu("Zomato", "food.restaurants", 22_000, 64_000, 4),
        Menu("Starbucks", "food.coffee", 21_000, 42_000, 2),
        Menu("Blinkit", "food.groceries", 35_000, 120_000, 3),
        Menu("Zepto", "food.groceries", 18_000, 60_000, 2),
        Menu("Uber", "transport.cab", 9_000, 32_000, 3),
        Menu("Ola", "transport.cab", 11_000, 38_000, 2),
        Menu("Indian Oil", "transport.fuel", 80_000, 180_000, 1),
        Menu("Amazon", "shopping", 45_000, 240_000, 1),
        Menu("Flipkart", "shopping", 60_000, 300_000, 1),
        Menu("Jio Recharge", "bills.recharge", 23_900, 34_900, 1),
        Menu("Netflix", "entertainment", 19_900, 19_900, 1),
        Menu("PharmEasy", "health", 15_000, 70_000, 1),
    )

    private val MENU_WEIGHT_TOTAL = MENU.sumOf { it.weight }

    suspend fun seedIfNeeded(db: ExpenseDatabase) {
        if (!BuildConfig.DEBUG) return
        val txnDao = db.transactionDao()
        if (txnDao.countDemo() > 0) return

        val accounts = db.catalogDao().observeAccounts().first()
        val bankIds = listOf("HDFC", "SBI", "ICICI", "AXIS").mapNotNull { bank ->
            accounts.firstOrNull { it.bankName == bank }?.id
        }.ifEmpty { listOf(1L) }

        val today = LocalDate.now()
        var utrCounter = 0
        fun nextUtr(): String = "DEMO" + (++utrCounter).toString().padStart(6, '0')

        fun millis(day: LocalDate, hour: Int, minute: Int): Long =
            day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        fun row(
            day: LocalDate,
            hour: Int,
            minute: Int,
            amountPaise: Long,
            type: TxnType,
            merchant: String?,
            categoryKey: String?,
            accountId: Long,
            provenance: Provenance = Provenance.AUTO_CONFIRMED,
            transferGroupId: String? = null,
            refundOf: Long? = null,
        ) = TransactionEntity(
            timestamp = millis(day, hour, minute),
            valueDate = day.toEpochDay(),
            amountPaise = amountPaise,
            type = type,
            accountId = accountId,
            merchantName = merchant,
            vpa = null,
            categoryKey = categoryKey,
            note = "demo",
            provenance = provenance,
            sourceSmsId = null,
            refundOfTxnId = refundOf,
            transferGroupId = transferGroupId,
            utrRef = nextUtr(),
            ruleAppliedKey = "demo",
        )

        val regular = mutableListOf<TransactionEntity>()

        // 40 days of daily spending, deterministic per day
        for (offset in 40 downTo 0) {
            val day = today.minusDays(offset.toLong())
            val rnd = Random((day.toEpochDay() * 31 + 7).toInt())

            if (day.dayOfMonth == 1) {
                regular += row(day, 9, 15, 4_850_000L, TxnType.INCOME, "Salary", null, bankIds[0])
            }
            if (day.dayOfMonth == 14) {
                regular += row(day, 19, 40, 125_000L, TxnType.INCOME, "UPI from Aditi", null, bankIds[1 % bankIds.size])
            }

            val count = 1 + rnd.nextInt(4)
            repeat(count) {
                var pick = rnd.nextInt(MENU_WEIGHT_TOTAL)
                val menu = MENU.first { pick -= it.weight; pick < 0 }
                regular += row(
                    day = day,
                    hour = 8 + rnd.nextInt(13),
                    minute = rnd.nextInt(60),
                    amountPaise = rnd.nextLong(menu.minPaise, menu.maxPaise + 1),
                    type = TxnType.EXPENSE,
                    merchant = menu.merchant,
                    categoryKey = menu.category,
                    accountId = bankIds[regular.size % bankIds.size],
                )
            }
        }

        // transfer pair: HDFC → SBI ₹5,000, four minutes apart (excluded from spend)
        val tDay = today.minusDays(10)
        regular += row(tDay, 12, 3, 500_000L, TxnType.TRANSFER, "Self transfer", null, bankIds[0], transferGroupId = "tg-demo-1")
        regular += row(tDay, 12, 7, 500_000L, TxnType.TRANSFER, "Self transfer", null, bankIds[1 % bankIds.size], transferGroupId = "tg-demo-1")

        // the two most recent Swiggy rows become review-inbox items (suggestions will fire)
        val reviewRows = regular.filter { it.merchantName == "Swiggy" }.sortedByDescending { it.timestamp }.take(2)
        regular.removeAll(reviewRows.toSet())
        regular += reviewRows.map { it.copy(provenance = Provenance.AUTO_REVIEW, categoryKey = null) }

        txnDao.insertAll(regular)

        // refund: last Zomato expense gets its money back the next day
        val zomato = txnDao.observeAll().first()
            .filter { it.merchantName == "Zomato" && it.type == TxnType.EXPENSE }
            .maxByOrNull { it.timestamp }
        if (zomato != null) {
            txnDao.insert(
                row(
                    day = today.minusDays(1), hour = 10, minute = 5,
                    amountPaise = zomato.amountPaise, type = TxnType.REFUND,
                    merchant = zomato.merchantName, categoryKey = null,
                    accountId = zomato.accountId, refundOf = zomato.id,
                ),
            )
        }

        // sample budgets so the gauge + category cards render (only if none exist)
        if (db.catalogDao().observeBudgets().first().isEmpty()) {
            db.catalogDao().upsertBudget(BudgetEntity(categoryKey = null, amountPaise = 2_000_000L, period = BudgetPeriod.MONTH, rollover = false))
            db.catalogDao().upsertBudget(BudgetEntity(categoryKey = "food", amountPaise = 500_000L, period = BudgetPeriod.MONTH, rollover = false))
            db.catalogDao().upsertBudget(BudgetEntity(categoryKey = "transport", amountPaise = 200_000L, period = BudgetPeriod.MONTH, rollover = false))
        }
    }
}
