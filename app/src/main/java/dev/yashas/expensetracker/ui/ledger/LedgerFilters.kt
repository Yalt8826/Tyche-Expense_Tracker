package dev.yashas.expensetracker.ui.ledger

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Ledger time helpers (UI-AUDIT R2): friendly day headers and the month strip.
 * Pure + tested.
 */
object LedgerFilters {

    const val ALL = "all"

    data class MonthOption(
        val key: String,
        val label: String,
        /** Inclusive epochDay bounds; null for "All". */
        val start: Long?,
        val end: Long?,
    )

    /** "All" first, then months with data, newest first. */
    fun monthOptions(days: List<Long>): List<MonthOption> {
        val months = days.map { YearMonth.from(LocalDate.ofEpochDay(it)) }.distinct().sortedDescending()
        val options = mutableListOf(MonthOption(ALL, "All", null, null))
        for (ym in months) {
            options += MonthOption(
                key = ym.toString(),
                label = "${ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${ym.year}",
                start = ym.atDay(1).toEpochDay(),
                end = ym.plusMonths(1).atDay(1).toEpochDay(),
            )
        }
        return options
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
}
