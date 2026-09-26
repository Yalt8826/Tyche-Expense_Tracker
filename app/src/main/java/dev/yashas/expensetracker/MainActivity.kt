package dev.yashas.expensetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dev.yashas.expensetracker.ui.ExpenseApp
import dev.yashas.expensetracker.ui.theme.ExpenseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val txnRepo = AppGraph.txnRepository(this)
        val analyticsRepo = AppGraph.analyticsRepository(this)
        val budgetRepo = AppGraph.budgetRepository(this)
        lifecycleScope.launch(Dispatchers.IO) { AppGraph.bootstrap(this@MainActivity) }
        setContent {
            ExpenseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ExpenseApp(txnRepo = txnRepo, analyticsRepo = analyticsRepo, budgetRepo = budgetRepo)
                }
            }
        }
    }
}
