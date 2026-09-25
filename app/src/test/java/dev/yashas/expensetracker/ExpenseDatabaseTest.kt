package dev.yashas.expensetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.yashas.expensetracker.data.db.ExpenseDatabase
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room-layer tests (04-TECH §8 "Repos/DAO"): dedup unique index behavior and
 * SQL aggregates (transfer exclusion, refund netting) run against a real
 * in-memory SQLite via Robolectric.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ExpenseDatabaseTest {

    private lateinit var db: ExpenseDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ExpenseDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun txn(
        accountId: Long = 1,
        type: TxnType = TxnType.EXPENSE,
        amountPaise: Long = 10_000L,
        timestamp: Long = 0,
        utrRef: String? = null,
        categoryKey: String? = null,
        refundOfTxnId: Long? = null,
    ) = TransactionEntity(
        timestamp = timestamp, valueDate = null, amountPaise = amountPaise, type = type,
        accountId = accountId, merchantName = null, vpa = null, categoryKey = categoryKey,
        note = null, provenance = Provenance.MANUAL, sourceSmsId = null,
        refundOfTxnId = refundOfTxnId, transferGroupId = null, utrRef = utrRef,
        ruleAppliedKey = null,
    )

    @Test
    fun sameAccountUtrInsertIsRejected() = runTest {
        val dao = db.transactionDao()
        dao.insert(txn(utrRef = "UTR-1"))
        dao.insert(txn(accountId = 2, utrRef = "UTR-1")) // different account: fine
        val second = runCatching { dao.insert(txn(utrRef = "UTR-1")) }
        assertTrue("expected unique-index violation", second.isFailure)
        assertEquals(1, dao.countByUtr(1, "UTR-1"))
    }

    @Test
    fun nullUtrRowsNeverCollide() = runTest {
        val dao = db.transactionDao()
        dao.insert(txn(utrRef = null))
        dao.insert(txn(utrRef = null))
        // SQLite NULLs are distinct in unique indexes: manual/no-UTR rows always insert.
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test
    fun sqlAggregatesExcludeTransfersAndNetRefunds() = runTest {
        val dao = db.transactionDao()
        val expenseId = dao.insert(txn(amountPaise = 50_000L, categoryKey = "food", utrRef = "E1"))
        dao.insert(txn(amountPaise = 20_000L, categoryKey = "transport", utrRef = "E2"))
        dao.insert(
            txn(amountPaise = 30_000L, utrRef = "R1", refundOfTxnId = expenseId, type = TxnType.REFUND),
        )
        val debit = dao.insert(txn(amountPaise = 70_000L, utrRef = "T1"))
        val credit = dao.insert(
            txn(accountId = 2, amountPaise = 70_000L, utrRef = "T2", type = TxnType.INCOME),
        )
        dao.markTransfer(debit, "tg1")
        dao.markTransfer(credit, "tg1")

        assertEquals(70_000L, dao.grossExpensePaise(null, null)) // transfers excluded in SQL
        assertEquals(30_000L, dao.refundNetPaise(null, null, null)) // refund of the food row
        val breakdown = dao.categoryBreakdown(null, null)
        assertEquals(2, breakdown.size)
    }
}
