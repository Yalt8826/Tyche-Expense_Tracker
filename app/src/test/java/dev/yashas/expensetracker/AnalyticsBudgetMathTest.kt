package dev.yashas.expensetracker

import dev.yashas.expensetracker.data.repo.AnalyticsMath
import dev.yashas.expensetracker.data.repo.BudgetMath
import dev.yashas.expensetracker.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** P5 pure-math tests: month bucketing, insights, budget states, pace verdicts. */
class AnalyticsBudgetMathTest {

    private fun row(day: Long, type: TxnType, paise: Long) =
        dev.yashas.expensetracker.data.db.dao.DailyTypeRow(day, type, paise)

    @Test
    fun monthBarsBucketByCalendarMonth() {
        // Oct 30 + Nov 1 2026 (different months), plus income
        val d1 = LocalDate.of(2026, 10, 30).toEpochDay()
        val d2 = LocalDate.of(2026, 11, 1).toEpochDay()
        val bars = AnalyticsMath.monthBars(
            listOf(row(d1, TxnType.EXPENSE, 100_00L), row(d2, TxnType.EXPENSE, 50_00L), row(d2, TxnType.INCOME, 900_00L)),
        )
        assertEquals(2, bars.size)
        assertEquals("2026-10", bars[0].yearMonth)
        assertEquals(100_00L, bars[0].expensePaise)
        assertEquals(0L, bars[0].incomePaise)
        assertEquals("2026-11", bars[1].yearMonth)
        assertEquals(50_00L, bars[1].expensePaise)
        assertEquals(900_00L, bars[1].incomePaise)
    }

    @Test
    fun insightsRankAndComparePeriods() {
        val cats = listOf(
            dev.yashas.expensetracker.data.db.dao.CategoryTotalRow("food", 48_20_00L, 12),
            dev.yashas.expensetracker.data.db.dao.CategoryTotalRow("transport", 20_00_00L, 5),
        )
        val bars = listOf(
            AnalyticsMath.MonthBar("2026-08", 0L, 100_00_00L),
            AnalyticsMath.MonthBar("2026-09", 0L, 92_00_00L),
        )
        val insights = AnalyticsMath.insightCards(cats, bars, 17) { p -> "₹$p" }
        assertEquals(true, insights.any { it.headline.contains("Largest category: food") })
        assertEquals(true, insights.any { it.headline.contains("↓") && it.headline.contains("previous month") })
    }

    @Test
    fun budgetStatesMatchFractions() {
        assertEquals(BudgetMath.State.HEALTHY, BudgetMath.stateFor(0.1f))
        assertEquals(BudgetMath.State.APPROACHING, BudgetMath.stateFor(0.85f))
        assertEquals(BudgetMath.State.EXCEEDED, BudgetMath.stateFor(1.2f))
    }

    @Test
    fun overallPaceProjectsEndOfMonth() {
        val today = LocalDate.of(2026, 9, 26) // 26/30 days ≈ 0.867 calendar
        val spent = 50_00_00L
        val limit = 60_00_00L
        val o = BudgetMath.overall(spent, limit, today)
        assertEquals(0.833f, o.spendFraction, 0.01f)
        assertEquals(0.867f, o.monthFraction, 0.01f)
        assertEquals(BudgetMath.State.APPROACHING, o.state)
        // projection: 500000 * 30 / 26 = 576923
        assertEquals(576_923L, o.projectedEndPaise)
        assertEquals(true, o.verdict.contains("pace"))
    }

    @Test
    fun overallWithoutBudgetExplainsItself() {
        val o = BudgetMath.overall(10_00L, null, LocalDate.of(2026, 9, 26))
        assertEquals(true, o.verdict.contains("No overall budget"))
    }
}
