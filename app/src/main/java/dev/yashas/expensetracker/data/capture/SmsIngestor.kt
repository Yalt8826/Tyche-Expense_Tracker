package dev.yashas.expensetracker.data.capture

import dev.yashas.expensetracker.data.db.ExpenseDatabase
import dev.yashas.expensetracker.data.db.entity.AccountEntity
import dev.yashas.expensetracker.data.db.entity.SmsRawEntity
import dev.yashas.expensetracker.data.db.entity.TemplateEntity
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.domain.model.Direction
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.domain.model.SmsParsedState
import dev.yashas.expensetracker.domain.model.TxnType
import kotlinx.coroutines.flow.first

/** Serializable field map for learned templates (stored in TemplateEntity.fieldMap). */
data class TemplateFieldMap(
    val direction: Direction,
    val accountMask: String?,
    val utrPrefix: String?,
    val merchant: String?,
)

/**
 * Persists one inbound SMS through the funnel (04-TECH §4 stage 5).
 *
 * v1 trust behavior: every captured row is AUTO_REVIEW until the suggestion engine
 * (P4) can attach a confident category — then AUTO_CONFIRMED activates (00 §4
 * detected → needs confirmation → confirmed ledger).
 */
class SmsIngestor(
    private val db: ExpenseDatabase,
    private val engine: CaptureEngine = CaptureEngine(),
) {

    enum class Outcome { CAPTURED, DUPLICATE_SMS, DUPLICATE_TXN, IGNORED }

    suspend fun ingest(sender: String, body: String, deliveryTimestamp: Long): Outcome {
        // stage 1: allowlist (comma-joined per account row) — BEFORE any persistence
        val allowlisted = db.catalogDao().allSenderAllowlists()
            .flatMap { it.split(',') }
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
        if (sender.trim().uppercase() !in allowlisted) {
            return Outcome.IGNORED
        }

        // raw-SMS dedup (carrier redeliveries)
        val digest = SmsText.digest(body)
        val smsId = db.catalogDao().insertSms(
            SmsRawEntity(
                sender = sender,
                timestamp = deliveryTimestamp,
                body = body,
                digest = digest,
                parsedState = SmsParsedState.UNPARSED,
            ),
        )
        if (smsId == -1L) return Outcome.DUPLICATE_SMS

        // stages 2–4: cheap screen + field extraction
        val result = engine.process(body)
        val parsed = result.parsed
        if (parsed == null) {
            return Outcome.IGNORED
        }

        // template stage: does this body's signature belong to a known family?
        val template = db.catalogDao().templateBySignature(parsed.matchedSignature!!)
        val fromTemplate = template != null
        if (template != null) db.catalogDao().bumpTemplateHit(template.id)

        // TXN-level dedup: (account, utr) is the ledger key (00 §4)
        val account = resolveAccount(sender, parsed.accountMask)
        if (parsed.utrRef != null && db.transactionDao().countByUtr(account.id, parsed.utrRef) > 0) {
            return Outcome.DUPLICATE_TXN
        }

        val txn = TransactionEntity(
            timestamp = deliveryTimestamp,
            valueDate = parsed.valueDateEpochDay ?: deliveryTimestamp / TransactionEntity.MILLIS_PER_DAY,
            amountPaise = parsed.amountPaise,
            type = when (parsed.direction) {
                Direction.DEBIT -> TxnType.EXPENSE
                Direction.CREDIT -> TxnType.INCOME
            },
            accountId = account.id,
            merchantName = parsed.merchant,
            vpa = parsed.vpa,
            categoryKey = null,
            note = null,
            provenance = Provenance.AUTO_REVIEW,
            sourceSmsId = smsId,
            refundOfTxnId = null,
            transferGroupId = null,
            utrRef = parsed.utrRef,
            ruleAppliedKey = template?.let { "template:${it.id}" },
        )
        db.transactionDao().insert(txn)
        db.catalogDao().markSmsState(smsId, SmsParsedState.PARSED)
        return Outcome.CAPTURED
    }

    /**
     * Template induction (04-TECH §4): the user confirms one message; its digit-masked
     * signature + the confirmed field map generalize to every SMS of that family.
     */
    suspend fun learnTemplate(bankId: String, body: String, confirmed: ParsedSms) {
        db.catalogDao().upsertTemplate(
            TemplateEntity(
                bankId = bankId,
                signature = SmsText.signature(body),
                fieldMap = serializeFieldMap(
                    TemplateFieldMap(
                        direction = confirmed.direction,
                        accountMask = confirmed.accountMask,
                        utrPrefix = confirmed.utrRef?.take(4),
                        merchant = confirmed.merchant,
                    ),
                ),
                version = 1,
            ),
        )
    }

    private suspend fun resolveAccount(sender: String, accountMask: String?): AccountEntity {
        val existing = db.catalogDao().observeAccounts().first().firstOrNull { account ->
            accountMask == null || account.last4Mask == accountMask || sender.uppercase() in account.smsSenderAllowlist.uppercase().split(",")
        }
        if (existing != null) return existing
        val bankId = BankTemplates.bankForSender(sender) ?: "UNKNOWN"
        val entity = AccountEntity(
            bankName = bankId,
            last4Mask = accountMask ?: "----",
            smsSenderAllowlist = sender.uppercase(),
        )
        val id = db.catalogDao().upsertAccount(entity)
        return entity.copy(id = if (id == -1L) 0L else id)
    }

    companion object {
        fun serializeFieldMap(map: TemplateFieldMap): String =
            listOf(
                map.direction.name,
                map.accountMask.orEmpty(),
                map.utrPrefix.orEmpty(),
                map.merchant.orEmpty(),
            ).joinToString("|")

        fun deserializeFieldMap(raw: String): TemplateFieldMap {
            val parts = raw.split("|")
            return TemplateFieldMap(
                direction = Direction.valueOf(parts.getOrElse(0) { Direction.DEBIT.name }),
                accountMask = parts.getOrNull(1)?.ifEmpty { null },
                utrPrefix = parts.getOrNull(2)?.ifEmpty { null },
                merchant = parts.getOrNull(3)?.ifEmpty { null },
            )
        }
    }
}
