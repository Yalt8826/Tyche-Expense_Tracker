package dev.yashas.expensetracker.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.yashas.expensetracker.data.export.CsvExporter
import dev.yashas.expensetracker.data.repo.BudgetRepository
import dev.yashas.expensetracker.data.repo.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * P6 / S17 Settings (behind the Home avatar): profile name, CSV export to Downloads,
 * demo-data wipe (debug builds), and the double-confirmed wipe-everything reset.
 * Zero-network preserved: export is a local MediaStore write.
 */
@Composable
fun SettingsScreen(
    budgetRepo: BudgetRepository,
    ledgerRows: suspend () -> List<dev.yashas.expensetracker.data.db.entity.TransactionEntity>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPrefs(context) }
    var name by remember { mutableStateOf("") }
    var nameLoaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmDemoWipe by remember { mutableStateOf(false) }
    var confirmWipeAll by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        prefs.nameFlow.collect { value ->
            name = value
            nameLoaded = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("Done") }
        }

        // ── profile ──
        androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name (used in the greeting)") },
                    singleLine = true,
                    enabled = nameLoaded,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { scope.launch { prefs.setName(name) ; status = "Name saved" } },
                    enabled = nameLoaded && name.isNotBlank(),
                ) { Text("Save name") }
            }
        }

        // ── data ──
        androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Export writes a CSV of your full ledger to your phone's Downloads folder. " +
                        "Everything stays on-device — the app never touches the network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        scope.launch {
                            status = "Exporting…"
                            val rows = ledgerRows()
                            val result = withContext(Dispatchers.IO) { CsvExporter.export(context, rows) }
                            status = if (result.uri != null) {
                                "Exported ${result.rowCount} transactions → Downloads/${result.fileName}"
                            } else {
                                "Export failed — storage unavailable"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export ledger to CSV") }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (dev.yashas.expensetracker.BuildConfig.DEBUG) {
                    OutlinedButton(
                        onClick = { confirmDemoWipe = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remove demo data") }
                }
                OutlinedButton(
                    onClick = { confirmWipeAll = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Wipe all transactions", color = MaterialTheme.colorScheme.error) }
            }
        }

        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(96.dp)) // FAB clearance
    }

    if (confirmDemoWipe) {
        AlertDialog(
            onDismissRequest = { confirmDemoWipe = false },
            title = { Text("Remove demo data?") },
            text = { Text("Deletes every demo-seeded transaction and its sample budgets. Your real captured transactions are untouched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDemoWipe = false
                    scope.launch {
                        val n = budgetRepo.wipeDemoData()
                        status = "Removed $n demo transactions"
                    }
                }) { Text("Remove demo data") }
            },
            dismissButton = { TextButton(onClick = { confirmDemoWipe = false }) { Text("Cancel") } },
        )
    }

    if (confirmWipeAll) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmWipeAll = false },
            title = { Text("Wipe ALL transactions?") },
            text = {
                Column {
                    Text("This permanently deletes every transaction — captured, confirmed, and manual. Accounts, categories and budgets stay. This cannot be undone.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Type DELETE to confirm") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWipeAll = false
                        scope.launch {
                            val n = budgetRepo.wipeAllTransactions()
                            status = "Wiped $n transactions"
                        }
                    },
                    enabled = typed.trim().uppercase() == "DELETE",
                ) { Text("Wipe everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipeAll = false }) { Text("Cancel") } },
        )
    }
}
