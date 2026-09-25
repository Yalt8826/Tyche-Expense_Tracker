package dev.yashas.expensetracker.data.rules

import dev.yashas.expensetracker.data.db.ExpenseDatabase
import dev.yashas.expensetracker.data.db.entity.RuleEntity
import dev.yashas.expensetracker.domain.model.RuleSource

/**
 * Deterministic suggestion precedence (04-TECH §5):
 * explicit user rules → system rules → merchant dictionary → user history.
 * Suggested category rides on the review card; one tap confirms.
 */
class SuggestionEngine(private val db: ExpenseDatabase) {

    data class Suggestion(
        val categoryKey: String,
        val reasonKey: String,
        /** Plain-language reason for "Why this category?" (01-SCREENS S11). */
        val reasonText: String,
        val confidenceRank: Int,
    )

    /**
     * Compact predicate DSL: `vpa~swiggy` / `merchant~IRCTC` / `sender~HDFCBK`.
     * Substring match, case-insensitive.
     */
    fun predicateMatches(predicate: String, sender: String, merchant: String?, vpa: String?): Boolean {
        val idx = predicate.indexOf('~')
        if (idx <= 0) return false
        val field = predicate.take(idx).lowercase()
        val needle = predicate.drop(idx + 1).lowercase()
        val haystack = when (field) {
            "sender" -> sender
            "merchant" -> merchant.orEmpty()
            "vpa" -> vpa.orEmpty()
            else -> return false
        }
        return needle in haystack.lowercase()
    }

    suspend fun suggest(sender: String, merchant: String?, vpa: String?): Suggestion? {
        // 1-2. explicit rules (user first, then system, both by hitCount)
        val rules = db.catalogDao().allRules() // ORDER BY source DESC, hitCount DESC
        for (rule in rules) {
            if (predicateMatches(rule.predicate, sender, merchant, vpa)) {
                db.catalogDao().bumpRuleHit(rule.id)
                return Suggestion(
                    categoryKey = rule.actionCategoryKey,
                    reasonKey = "rule:${rule.id}",
                    reasonText = if (rule.source == RuleSource.USER) {
                        "You set this rule — always categorize it this way"
                    } else {
                        "Learned from your corrections (${rule.hitCount} so far)"
                    },
                    confidenceRank = if (rule.source == RuleSource.USER) 0 else 1,
                )
            }
        }

        // 3. merchant dictionary
        MerchantDictionary.suggest(merchant, vpa)?.let { (key, matchedBy) ->
            return Suggestion(
                categoryKey = key,
                reasonKey = matchedBy,
                reasonText = "Merchant match — known category for this name",
                confidenceRank = 2,
            )
        }

        // 4. user history: last dominant category for this exact merchant/vpa
        val identity = merchant?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
            ?: vpa?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val history = db.transactionDao().historyCategoryCounts(identity)
        val top = history.firstOrNull { it.label != "(none)" && it.amountPaise > 0 } ?: return null
        return Suggestion(
            categoryKey = top.label,
            reasonKey = "history:$identity",
            reasonText = "You usually tag ${top.label} here (${top.txnCount} past ${if (top.txnCount == 1) "transaction" else "transactions"})",
            confidenceRank = 3,
        )
    }

    /** Rule creation from a correction (S11 "Always categorize this as Food?"). */
    suspend fun createRuleFromCorrection(merchant: String?, vpa: String?, categoryKey: String): Boolean {
        val identity = merchant?.trim()?.takeIf { it.isNotEmpty() }
            ?: vpa?.trim()?.takeIf { it.isNotEmpty() }
            ?: return false
        val field = if (merchant?.trim()?.isNotEmpty() == true) "merchant" else "vpa"
        val predicate = "$field~${identity.lowercase()}"
        val exists = db.catalogDao().allRules().any { it.predicate == predicate && it.actionCategoryKey == categoryKey }
        if (exists) return false
        db.catalogDao().insertRule(
            RuleEntity(
                predicate = predicate,
                actionCategoryKey = categoryKey,
                source = RuleSource.USER,
                hitCount = 0,
            ),
        )
        return true
    }
}
