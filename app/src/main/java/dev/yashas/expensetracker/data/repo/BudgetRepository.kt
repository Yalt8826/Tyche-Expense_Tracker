package dev.yashas.expensetracker.data.repo

import dev.yashas.expensetracker.data.db.dao.TransactionDao
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first

/**
 * Budget math (01-SCREENS S15): dual-progress readout — spending progress vs calendar
 * progress — plus plain verdicts and end-of-month projection. Pure + tested.
 */
object BudgetMath {

    data class CategoryStatus(
        val categoryKey: String,
        val spentPaise: Long,
        val limitPaise: Long,
        /** 0..1+ fraction of budget used. */
        val fraction: Float,
        /** HEALTHY / APPROACHING / EXCEEDED — always paired with icon+text in UI (S15). */
        val state: State,
    )

    enum class State { HEALTHY, APPROACHING, EXCEEDED }

    fun stateFor(fraction: Float): State = when {
        fraction >= 1f -> State.EXCEEDED
        fraction >= 0.8f -> State.APPROACHING
        else -> State.HEALTHY
    }

    fun categoryStatuses(
        limitByCategory: Map<String, Long>,
        spentByCategory: Map<String, Long>,
    ): List<CategoryStatus> =
        limitByCategory.map { (key, limit) ->
            val spent = spentByCategory[key] ?: 0L
            val fraction = if (limit <= 0) 1f else spent.toFloat() / limit
            CategoryStatus(key, spent, limit, fraction, stateFor(fraction))
        }.sortedByDescending { it.fraction }

    /**
     * Overall pace: spending fraction vs month-calendar fraction.
     * Verdicts are plain-language per S15 (never jargon, never color-only).
     */
    data class Overall(
        val spentPaise: Long,
        val limitPaise: Long?,
        val spendFraction: Float,
        val monthFraction: Float,
        val projectedEndPaise: Long,
        val verdict: String,
        val state: State,
    )

    fun overall(
        spentPaise: Long,
        limitPaise: Long?,
        today: LocalDate = LocalDate.now(),
    ): Overall {
        val ym = YearMonth.from(today)
        val dayOfMonth = today.dayOfMonth
        val daysInMonth = ym.lengthOfMonth()
        val monthFraction = dayOfMonth.toFloat() / daysInMonth
        val spendFraction = if (limitPaise == null || limitPaise <= 0) 0f else spentPaise.toFloat() / limitPaise
        val projected = if (dayOfMonth > 0) spentPaise * daysInMonth / dayOfMonth else spentPaise

        val state = if (limitPaise == null) State.HEALTHY else stateFor(spendFraction)
        val verdict = when {
            limitPaise == null -> "No overall budget set yet — add one to see your pace"
            state == State.EXCEEDED -> "You're over your monthly budget"
            spendFraction > monthFraction + 0.05f -> "You're slightly ahead of your usual spending pace"
            spendFraction < monthFraction - 0.05f -> "You're spending slower than your pace — nice"
            else -> "Right on your usual pace"
        }
        return Overall(spentPaise, limitPaise, spendFraction, monthFraction, projected, verdict, state)
    }
}

/** Budget data source: per-category spend vs limits over the current month. */
class BudgetRepository(
    private val dao: TransactionDao,
    private val catalog: dev.yashas.expensetracker.data.db.dao.CatalogDao,
) {

    suspend fun load(today: LocalDate = LocalDate.now()): BudgetData {
        val ym = YearMonth.from(today)
        val from = ym.atDay(1).toEpochDay()
        val to = ym.plusMonths(1).atDay(1).toEpochDay()

        val spentByCategoryRaw = dao.categoryBreakdown(from, to).associate { it.label to it.amountPaise }
        // roll subcategory spend up into parents (a "food" budget must see food.restaurants)
        val cats = catalog.observeCategories().first()
        val keyById = cats.associate { it.id to it.key }
        val spentByCategory = spentByCategoryRaw.toMutableMap()
        for ((key, amount) in spentByCategoryRaw) {
            var current = cats.firstOrNull { it.key == key }
            while (current?.parentId != null) {
                val parentKey = keyById[current.parentId]
                if (parentKey != null) spentByCategory.merge(parentKey, amount, Long::plus)
                current = cats.firstOrNull { it.id == current.parentId }
            }
        }
        // overall = gross spend regardless of category roll-up (no double counting)
        val spentTotal = dao.grossExpensePaise(from, to)
        val overallLimit = dao.overallBudgetPaise()?.takeIf { it > 0 }

        return BudgetData(
            overall = BudgetMath.overall(spentTotal, overallLimit, today),
            categories = BudgetMath.categoryStatuses(
                dao.categoryLimits().associate { it.label to it.amountPaise },
                spentByCategory,
            ),
            spentByCategory = spentByCategory,
            dailySpend = dao.dailySeries(from, to).map { it.day to it.amountPaise },
            daysInMonth = ym.lengthOfMonth(),
        )
    }

    /** Create-or-replace a budget for one category (null = overall). Rupee-free zone: paise in. */
    suspend fun saveBudget(categoryKey: String?, amountPaise: Long) {
        val existing = catalog.observeBudgets().first().firstOrNull { it.categoryKey == categoryKey }
        catalog.upsertBudget(
            dev.yashas.expensetracker.data.db.entity.BudgetEntity(
                id = existing?.id ?: 0,
                categoryKey = categoryKey,
                amountPaise = amountPaise,
                period = dev.yashas.expensetracker.domain.model.BudgetPeriod.MONTH,
                rollover = existing?.rollover ?: false,
            ),
        )
    }

    /** Remove a category budget by key (overall budgets have categoryKey == null). */
    suspend fun deleteBudget(categoryKey: String?) {
        catalog.observeBudgets().first().firstOrNull { it.categoryKey == categoryKey }?.let {
            catalog.deleteBudget(it.id)
        }
    }

    /** Every category without a budget this month — the "add" list in the editor. */
    suspend fun unbudgetedCategories(): List<dev.yashas.expensetracker.data.db.entity.CategoryEntity> {
        val budgeted = catalog.observeBudgets().first().mapNotNull { it.categoryKey }.toSet()
        return catalog.observeCategories().first().filter { it.key !in budgeted }
    }
}

data class BudgetData(
    val overall: BudgetMath.Overall,
    val categories: List<BudgetMath.CategoryStatus>,
    val spentByCategory: Map<String, Long>,
    /** (epochDay, paise) for each day of the current month that had spend. */
    val dailySpend: List<Pair<Long, Long>>,
    val daysInMonth: Int,
)
