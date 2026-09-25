package dev.yashas.expensetracker.domain.model

/**
 * Money formatting at the UI edge only (04-TECH §3a). All ledger amounts are Long paise;
 * this is the single place they become display strings, with Indian digit grouping
 * (₹1,18,420) per 01-SCREENS cross-cutting rules.
 */
object MoneyFormat {

    fun formatPaise(paise: Long): String {
        val negative = paise < 0
        val abs = if (negative) -paise else paise
        val rupees = abs / 100
        val remainder = (abs % 100).toInt()

        val rupeeStr = rupees.toString()
        val grouped = if (rupeeStr.length <= 3) {
            rupeeStr
        } else {
            // Indian grouping: last 3 digits stay together, the rest group in 2s from the right.
            val head = rupeeStr.dropLast(3)
            val tail = rupeeStr.takeLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
        }

        val amount = if (remainder == 0) "₹$grouped" else "₹$grouped.%02d".format(remainder)
        return if (negative) "-$amount" else amount
    }
}
