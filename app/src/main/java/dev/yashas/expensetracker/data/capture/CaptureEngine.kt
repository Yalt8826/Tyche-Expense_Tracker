package dev.yashas.expensetracker.data.capture

import dev.yashas.expensetracker.domain.model.Direction

/**
 * The no-AI funnel (04-TECH §4), pure and fixture-testable:
 *   1. sender allowlist        (applied by SmsIngestor before this engine)
 *   2. cheap screen            (₹ amount + debit/credit keyword)
 *   3. field extraction        (mask, UTR, balance, merchant/VPA, value date)
 *   4. generic fallback        (fields may be null; SMS still lands in review)
 *
 * Template knowledge (digit-masked signature → TemplateEntity) is applied at
 * persist time by SmsIngestor, which knows the DB; this class stays pure.
 */
class CaptureEngine {

    enum class Reason {
        NO_AMOUNT,
        NO_DIRECTION_KEYWORD,
        AMBIGUOUS_DIRECTION,
        PARSED,
    }

    data class Result(
        val parsed: ParsedSms?,
        val reason: Reason,
    )

    fun process(body: String): Result {
        val amountPaise = SmsText.extractAmountPaise(body)
            ?: return Result(null, Reason.NO_AMOUNT)

        if (!SmsText.hasDirectionKeyword(body)) {
            return Result(null, Reason.NO_DIRECTION_KEYWORD)
        }

        val direction = SmsText.extractDirection(body)
            ?: return Result(null, Reason.AMBIGUOUS_DIRECTION)

        return Result(
            parsed = ParsedSms(
                amountPaise = amountPaise,
                direction = direction,
                accountMask = SmsText.extractAccountMask(body),
                utrRef = SmsText.extractUtr(body),
                balancePaise = SmsText.extractBalancePaise(body),
                merchant = SmsText.extractMerchant(body, direction),
                vpa = SmsText.extractVpa(body),
                valueDateEpochDay = SmsText.extractValueDateEpochDay(body),
                matchedSignature = SmsText.signature(body),
                fromTemplate = false,
            ),
            reason = Reason.PARSED,
        )
    }
}
