package dev.yashas.expensetracker.domain.model

/**
 * Money formatting at the UI edge only (04-TECH §3a). All ledger amounts are Long paise;
 * these are the single set of functions that turn them into display strings with Indian
 * digit grouping (₹1,18,420).
 *
 * Decimal policy (UI-AUDIT R5): transaction rows and review cards always show two
 * decimals (exact); hero figures, gauges and summaries are whole rupees.
 */
object MoneyFormat {

    private fun group(rupees: Long): String {
        val rupeeStr = rupees.toString()
        return if (rupeeStr.length <= 3) {
            rupeeStr
        } else {
            // Indian grouping: last 3 digits stay together, the rest group in 2s from the right.
            val head = rupeeStr.dropLast(3)
            val tail = rupeeStr.takeLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
        }
    }

    private fun signed(negative: Boolean, body: String): String = if (negative) "-$body" else body

    /** Auto: paise shown only when nonzero (₹70 / ₹212.43). */
    fun formatPaise(paise: Long): String {
        val negative = paise < 0
        val abs = if (negative) -paise else paise
        val rupees = abs / 100
        val remainder = (abs % 100).toInt()
        val body = "₹${group(rupees)}" + if (remainder == 0) "" else ".%02d".format(remainder)
        return signed(negative, body)
    }

    /** Always two decimals (₹70.00, ₹212.43) — rows, review cards, budget cards. */
    fun formatPaiseExact(paise: Long): String {
        val negative = paise < 0
        val abs = if (negative) -paise else paise
        val body = "₹${group(abs / 100)}.%02d".format((abs % 100).toInt())
        return signed(negative, body)
    }

    /** Whole rupees, never paise (₹77,481) — hero figures, gauges, projections. */
    fun formatRupeesWhole(paise: Long): String {
        val negative = paise < 0
        val abs = if (negative) -paise else paise
        return signed(negative, "₹${group(abs / 100)}")
    }
}
