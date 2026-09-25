package dev.yashas.expensetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.yashas.expensetracker.data.capture.CaptureEngine
import dev.yashas.expensetracker.data.capture.SmsIngestor
import dev.yashas.expensetracker.data.capture.SmsText
import dev.yashas.expensetracker.data.db.ExpenseDatabase
import dev.yashas.expensetracker.data.db.entity.AccountEntity
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Funnel persistence tests (04-TECH §8): allowlist gating, raw/txn dedup outcomes,
 * template induction over the in-memory Room DB.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SmsIngestorTest {

    private lateinit var db: ExpenseDatabase
    private lateinit var ingestor: SmsIngestor

    private val fixtureA = "Rs.200.00 debited from a/c XX1234 on 26-09-26 for Swiggy. Ref 123456789012. Not you? Call 18001234"
    // same letter content as fixtureA, same digit-run lengths → same template family
    private val fixtureB = "Rs.250.00 debited from a/c XX9999 on 27-09-26 for Swiggy. Ref 223456789012. Not you? Call 18001234"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ingestor = SmsIngestor(db)
        runTest {
            db.catalogDao().upsertAccount(
                AccountEntity(bankName = "HDFC", last4Mask = "1234", smsSenderAllowlist = "AD-HDFCBK,VM-HDFCBK"),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun allowlistedSmsIsCapturedForReview() = runTest {
        val outcome = ingestor.ingest("AD-HDFCBK", fixtureA, 1_000_000L)
        assertEquals(SmsIngestor.Outcome.CAPTURED, outcome)

        val txns = db.transactionDao().observeAll().first()
        assertEquals(1, txns.size)
        assertEquals(TxnType.EXPENSE, txns[0].type)
        assertEquals(20_000L, txns[0].amountPaise)
        assertEquals(Provenance.AUTO_REVIEW, txns[0].provenance)
        assertEquals("Swiggy", txns[0].merchantName)
        assertNotNull(txns[0].sourceSmsId)

        // raw row exists and is marked parsed
        val raw = db.catalogDao().smsById(txns[0].sourceSmsId!!)
        assertNotNull(raw)
        assertEquals(dev.yashas.expensetracker.domain.model.SmsParsedState.PARSED, raw!!.parsedState)
    }

    @Test
    fun nonAllowlistedSenderIsIgnoredWithoutPersisting() = runTest {
        val outcome = ingestor.ingest("XX-JUNKMSG", fixtureA, 1_000_000L)
        assertEquals(SmsIngestor.Outcome.IGNORED, outcome)
        assertTrue(db.transactionDao().observeAll().first().isEmpty())
    }

    @Test
    fun identicalRedeliveryIsDuplicateSms() = runTest {
        assertEquals(SmsIngestor.Outcome.CAPTURED, ingestor.ingest("AD-HDFCBK", fixtureA, 1_000_000L))
        assertEquals(SmsIngestor.Outcome.DUPLICATE_SMS, ingestor.ingest("AD-HDFCBK", fixtureA, 1_000_050L))
        assertEquals(1, db.transactionDao().observeAll().first().size)
    }

    @Test
    fun sameUtrDifferentBodyIsDuplicateTxn() = runTest {
        assertEquals(SmsIngestor.Outcome.CAPTURED, ingestor.ingest("AD-HDFCBK", fixtureA, 1_000_000L))
        // different body (new digest) but same (account, UTR) → ledger dedup
        val replay = "Rs.200.00 debited from a/c XX1234 on 26-09-26 for Swiggy. Ref 123456789012"
        assertEquals(SmsIngestor.Outcome.DUPLICATE_TXN, ingestor.ingest("AD-HDFCBK", replay, 1_000_100L))
        assertEquals(1, db.transactionDao().observeAll().first().size)
    }

    @Test
    fun learnedTemplateAppliesToFamilySibling() = runTest {
        ingestor.ingest("AD-HDFCBK", fixtureA, 1_000_000L)
        val parsed = CaptureEngine().process(fixtureA).parsed!!
        ingestor.learnTemplate("HDFC", fixtureA, parsed)

        val template = db.catalogDao().templateBySignature(SmsText.signature(fixtureA))
        assertNotNull(template)

        // same digit-mask family → sibling SMS carries the template key
        assertEquals(SmsText.signature(fixtureA), SmsText.signature(fixtureB))
        val outcome = ingestor.ingest("AD-HDFCBK", fixtureB, 2_000_000L)
        assertEquals(SmsIngestor.Outcome.CAPTURED, outcome)
        val txns = db.transactionDao().observeAll().first()
        assertEquals(2, txns.size)
        val sibling = txns.first { it.utrRef == "223456789012" }
        assertTrue(sibling.ruleAppliedKey!!.startsWith("template:"))
    }

    @Test
    fun unknownSenderWithoutAccountStillCapturesUnderSeededAllowlist() = runTest {
        // seed another bank row (as AppGraph.seedBanksIfNeeded would)
        db.catalogDao().upsertAccount(
            AccountEntity(bankName = "AXIS", last4Mask = "----", smsSenderAllowlist = "AD-AXISBK,AXISBK"),
        )
        val axisSms = "Rs.75.00 debited from A/c XX5566 on 26-09-26 (UPI/QR purchase). Ref 112233445566"
        assertEquals(SmsIngestor.Outcome.CAPTURED, ingestor.ingest("AD-AXISBK", axisSms, 3_000_000L))
        val txns = db.transactionDao().observeAll().first()
        assertEquals(1, txns.size)
        // no UTR-free collisions; account auto-created is the seeded AXIS row via sender match
        assertNull(txns[0].categoryKey)
    }
}
