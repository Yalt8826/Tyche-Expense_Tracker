package dev.yashas.expensetracker

import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.TxnType
import dev.yashas.expensetracker.ui.ledger.DragTagMath
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
    fun presetOptionsMatchRequestedPeriods() {
        // Fixed "today" so the week/month windows are deterministic (2026-09-23 = Wednesday)
        val today = LocalDate.of(2026, 9, 23)
        val opts = LedgerFilters.presetOptions(today)
        assertEquals(4, opts.size)
        assertEquals(LedgerFilters.ALL, opts[0].key)
        val thisWeek = opts.first { it.key == LedgerFilters.THIS_WEEK }
        // Monday of that week
        assertEquals(LocalDate.of(2026, 9, 21).toEpochDay(), thisWeek.start)
        assertEquals(LocalDate.of(2026, 9, 28).toEpochDay(), thisWeek.end)
        val thisMonth = opts.first { it.key == LedgerFilters.THIS_MONTH }
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), thisMonth.start)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), thisMonth.end)
        val lastMonth = opts.first { it.key == LedgerFilters.LAST_MONTH }
        assertEquals(LocalDate.of(2026, 8, 1).toEpochDay(), lastMonth.start)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), lastMonth.end)
    }

    @Test
    fun rangeLabelFormatsInclusiveEnd() {
        val today = LocalDate.of(2026, 9, 27)
        val from = LocalDate.of(2026, 9, 12).toEpochDay()
        val toEx = LocalDate.of(2026, 9, 27).toEpochDay() // end-exclusive, so label shows 26 Sep
        assertEquals("12 Sep – 26 Sep", LedgerFilters.rangeLabel(from, toEx, today))
    }

    @Test
    fun decimalPolicyHolds() {
        assertEquals("₹70", MoneyFormat.formatPaise(7_000L))
        assertEquals("₹70.00", MoneyFormat.formatPaiseExact(7_000L))
        assertEquals("₹212.43", MoneyFormat.formatPaiseExact(21_243L))
        assertEquals("₹77,481", MoneyFormat.formatRupeesWhole(77_481_20L))
        assertEquals("-₹212.43", MoneyFormat.formatPaiseExact(-21_243L))
    }

    // --- Home proportion bar (new widget) ---

    @Test
    fun homeSlicesSplitConfirmedExpensesByTag() {
        val rows = listOf(
            txn(1, "A", 50_000L, 0).copy(categoryKey = "food", provenance = Provenance.AUTO_CONFIRMED),
            txn(2, "B", 30_000L, 0).copy(categoryKey = "transport", provenance = Provenance.AUTO_CONFIRMED),
            txn(3, "C", 20_000L, 0).copy(categoryKey = null, provenance = Provenance.AUTO_CONFIRMED),
            // excluded: review-pending, transfer, income
            txn(4, "D", 10_000L, 0).copy(categoryKey = "food", provenance = Provenance.AUTO_REVIEW),
            txn(5, "E", 10_000L, 0).copy(type = TxnType.TRANSFER, categoryKey = "food"),
            txn(6, "F", 10_000L, 0).copy(type = TxnType.INCOME, categoryKey = "food"),
        )
        val slices = dev.yashas.expensetracker.data.repo.HomeBreakdown.slices(rows, emptyMap(), emptyMap())
        assertEquals(3, slices.size)
        assertEquals(0.5f, slices[0].fraction, 0.001f)
        assertEquals("food", slices[0].categoryKey)
        assertEquals("Untagged", slices[2].label)
        assertEquals(1f, slices.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun slugForNamespacesUserTags() {
        assertEquals("user_swiggy_orders", dev.yashas.expensetracker.data.rules.CategorySeed.slugFor("Swiggy Orders!"))
        assertEquals("user_cafe", dev.yashas.expensetracker.data.rules.CategorySeed.slugFor("  Cafe  "))
    }

    @Test
    fun displaySlicesAggregateTailIntoGreyOthers() {
        val mk = { key: String, fraction: Float ->
            dev.yashas.expensetracker.data.repo.HomeBreakdown.Slice(key, key, "series_cyan", (fraction * 100_000).toLong(), fraction)
        }
        val slices = listOf(mk("a", 0.4f), mk("b", 0.25f), mk("c", 0.15f), mk("d", 0.1f), mk("e", 0.06f), mk("f", 0.04f))
        val display = dev.yashas.expensetracker.data.repo.HomeBreakdown.toDisplaySlices(slices)
        assertEquals(5, display.size)
        assertEquals(listOf("a", "b", "c", "d", "others"), display.map { it.categoryKey })
        val others = display.last()
        assertEquals("Others", others.label)
        assertEquals("series_grey", others.colorToken)
        assertEquals(0.1f, others.fraction, 0.001f)
        assertEquals(1f, display.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
        // no aggregation needed when within the limit
        assertEquals(3, dev.yashas.expensetracker.data.repo.HomeBreakdown.toDisplaySlices(slices.take(3)).size)
    }

    @Test
    fun chipOrderMirrorsBarIncludingSubcategories() {
        val mk = { key: String, amount: Long ->
            dev.yashas.expensetracker.data.repo.HomeBreakdown.Slice(key, key, "series_cyan", amount, 0f)
        }
        val slices = listOf(
            mk("shopping", 19_235_00L),
            mk("food.groceries", 5_000_00L),
            mk("food.restaurants", 2_000_00L),
            mk("transport", 1_000_00L),
        )
        val order = dev.yashas.expensetracker.data.repo.HomeBreakdown.chipOrder(
            slices = slices,
            topKeys = listOf("food", "transport", "shopping", "health"),
        )
        // food (7,00,000 incl. subcategories) beats transport; zero-spend health sinks alphabetically last
        assertEquals(listOf("shopping", "food", "transport", "health"), order)
    }

    @Test
    fun ringOffsetsSurroundTheAnchorEvenly() {
        val r = DragTagMath.ringRadius(circleRadius = 75f, bubbleRadius = 40f)
        val ring = DragTagMath.ringOffsets(count = 5, ringRadius = r)
        assertEquals(5, ring.size)
        // every bubble sits clear of the dragged circle's edge
        ring.forEach { o ->
            assertEquals("ring radius", r, o.getDistance(), 0.5f)
            org.junit.Assert.assertTrue("bubble overlaps circle", o.getDistance() > 75f + 40f)
        }
        // first bubble is straight above the anchor, and they are evenly spread
        assertEquals(0f, ring.first().x, 0.5f)
        org.junit.Assert.assertTrue(ring.first().y < 0f)
        val angles = ring.map { Math.toDegrees(kotlin.math.atan2(it.y, it.x).toDouble()) }
        assertEquals(5, angles.distinct().size)
    }

    @Test
    fun ringAnchorIsNudgedSoEveryBubbleFitsOnScreen() {
        val screen = androidx.compose.ui.unit.IntSize(1080, 2400)
        val r = DragTagMath.ringRadius(circleRadius = 75f, bubbleRadius = 40f)
        val margin = r + 40f + 28f
        // grabbed near the top-left corner → pushed in far enough for the whole ring
        val a = DragTagMath.clampRingAnchor(
            anchor = androidx.compose.ui.geometry.Offset(10f, 20f),
            screen = screen,
            ringRadius = r,
            bubbleRadius = 40f,
            labelPad = 28f,
        )
        assertEquals(margin, a.x, 0.01f)
        assertEquals(margin, a.y, 0.01f)
        DragTagMath.ringOffsets(5, r).forEach { o ->
            val c = a + o
            org.junit.Assert.assertTrue("bubble off-screen: $c", c.x >= 0f && c.y >= 0f)
            org.junit.Assert.assertTrue("bubble off-screen: $c", c.x <= screen.width && c.y <= screen.height)
        }
        // a comfortable grab is left exactly where the finger was
        val mid = androidx.compose.ui.geometry.Offset(540f, 1200f)
        assertEquals(mid, DragTagMath.clampRingAnchor(mid, screen, r, 40f, 28f))
    }

    @Test
    fun clampCircleCenterKeepsTheCircleOnScreen() {
        val screen = androidx.compose.ui.unit.IntSize(1080, 2400)
        val c = DragTagMath.clampCircleCenter(
            center = androidx.compose.ui.geometry.Offset(-500f, -800f),
            screen = screen,
            circleRadius = 75f,
        )
        assertEquals(75f, c.x, 0.01f)
        assertEquals(75f, c.y, 0.01f)
        val far = DragTagMath.clampCircleCenter(
            center = androidx.compose.ui.geometry.Offset(5000f, 5000f),
            screen = screen,
            circleRadius = 75f,
        )
        assertEquals(1080f - 75f, far.x, 0.01f)
        assertEquals(2400f - 75f, far.y, 0.01f)
        val inside = androidx.compose.ui.geometry.Offset(540f, 1200f)
        assertEquals(inside, DragTagMath.clampCircleCenter(inside, screen, 75f))
    }

    @Test
    fun dragTargetsPickNearestWithinForgivingRadius() {
        val centers = listOf(
            androidx.compose.ui.geometry.Offset(-100f, -80f),
            androidx.compose.ui.geometry.Offset(100f, -80f),
            androidx.compose.ui.geometry.Offset(0f, -160f),
        )
        assertEquals(-1, DragTagMath.pickTarget(androidx.compose.ui.geometry.Offset(0f, 0f), centers, 40f))
        assertEquals(2, DragTagMath.pickTarget(androidx.compose.ui.geometry.Offset(0f, -150f), centers, 40f))
        assertEquals(0, DragTagMath.pickTarget(androidx.compose.ui.geometry.Offset(-130f, -90f), centers, 40f))
        // too far from everything → no target
        assertEquals(-1, DragTagMath.pickTarget(androidx.compose.ui.geometry.Offset(400f, 400f), centers, 40f))
    }
}
