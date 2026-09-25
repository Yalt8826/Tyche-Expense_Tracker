package dev.yashas.expensetracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.yashas.expensetracker.ui.components.PlaceholderScreen
import dev.yashas.expensetracker.ui.navigation.Destination
import dev.yashas.expensetracker.ui.navigation.bottomTabOrder

/**
 * App shell: 4 tabs + center-adjacent quick-add FAB (00-MASTER §7).
 * Tabs keep their state across switches (saveState/restoreState).
 */
@Composable
fun ExpenseApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

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
            // Quick-add sheet arrives in P4 (01-SCREENS S7); shell placement is part of the skeleton.
            FloatingActionButton(onClick = { /* quick-add: P4 */ }) {
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
                PlaceholderScreen(title = "Home", subtitle = "Dashboard lands in P4")
            }
            composable(Destination.TRANSACTIONS.route) {
                PlaceholderScreen(title = "Transactions", subtitle = "Ledger lands in P4")
            }
            composable(Destination.ANALYTICS.route) {
                PlaceholderScreen(title = "Analytics", subtitle = "Charts land in P5")
            }
            composable(Destination.BUDGETS.route) {
                PlaceholderScreen(title = "Budgets", subtitle = "Budgets land in P5")
            }
        }
    }
}
