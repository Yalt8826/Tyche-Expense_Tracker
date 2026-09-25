package dev.yashas.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P0 smoke: the JVM unit-test harness runs, and Long paise math stays exact
 * (ledger rule: amounts are paise integers, never floats — 04-TECH §3a).
 */
class SmokeTest {
    @Test
    fun paiseLongMathIsExact() {
        // ₹1,842.00 + ₹99.99 = ₹1,941.99
        val a = 184_200L
        val b = 9_999L
        assertEquals(194_199L, a + b)
    }
}
