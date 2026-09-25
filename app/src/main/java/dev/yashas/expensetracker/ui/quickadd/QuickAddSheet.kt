package dev.yashas.expensetracker.ui.quickadd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yashas.expensetracker.data.db.entity.CategoryEntity
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.domain.model.MoneyFormat
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * S7 Quick-add sheet: keypad-first cash entry (SMS-invisible spending is the design
 * target). Full custom keypad + Lottie arrive with P6 polish; v1 wires the flow.
 */
@Composable
fun QuickAddSheet(
    repo: TxnRepository,
    categories: List<CategoryEntity>,
    onDone: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf<String?>(null) }

    val paise = amountText.toLongOrNull() ?: 0L

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            text = "Add expense",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = MoneyFormat.formatPaise(paise * 100),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { s -> amountText = s.filter(Char::isDigit).take(8) },
            label = { Text("Amount in rupees") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it.take(40) },
            label = { Text("Where (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text("Category", style = MaterialTheme.typography.labelLarge)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(categories.filter { it.parentId == null && it.key != "other" }, key = { it.key }) { cat ->
                FilterChip(
                    selected = selectedKey == cat.key,
                    onClick = { selectedKey = cat.key },
                    label = { Text(cat.name) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (paise > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        repo.quickAddExpense(
                            amountPaise = paise * 100,
                            categoryKey = selectedKey,
                            merchant = merchant.trim().ifEmpty { null },
                            note = null,
                            valueDateEpochDay = LocalDate.now().toEpochDay(),
                            nowMillis = System.currentTimeMillis(),
                        )
                        onDone()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add")
        }
        Spacer(Modifier.height(24.dp))
    }
}
