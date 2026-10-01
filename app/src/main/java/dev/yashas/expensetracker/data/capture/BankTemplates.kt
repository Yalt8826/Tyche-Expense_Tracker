package dev.yashas.expensetracker.data.capture

/**
 * Seeded bank-SMS templates (04-TECH §4: "ship templates for the family's actual banks").
 *
 * v1 extraction model: each bank has a bankId + a per-field anchor strategy; the
 * digit-masked body signature (SmsText.signature) is the family key stored in
 * TemplateEntity.signature. Fixtures below are REDACTED synthetic formats — no real data.
 */
object BankTemplates {

    data class Seed(
        val bankId: String,
        val senderCodes: List<String>,
        val fixtures: List<String>,
    )

    val SEEDS = listOf(
        Seed(
            bankId = "KOTAK",
            senderCodes = listOf("KOTAKB"),
            fixtures = listOf(
                "Sent Rs.70.00 from Kotak Bank A/c X9624 to Vaishnavi Juice And on 27-09-26. UPI Ref 627065970015. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud",
                "Sent Rs.2,000.00 from Kotak Bank A/c X9624 to rajrishank0@okaxis on 26-09-26. UPI Ref 663561033525. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud",
            ),
        ),
        Seed(
            bankId = "HDFC",
            senderCodes = listOf("AD-HDFCBK", "VM-HDFCBK", "HDFCBK"),
            fixtures = listOf(
                "Rs.200.00 debited from a/c XX1234 on 26-09-26 for Swiggy. Ref 123456789012. Not you? Call 18001234",
                "Rs.1,250.50 credited to a/c XX1234 on 27-09-26 towards Salary refund. Ref 223456789012",
            ),
        ),
        Seed(
            bankId = "SBI",
            senderCodes = listOf("AD-SBIBNK", "VM-SBIBNK", "SBIBNK"),
            fixtures = listOf(
                "Rs 500 debited on 26-09-26 from A/c XX9876 towards AMAZON PAY. Ref 987654321012",
                "Your A/c no. XX9876 is debited by Rs 1,200.00 on 26-09-26 towards UPI/AMAZON PAY. Not you? Call 1800111109. Ref 678765432100",
                "Rs 2,500.75 credited on 28-09-26 in A/c XX9876 towards UPI from john@upi. Ref 887654321012",
            ),
        ),
        Seed(
            bankId = "ICICI",
            senderCodes = listOf("AD-ICICIB", "VM-ICICIB", "ICICIB"),
            fixtures = listOf(
                "Rs.1500.00 spent on ICICI Credit Card XX4321 at ZOMATO on 26-09-26. Txn ID 123456789012",
                "Rs.99.00 debited from ICICI Bank A/c XX1122 on 26-09-26 for JIO Recharge. Txn ID 423456789012",
            ),
        ),
        Seed(
            bankId = "AXIS",
            senderCodes = listOf("AD-AXISBK", "VM-AXISBK", "AXISBK"),
            fixtures = listOf(
                "Rs.75.00 debited from A/c XX5566 on 26-09-26 (UPI/QR purchase). Ref 112233445566",
                "Rs.10,000.00 credited to A/c XX5566 on 29-09-26 towards IMPS from ACA999. Ref 212233445566",
            ),
        ),
    )

    /** Sender code → bankId for the allowlist stage. */
    fun bankForSender(sender: String): String? {
        val s = sender.uppercase().trim()
        return SEEDS.firstOrNull { seed -> seed.senderCodes.any(s::endsWith) }?.bankId
    }
}
