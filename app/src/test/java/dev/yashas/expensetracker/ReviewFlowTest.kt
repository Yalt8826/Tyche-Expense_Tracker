package dev.yashas.expensetracker

import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType
import dev.yashas.expensetracker.ui.ledger.LedgerFilters
import dev.yashas.expensetracker.ui.ledger.ReviewGrouper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** UI-AUDIT R1/R2/R5 pure-logic tests: payee grouping, month/day labels, decimal policy. */
class ReviewFlowTest {

    private fun txn(
        id: Long,
        merchant: String?,
        paise: Long,
        day: Long,
    ) = TransactionEntity(
        id = id,
        timestamp = day * 86_400_000L,
        valueDate = day,
        amountPaise = paise,
        type = TxnType.EXPENSE,
        accountId = 1,
        merchantName = merchant,
        vpa = null,
        categoryKey = null,
        note = null,
        provenance = Provenance.AUTO_REVIEW,
        sourceSmsId = null,
        refundOfTxnId = null,
        transferGroupId = null,
        utrRef = null,
        ruleAppliedKey = null,
    )

    @Test
    fun groupsByPayeeCaseInsensitiveWithLatestFirst() {
        val d1 = LocalDate.of(2026, 9, 20).toEpochDay()
        val d2 = LocalDate.of(2026, 9, 25).toEpochDay()
        val groups = ReviewGrouper.group(
            listOf(
                ReviewGrouper.Item(txn(1, "Swiggy", 38_046L, d1), null),
                ReviewGrouper.Item(txn(2, "swiggy", 20_000L, d2), null),
                ReviewGrouper.Item(txn(3, "Ola", 21_243L, d1), null),
            ),
        )
        assertEquals(2, groups.size)
        // newest group first
        assertEquals("swiggy", groups[0].key)
        assertEquals(2, groups[0].items.size)
        assertEquals(58_046L, groups[0].totalPaise)
        // within the group, newest item first
        assertEquals(2L, groups[0].items.first().id)
    }

    @Test
    fun nullMerchantFallsBackToVpaThenUnknown() {
        val groups = ReviewGrouper.group(
            listOf(
                ReviewGrouper.Item(txn(1, null, 100L, 0), null),
                ReviewGrouper.Item(txn(2, null, 200L, 0).copy(vpa = "someone@upi"), null),
            ),
        )
        assertEquals(2, groups.size)
        // "someone@upi" sorts before "unknown"; look up by key, not position
        val keys = groups.map { it.key }.toSet()
        assertEquals(setOf("someone@upi", "unknown"), keys)
        assertEquals("someone@upi", groups.first { it.key == "someone@upi" }.items.first().vpa)
    }

    @Test
    fun dayLabelsAreFriendly() {
        val today = LocalDate.of(2026, 9, 27)
        assertEquals("Today", LedgerFilters.dayLabel(today.toEpochDay(), today))
        assertEquals("Yesterday", LedgerFilters.dayLabel(today.minusDays(1).toEpochDay(), today))
        assertEquals("18 Sep", LedgerFilters.dayLabel(LocalDate.of(2026, 9, 18).toEpochDay(), today))
        assertEquals("18 Sep 2025", LedgerFilters.dayLabel(LocalDate.of(2025, 9, 18).toEpochDay(), today))
    }

    @Test
    fun monthOptionsAreAllThenNewestMonths() {
        val days = listOf(
            LocalDate.of(2026, 9, 1).toEpochDay(),
            LocalDate.of(2026, 9, 20).toEpochDay(),
            LocalDate.of(2026, 8, 15).toEpochDay(),
        )
        val opts = LedgerFilters.monthOptions(days)
        assertEquals(3, opts.size)
        assertEquals(LedgerFilters.ALL, opts[0].key)
        assertEquals("Sep 2026", opts[1].label)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), opts[1].start)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), opts[1].end)
        assertEquals("Aug 2026", opts[2].label)
    }

    @Test
    fun decimalPolicyHolds() {
        assertEquals("₹70", MoneyFormat.formatPaise(7_000L))
        assertEquals("₹70.00", MoneyFormat.formatPaiseExact(7_000L))
        assertEquals("₹212.43", MoneyFormat.formatPaiseExact(21_243L))
        assertEquals("₹77,481", MoneyFormat.formatRupeesWhole(77_481_20L))
        assertEquals("-₹212.43", MoneyFormat.formatPaiseExact(-21_243L))
    }
}
