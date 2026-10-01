package dev.yashas.expensetracker.data.capture

import dev.yashas.expensetracker.domain.model.Direction
import java.security.MessageDigest

/**
 * Result of parsing one bank SMS (04-TECH §4 funnel output).
 * valueDateEpochDay is set when the body contains a date; delivery timestamp fallback
 * happens at persist time.
 */
data class ParsedSms(
    val amountPaise: Long,
    val direction: Direction,
    val accountMask: String?,
    val utrRef: String?,
    val balancePaise: Long?,
    val merchant: String?,
    val vpa: String?,
    val valueDateEpochDay: Long?,
    val matchedSignature: String?,
    /** true when a seeded/learned template matched; false = generic fallback. */
    val fromTemplate: Boolean,
)

object SmsText {

    /**
     * Digit-masked signature: every digit run becomes `#` × run length. Stable across
     * amounts/dates/refs of equal length — the family key for template induction.
     */
    fun signature(body: String): String =
        buildString {
            var run = 0
            for (c in body) {
                if (c.isDigit()) {
                    run++
                } else {
                    repeat(run) { append('#') }
                    run = 0
                    append(c)
                }
            }
            repeat(run) { append('#') }
        }

    /** SHA-256 of the normalized body (whitespace-collapsed, trimmed) — raw-SMS dedup. */
    fun digest(body: String): String {
        val normalized = body.trim().replace(Regex("\\s+"), " ")
        val bytes = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val AMOUNT_REGEX = Regex("""(?:rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    /** ₹/Rs amount → paise. Returns null when no amount anchor exists (cheap screen fails). */
    fun extractAmountPaise(body: String): Long? {
        val m = AMOUNT_REGEX.find(body) ?: return null
        return rupeesToPaise(m.groupValues[1])
    }

    fun rupeesToPaise(raw: String): Long {
        val clean = raw.replace(",", "")
        val dot = clean.indexOf('.')
        return if (dot < 0) {
            clean.toLong() * 100
        } else {
            val rupees = clean.take(dot).ifEmpty { "0" }.toLong()
            val frac = clean.drop(dot + 1).padEnd(2, '0').take(2)
            rupees * 100 + frac.toLong()
        }
    }

    private val DATE_REGEXES = listOf(
        Regex("""\b([0-3]?\d)[-/]([01]?\d)[-/](\d{2}|\d{4})\b"""),
    )

    /** dd-mm-yy / dd/mm/yyyy (Indian convention) → epochDay, or null. */
    fun extractValueDateEpochDay(body: String): Long? {
        for (regex in DATE_REGEXES) {
            val m = regex.find(body) ?: continue
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            var year = m.groupValues[3].toInt()
            if (year < 100) year += 2000
            if (day !in 1..31 || month !in 1..12) continue
            return java.time.LocalDate.of(year, month, day).toEpochDay()
        }
        return null
    }

    fun hasDirectionKeyword(body: String): Boolean =
        DIRECTION_REGEX.containsMatchIn(body)

    /** "credit card" is a product noun, not a direction — excluded via lookahead. */
    val DIRECTION_REGEX = Regex(
        """\b(debited|debit|spent|paid|purchase|credited|credit(?!\s*card)|received|deposit|sent)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun extractDirection(body: String): Direction? {
        val credit = Regex("""\b(credited|credit(?!\s*card)|received|deposit)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        val debit = Regex("""\b(debited|debit|spent|paid|purchase|sent)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)
        return when {
            debit && !credit -> Direction.DEBIT
            credit && !debit -> Direction.CREDIT
            debit && credit -> null // ambiguous: review
            else -> null
        }
    }

    /** First a/c-style mask: XX1234, X1234, *1234, a/c 1234 … kept verbatim as display mask. */
    fun extractAccountMask(body: String): String? =
        Regex("""(?:a/?c[\s.:]*|XX|x{2,3}|\*)\s*([0-9]{2,6}|[0-9]{2,6})""", RegexOption.IGNORE_CASE)
            .find(body)?.value?.replace(Regex("""\s"""), "")

    fun extractUtr(body: String): String? =
        Regex("""(?i:utr|ref(?:erence)?(?:\s*no\.?)?|txn\.?\s*id)(?![A-Za-z])\s*[:.#-]*\s*([0-9A-Za-z]{6,24})""")
            .find(body)?.groupValues?.get(1)?.uppercase()

    fun extractBalancePaise(body: String): Long? =
        Regex("""(?:bal(?:ance)?|avl(?:abl)?(?:\s*bal)?)\s*[:.-]?\s*(?:rs\.?|inr)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
            .find(body)?.let { rupeesToPaise(it.groupValues[1]) }

    /** UPI VPA if present (someone@bank). */
    fun extractVpa(body: String): String? =
        Regex("""\b([a-z0-9._-]{2,}@[a-z]{2,})\b""", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.get(1)

    /**
     * Merchant/sender guess, direction-aware (bank-SMS convention):
     *  DEBIT → payee follows for/towards/at/to (Kotak debit uses "to <Payee>")
     *  CREDIT → payer follows from/by ("from JOHN DOE"), falling back to VPA.
     * Possessive pronouns right after the anchor ("to your…") mean the user's own
     * account — never a merchant — and are skipped.
     */
    fun extractMerchant(body: String, direction: Direction? = null): String? {
        val debitAnchors = "for|towards|at|to"
        val creditAnchors = "from|by"
        val anchors = when (direction) {
            Direction.DEBIT -> debitAnchors
            Direction.CREDIT -> creditAnchors
            null -> "$debitAnchors|$creditAnchors"
        }
        val noPronoun = """(?!your\b|you\b|ur\b)"""
        // 1) Capitalized name run after the anchor (JOHN DOE, Amazon Pay,…)
        val capName = Regex("""(?:$anchors)\s+$noPronoun([A-Z][A-Za-z0-9&]*(?:\s+[A-Z][A-Za-z0-9&]*)*)""")
        capName.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ':')?.let { return it }
        // 2) Credit fallback: any word-run after from/by (senders are sometimes lowercase)
        if (direction == Direction.CREDIT) {
            val anyName = Regex("""(?:$creditAnchors)\s+$noPronoun([A-Za-z][A-Za-z0-9&@_-]{2,}(?:\s+[A-Za-z][A-Za-z0-9&_-]{2,})*)""")
            anyName.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ':')?.let { return it }
        }
        return extractVpa(body)
    }
}
