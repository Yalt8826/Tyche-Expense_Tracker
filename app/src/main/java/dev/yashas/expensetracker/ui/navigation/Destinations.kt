package dev.yashas.expensetracker.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The 4 primary tabs (00-MASTER §7). Review Inbox is a filtered mode of Transactions,
 * never a tab; Settings lives behind the Home avatar. Routes stay arg-free until
 * the real screens land.
 */
enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home),
    TRANSACTIONS("transactions", "Transactions", Icons.Outlined.ReceiptLong),
    ANALYTICS("analytics", "Analytics", Icons.Outlined.Insights),
    BUDGETS("budgets", "Budgets", Icons.Outlined.PieChart),
}

val bottomTabOrder = listOf(Destination.HOME, Destination.TRANSACTIONS, Destination.ANALYTICS, Destination.BUDGETS)
