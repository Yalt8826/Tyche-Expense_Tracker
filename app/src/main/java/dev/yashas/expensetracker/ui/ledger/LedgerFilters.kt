package dev.yashas.expensetracker.ui.ledger

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Ledger time helpers (UI-AUDIT R2): friendly day headers and the month strip.
 * Pure + tested.
 */
object LedgerFilters {

    const val ALL = "all"
    const val THIS_WEEK = "this_week"
    const val THIS_MONTH = "this_month"
    const val LAST_MONTH = "last_month"
    const val RANGE = "range"

    data class MonthOption(
        val key: String,
        val label: String,
        /** Inclusive epochDay bounds; null for "All". */
        val start: Long?,
        val end: Long?,
    )

    /** Chip presets + "All". Range filtering (custom from/to) is separate state. */
    fun presetOptions(today: LocalDate = LocalDate.now()): List<MonthOption> {
        fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong()) // Monday
        val thisWeek = weekStart(today)
        val thisMonth = today.withDayOfMonth(1)
        val lastMonth = thisMonth.minusMonths(1)
        return listOf(
            MonthOption(ALL, "All", null, null),
            MonthOption(
                THIS_WEEK, "This week",
                thisWeek.toEpochDay(), thisWeek.plusDays(7).toEpochDay(),
            ),
            MonthOption(
                THIS_MONTH, "This month",
                thisMonth.toEpochDay(), thisMonth.plusMonths(1).withDayOfMonth(1).toEpochDay(),
            ),
            MonthOption(
                LAST_MONTH, "Last month",
                lastMonth.toEpochDay(), lastMonth.plusMonths(1).withDayOfMonth(1).toEpochDay(),
            ),
        )
    }

    /** Today / Yesterday / 27 Sep / 27 Sep 2025. */
    fun dayLabel(day: Long, today: LocalDate = LocalDate.now()): String {
        val date = LocalDate.ofEpochDay(day)
        return when (day) {
            today.toEpochDay() -> "Today"
            today.minusDays(1).toEpochDay() -> "Yesterday"
            else -> {
                val month = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                if (date.year == today.year) "${date.dayOfMonth} $month"
                else "${date.dayOfMonth} $month ${date.year}"
            }
        }
    }

    /** "27 Sep" short form for review-group item lines. */
    fun shortDate(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
    }

    /** "12 Sep – 26 Sep" (same year drops the year). */
    fun rangeLabel(from: Long, to: Long, today: LocalDate = LocalDate.now()): String {
        val f = LocalDate.ofEpochDay(from)
        val t = LocalDate.ofEpochDay(to.minus(1)) // end is exclusive
        val fmt = { d: LocalDate ->
            if (d.year == today.year) "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}"
            else "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.year}"
        }
        return "${fmt(f)} – ${fmt(t)}"
    }
}
