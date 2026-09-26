package dev.yashas.expensetracker.data.repo

import dev.yashas.expensetracker.data.db.dao.TransactionDao
import java.time.LocalDate

/**
 * Deterministic analytics math (04-TECH §7: aggregates via DAO, insights as period
 * comparisons — no ML). Pure functions here are unit-tested; the ViewModel only ferries state.
 */
object AnalyticsMath {

    enum class Period(val months: Long, val label: String) {
        M1(1, "1M"), M3(3, "3M"), M6(6, "6M"), Y1(12, "1Y"),
    }

    data class MonthBar(val yearMonth: String, val incomePaise: Long, val expensePaise: Long)

    data class Insight(val headline: String, val detail: String)

    /** Window [from, to) as epochDays ending today. */
    fun windowFor(period: Period, today: LocalDate = LocalDate.now()): Pair<Long, Long> =
        today.minusMonths(period.months).plusDays(1).toEpochDay() to today.plusDays(1).toEpochDay()

    /**
     * Buckets per-type daily rows into calendar months (exact via LocalDate, not /30).
     * Rows outside any month boundary are impossible by construction of the window.
     */
    fun monthBars(rows: List<dev.yashas.expensetracker.data.db.dao.DailyTypeRow>): List<MonthBar> {
        val byMonth = linkedMapOf<String, LongArray>()
        for (row in rows) {
            val ym = LocalDate.ofEpochDay(row.day).let { "%04d-%02d".format(it.year, it.monthValue) }
            val acc = byMonth.getOrPut(ym) { LongArray(2) }
            when (row.type) {
                dev.yashas.expensetracker.domain.model.TxnType.INCOME -> acc[0] += row.amountPaise
                else -> acc[1] += row.amountPaise
            }
        }
        return byMonth.map { (ym, acc) -> MonthBar(ym, acc[0], acc[1]) }.sortedBy { it.yearMonth }
    }

    /** C4 note: transfers/refunds excluded — this shows true in/out only (02-CHARTS). */
    fun insightCards(
        categoryTotals: List<dev.yashas.expensetracker.data.db.dao.CategoryTotalRow>,
        monthBars: List<MonthBar>,
        txnCount: Int,
        rupees: (Long) -> String,
    ): List<Insight> {
        val insights = mutableListOf<Insight>()
        val total = categoryTotals.sumOf { it.amountPaise }
        categoryTotals.maxByOrNull { it.amountPaise }?.let { top ->
            if (total > 0) {
                val share = top.amountPaise * 100 / total
                insights += Insight(
                    headline = "Largest category: ${top.label} · $share%",
                    detail = "${rupees(top.amountPaise)} of ${rupees(total)} across ${top.txnCount} transactions",
                )
            }
        }
        if (monthBars.size >= 2) {
            val last = monthBars.last()
            val prev = monthBars[monthBars.size - 2]
            val delta = last.expensePaise - prev.expensePaise
            val dir = if (delta > 0) "↑" else if (delta < 0) "↓" else "·"
            insights += Insight(
                headline = "$dir ${rupees(kotlin.math.abs(delta))} vs previous month",
                detail = "${rupees(prev.expensePaise)} → ${rupees(last.expensePaise)}",
            )
            val netLast = last.incomePaise - last.expensePaise
            insights += Insight(
                headline = if (netLast >= 0) "You earned more than you spent" else "You spent more than you earned",
                detail = "Last month net: ${rupees(netLast)}",
            )
        }
        if (txnCount > 0) {
            insights += Insight(
                headline = "$txnCount transactions in this period",
                detail = "Every one of them stays on this phone",
            )
        }
        return insights
    }
}

/** Analytics data source bridging DAO rows to UI models. */
class AnalyticsRepository(private val dao: TransactionDao) {
    suspend fun load(period: AnalyticsMath.Period): AnalyticsData {
        val (from, to) = AnalyticsMath.windowFor(period)
        val daily = dao.dailySeriesByType(from, to)
        return AnalyticsData(
            period = period,
            monthBars = AnalyticsMath.monthBars(daily),
            dailyExpense = dao.dailySeries(from, to),
            categoryTotals = dao.categoryBreakdown(from, to),
            topMerchants = dao.topMerchants(from, to, 5),
            txnCount = dao.countInRange(from, to),
        )
    }
}

data class AnalyticsData(
    val period: AnalyticsMath.Period,
    val monthBars: List<AnalyticsMath.MonthBar>,
    val dailyExpense: List<dev.yashas.expensetracker.data.db.dao.DailyTotalRow>,
    val categoryTotals: List<dev.yashas.expensetracker.data.db.dao.CategoryTotalRow>,
    val topMerchants: List<dev.yashas.expensetracker.data.db.dao.CategoryTotalRow>,
    val txnCount: Int,
)
