package dev.yashas.expensetracker.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.components.TxnRow

/**
 * S6 Home dashboard (v1 scope): review banner + hero month number + recent 5.
 * Hero count-up, sparkline backdrop and ring arrive with the chart/polish phases.
 */
@Composable
fun HomeScreen(
    repo: TxnRepository,
    onOpenReview: () -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = homeFactory(repo))
    val state by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        if (state.reviewCount > 0) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = onOpenReview) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "${state.reviewCount} ${if (state.reviewCount == 1) "transaction needs" else "transactions need"} your confirmation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Review them to keep your totals honest",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column {
            Text(
                text = state.monthLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormat.formatPaise(state.monthNetPaise),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Spent this month",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text("Recent", style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                if (state.recent.isEmpty()) {
                    Text(
                        "No expenses yet — your bank SMS will start appearing here automatically. " +
                            "Add your first expense anytime.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.recent.forEach { txn ->
                        val catKey = txn.categoryKey
                        TxnRow(txn = txn, categoryName = catKey, categoryIconName = null, categoryColorToken = null)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
