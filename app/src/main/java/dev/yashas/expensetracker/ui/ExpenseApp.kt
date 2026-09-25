package dev.yashas.expensetracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.ui.components.PlaceholderScreen
import dev.yashas.expensetracker.ui.home.HomeScreen
import dev.yashas.expensetracker.ui.ledger.LedgerScreen
import dev.yashas.expensetracker.ui.navigation.Destination
import dev.yashas.expensetracker.ui.navigation.bottomTabOrder
import dev.yashas.expensetracker.ui.quickadd.QuickAddSheet

/**
 * App shell: 4 tabs + quick-add FAB (00-MASTER §7). Review Inbox is reached from the
 * Home banner as a filtered mode of Transactions (route arg), never its own tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseApp(repo: TxnRepository) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    var quickAddOpen by remember { mutableStateOf(false) }
    val categories by repo.observeCategories().collectAsState(initial = emptyList())

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomTabOrder.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(imageVector = dest.icon, contentDescription = dest.label) },
                        label = { Text(text = dest.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { quickAddOpen = true }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Quick add")
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.HOME.route) {
                HomeScreen(
                    repo = repo,
                    onOpenReview = { navController.navigate(TRANSACTIONS_REVIEW_ROUTE) },
                )
            }
            composable(Destination.TRANSACTIONS.route) {
                LedgerScreen(repo = repo, reviewMode = false)
            }
            composable(Destination.ANALYTICS.route) {
                PlaceholderScreen(title = "Analytics", subtitle = "Charts land in P5")
            }
            composable(Destination.BUDGETS.route) {
                PlaceholderScreen(title = "Budgets", subtitle = "Budgets land in P5")
            }
            composable(TRANSACTIONS_REVIEW_ROUTE) {
                LedgerScreen(repo = repo, reviewMode = true)
            }
        }
    }

    if (quickAddOpen) {
        ModalBottomSheet(onDismissRequest = { quickAddOpen = false }) {
            QuickAddSheet(
                repo = repo,
                categories = categories,
                onDone = { quickAddOpen = false },
            )
        }
    }
}

private const val TRANSACTIONS_REVIEW_ROUTE = "transactions/review"
