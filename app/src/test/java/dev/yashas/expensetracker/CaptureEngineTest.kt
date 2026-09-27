package dev.yashas.expensetracker

import dev.yashas.expensetracker.data.capture.BankTemplates
import dev.yashas.expensetracker.data.capture.CaptureEngine
import dev.yashas.expensetracker.data.capture.SmsText
import dev.yashas.expensetracker.domain.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Parser fixture corpus (04-TECH §8 "Parser: fixture corpus per bank template,
 * golden in/out"). All fixtures are REDACTED synthetic formats — no real SMS data.
 */
class CaptureEngineTest {

    private val engine = CaptureEngine()

    // --- golden in/out per bank ---

    @Test
    fun hdfcDebitFixtureParsesFully() {
        val body = "Rs.200.00 debited from a/c XX1234 on 26-09-26 for Swiggy. Ref 123456789012. Not you? Call 18001234"
        val r = engine.process(body)
        assertEquals(CaptureEngine.Reason.PARSED, r.reason)
        val p = r.parsed!!
        assertEquals(20_000L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertTrue(p.accountMask!!.contains("1234"))
        assertEquals("123456789012", p.utrRef)
        assertEquals("Swiggy", p.merchant)
        assertEquals(LocalDate.of(2026, 9, 26).toEpochDay(), p.valueDateEpochDay)
    }

    @Test
    fun kotakSentFixtureParsesFully() {
        val body = "Sent Rs.2,000.00 from Kotak Bank A/c X9624 to rajrishank0@okaxis on 26-09-26. UPI Ref 663561033525. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
        val p = engine.process(body).parsed!!
        assertEquals(200_000L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("rajrishank0@okaxis", p.vpa)
        assertEquals("663561033525", p.utrRef)
        assertEquals(LocalDate.of(2026, 9, 26).toEpochDay(), p.valueDateEpochDay)
    }

    @Test
    fun sbiCreditFixtureParsesWithPaise() {
        val body = "Rs 2,500.75 credited on 28-09-26 in A/c XX9876 towards UPI from john@upi. Ref 887654321012"
        val p = engine.process(body).parsed!!
        assertEquals(250_075L, p.amountPaise)
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals("john@upi", p.vpa)
        assertEquals("887654321012", p.utrRef)
    }

    @Test
    fun iciciCardFixtureParsesSpentKeyword() {
        val body = "Rs.1500.00 spent on ICICI Credit Card XX4321 at ZOMATO on 26-09-26. Txn ID 123456789012"
        val p = engine.process(body).parsed!!
        assertEquals(150_000L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("ZOMATO", p.merchant)
        assertEquals("123456789012", p.utrRef)
    }

    @Test
    fun axisUpiFixtureParses() {
        val body = "Rs.75.00 debited from A/c XX5566 on 26-09-26 (UPI/QR purchase). Ref 112233445566"
        val p = engine.process(body).parsed!!
        assertEquals(7_500L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals("112233445566", p.utrRef)
    }

    @Test
    fun everySeededFixtureParses() {
        BankTemplates.SEEDS.flatMap { it.fixtures }.forEach { body ->
            val r = engine.process(body)
            assertEquals("fixture failed: $body", CaptureEngine.Reason.PARSED, r.reason)
            assertNotNull(r.parsed)
            assertTrue(r.parsed!!.amountPaise > 0)
        }
    }

    // --- funnel rejection reasons ---

    @Test
    fun noAmountIsRejected() {
        assertEquals(CaptureEngine.Reason.NO_AMOUNT, engine.process("Your OTP is 4821. Do not share.").reason)
    }

    @Test
    fun noDirectionKeywordIsRejected() {
        assertEquals(
            CaptureEngine.Reason.NO_DIRECTION_KEYWORD,
            engine.process("Your balance is Rs.500.00. Have a nice day.").reason,
        )
    }

    @Test
    fun ambiguousDirectionIsRejected() {
        assertEquals(
            CaptureEngine.Reason.AMBIGUOUS_DIRECTION,
            engine.process("Rs.100.00 debited and credited back to a/c XX1234. Ref 9999").reason,
        )
    }

    // --- signature (template induction key) ---

    @Test
    fun signatureIsStableWithinTemplateFamily() {
        val a = "Rs.200.00 debited from a/c XX1234 on 26-09-26 for Swiggy. Ref 123456789012."
        // same letter content, same digit-run lengths (values differ) → same family
        val b = "Rs.350.00 debited from a/c XX5678 on 27-09-26 for Swiggy. Ref 223456789012."
        assertEquals(SmsText.signature(a), SmsText.signature(b))
        val c = "Rs.2000.00 debited from a/c XX1234 on 26-09-26 for Swiggy. Ref 123456789012."
        assertTrue(SmsText.signature(a) != SmsText.signature(c))
    }

    @Test
    fun digestIsWhitespaceInsensitiveAndDiscriminating() {
        val a = "Rs.200.00  debited   from a/c XX1234"
        val b = "Rs.200.00 debited from a/c XX1234"
        assertEquals(SmsText.digest(a), SmsText.digest(b))
        assertTrue(SmsText.digest(a) != SmsText.digest("Rs.201.00 debited from a/c XX1234"))
    }

    // --- paise conversion edges ---

    @Test
    fun rupeesToPaiseHandlesGroupingAndDecimals() {
        assertEquals(125_050L, SmsText.rupeesToPaise("1,250.50"))
        assertEquals(20_000L, SmsText.rupeesToPaise("200"))
        assertEquals(99L, SmsText.rupeesToPaise("0.99"))
        assertEquals(12_345_678_900L, SmsText.rupeesToPaise("12,34,56,789"))
        assertEquals(12_340L, SmsText.rupeesToPaise("123.4")) // .4 → .40
    }

    // --- extractor edges ---

    @Test
    fun utrExtractionIsCaseNormalizedOrNull() {
        assertEquals("ABC123XYZ", SmsText.extractUtr("utr: abc123xyz"))
        assertNull(SmsText.extractUtr("no reference here"))
    }

    @Test
    fun balanceExtractionFindsBalances() {
        assertEquals(15_234_00L, SmsText.extractBalancePaise("Bal: Rs.15,234.00"))
        assertNull(SmsText.extractBalancePaise("no balance mentioned"))
    }
}
