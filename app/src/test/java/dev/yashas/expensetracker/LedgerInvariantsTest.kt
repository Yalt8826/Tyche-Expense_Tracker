package dev.yashas.expensetracker

import dev.yashas.expensetracker.data.repo.LedgerInvariants
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.domain.model.TxnType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ledger invariants (04-TECH §8): transfer exclusion, refund netting, paise math,
 * duplicate suspicion. Pure JVM — no Android framework.
 */
class LedgerInvariantsTest {

    private fun row(
        id: Long,
        type: TxnType,
        accountId: Long = 1,
        amountPaise: Long,
        timestamp: Long,
        categoryKey: String? = null,
        merchantName: String? = null,
        utrRef: String? = null,
        refundOfTxnId: Long? = null,
        transferGroupId: String? = null,
    ) = LedgerInvariants.Row(
        id = id, type = type, accountId = accountId, amountPaise = amountPaise,
        timestamp = timestamp, categoryKey = categoryKey, merchantName = merchantName,
        utrRef = utrRef, refundOfTxnId = refundOfTxnId, transferGroupId = transferGroupId,
    )

    // --- paise math ---

    @Test
    fun paiseSumsAreExact() {
        val rows = listOf(
            row(1, TxnType.EXPENSE, amountPaise = 123_456L, timestamp = 0),
            row(2, TxnType.EXPENSE, amountPaise = 99_99L, timestamp = 1),
        )
        assertEquals(133_455L, LedgerInvariants.grossExpensePaise(rows))
    }

    // --- transfer exclusion ---

    @Test
    fun transfersAreExcludedFromSpend() {
        val t = 1_000_000L
        val rows = listOf(
            row(1, TxnType.EXPENSE, accountId = 1, amountPaise = 200_000L, timestamp = t, categoryKey = "food"),
            row(2, TxnType.INCOME, accountId = 2, amountPaise = 200_000L, timestamp = t + 60_000),
        )
        val pairs = LedgerInvariants.detectTransferPairs(rows)
        assertEquals(listOf(1L to 2L), pairs)
        // after marking both legs TRANSFER they leave the spend surfaces
        val marked = rows.map {
            if (it.id == 1L || it.id == 2L) it.copy(type = TxnType.TRANSFER, transferGroupId = "tg1") else it
        }
        assertEquals(0L, marked.filter { it.type == TxnType.EXPENSE }.sumOf { it.amountPaise })
    }

    @Test
    fun transferPairingRespectsWindowAndAccount() {
        val t = 2_000_000L
        val window = LedgerInvariants.DEFAULT_TRANSFER_WINDOW_MILLIS
        val rows = listOf(
            // out of account 1, into account 3 — outside window: NOT a pair
            row(1, TxnType.EXPENSE, accountId = 1, amountPaise = 50_000L, timestamp = t),
            row(2, TxnType.INCOME, accountId = 3, amountPaise = 50_000L, timestamp = t + window + 1),
            // same amount, same account: NOT a pair (a real income echo would be a dup, not a transfer)
            row(3, TxnType.EXPENSE, accountId = 1, amountPaise = 70_000L, timestamp = t),
            row(4, TxnType.INCOME, accountId = 1, amountPaise = 70_000L, timestamp = t + 1000),
            // inside window, different account: pair
            row(5, TxnType.EXPENSE, accountId = 1, amountPaise = 60_000L, timestamp = t),
            row(6, TxnType.INCOME, accountId = 2, amountPaise = 60_000L, timestamp = t + 5 * 60_000),
        )
        val pairs = LedgerInvariants.detectTransferPairs(rows)
        assertEquals(listOf(5L to 6L), pairs)
    }

    // --- refund netting ---

    @Test
    fun refundsNetOutOfTheirSourceCategory() {
        val rows = listOf(
            row(1, TxnType.EXPENSE, amountPaise = 50_000L, timestamp = 0, categoryKey = "food", merchantName = "Swiggy"),
            row(2, TxnType.EXPENSE, amountPaise = 20_000L, timestamp = 1, categoryKey = "transport"),
            row(3, TxnType.REFUND, amountPaise = 50_000L, timestamp = 2, refundOfTxnId = 1, merchantName = "Swiggy"),
        )
        assertEquals(20_000L, LedgerInvariants.netExpensePaise(rows))
        assertEquals(mapOf("transport" to 20_000L), LedgerInvariants.netByCategory(rows))
    }

    @Test
    fun unmatchedRefundIsNotNetted() {
        val rows = listOf(
            row(1, TxnType.EXPENSE, amountPaise = 50_000L, timestamp = 0, categoryKey = "food"),
            row(2, TxnType.REFUND, amountPaise = 30_000L, timestamp = 2, refundOfTxnId = null),
        )
        // unlinked refund: stays out of netting (it goes to Review instead of auto-income, 00 §4)
        assertEquals(50_000L, LedgerInvariants.netExpensePaise(rows))
        assertEquals(mapOf("food" to 50_000L), LedgerInvariants.netByCategory(rows))
    }

    @Test
    fun partialRefundNetsPartially() {
        val rows = listOf(
            row(1, TxnType.EXPENSE, amountPaise = 50_000L, timestamp = 0, categoryKey = "food"),
            row(2, TxnType.REFUND, amountPaise = 12_340L, timestamp = 2, refundOfTxnId = 1),
        )
        assertEquals(37_660L, LedgerInvariants.netExpensePaise(rows))
        assertEquals(mapOf("food" to 37_660L), LedgerInvariants.netByCategory(rows))
    }

    // --- duplicates ---

    @Test
    fun duplicateSuspectsRequireAmountCounterpartyAndDifferingUtr() {
        val t = 3_000_000L
        val rows = listOf(
            row(1, TxnType.EXPENSE, amountPaise = 12_400L, timestamp = t, merchantName = "Swiggy", utrRef = "U1"),
            row(2, TxnType.EXPENSE, amountPaise = 12_400L, timestamp = t + 120_000, merchantName = "Swiggy", utrRef = "U2"),
            row(3, TxnType.EXPENSE, amountPaise = 12_400L, timestamp = t + 240_000, merchantName = "Zomato", utrRef = "U3"),
        )
        assertEquals(listOf(1L to 2L), LedgerInvariants.duplicateSuspects(rows))
    }

    // --- Indian money formatting ---

    @Test
    fun indianGroupingFormatsCorrectly() {
        assertEquals("₹0", MoneyFormat.formatPaise(0L))
        assertEquals("₹18,420", MoneyFormat.formatPaise(18_420_00L))
        assertEquals("₹1,18,420", MoneyFormat.formatPaise(1_18_420_00L))
        assertEquals("₹12,34,56,789", MoneyFormat.formatPaise(12_34_56_789_00L))
        assertEquals("₹999", MoneyFormat.formatPaise(99_900L))
        assertEquals("₹1,234.50", MoneyFormat.formatPaise(1_234_50L))
        assertEquals("-₹250.75", MoneyFormat.formatPaise(-25_075L))
    }
}
