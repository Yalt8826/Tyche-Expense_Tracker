package dev.yashas.expensetracker.data.rules

/**
 * Bundled merchant dictionary (00-MASTER §5): deterministic first-line suggestions.
 * Lowercased substring match on merchantName/vpa. REDACTED seed set — grows with
 * live corrections via user rules (RuleEntity source=USER always beats this).
 */
object MerchantDictionary {

    val MAPPING: Map<String, List<String>> = mapOf(
        "swiggy" to listOf("food", "food.restaurants"),
        "zomato" to listOf("food", "food.restaurants"),
        "dominos" to listOf("food", "food.restaurants"),
        "starbucks" to listOf("food", "food.coffee"),
        "third wave" to listOf("food", "food.coffee"),
        "blinkit" to listOf("food", "food.groceries"),
        "zepto" to listOf("food", "food.groceries"),
        "bigbasket" to listOf("food", "food.groceries"),
        "dmart" to listOf("food", "food.groceries"),
        "uber" to listOf("transport", "transport.cab"),
        "ola" to listOf("transport", "transport.cab"),
        "rapido" to listOf("transport", "transport.cab"),
        "irctc" to listOf("transport"),
        "redbus" to listOf("transport"),
        "indian oil" to listOf("transport", "transport.fuel"),
        "hp petrol" to listOf("transport", "transport.fuel"),
        "amazon" to listOf("shopping"),
        "flipkart" to listOf("shopping"),
        "myntra" to listOf("shopping"),
        "netflix" to listOf("entertainment"),
        "spotify" to listOf("entertainment"),
        "hotstar" to listOf("entertainment"),
        "bookmyshow" to listOf("entertainment"),
        "pharmeasy" to listOf("health"),
        "apollo" to listOf("health"),
        "1mg" to listOf("health"),
        "udemy" to listOf("education"),
        "coursera" to listOf("education"),
        "jio" to listOf("bills", "bills.recharge"),
        "airtel" to listOf("bills", "bills.recharge"),
        "bsnlpdcl" to listOf("bills"),
        "bescom" to listOf("bills", "bills.electricity"),
        "tneb" to listOf("bills", "bills.electricity"),
    )

    /** First match wins; returns (categoryKey, matchedBy) or null. */
    fun suggest(merchant: String?, vpa: String?): Pair<String, String>? {
        for (source in listOf(merchant, vpa)) {
            val text = source?.lowercase()?.trim() ?: continue
            if (text.isEmpty()) continue
            for ((needle, chain) in MAPPING) {
                if (needle in text) return chain.first() to "dictionary:$needle"
            }
        }
        return null
    }
}
