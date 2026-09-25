package dev.yashas.expensetracker.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yashas.expensetracker.data.db.entity.TransactionEntity
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.domain.model.Provenance
import dev.yashas.expensetracker.ui.components.TxnRow
import java.time.Instant
import java.time.ZoneId

/**
 * S9 Transactions list + S10 Review Inbox (filtered mode) + S11 detail sheet.
 * Search = free text over merchant/vpa/category/note; summary bar counts matches.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    repo: TxnRepository,
    reviewMode: Boolean = false,
) {
    val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.factory(repo))
    val state by vm.state.collectAsState()
    var detailFor by remember { mutableStateOf<TransactionEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // search bar (hidden in review mode — the inbox is about deciding, not searching)
        if (!reviewMode) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search merchant, category, note…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                },
                singleLine = true,
            )
            if (state.query.isNotBlank()) {
                Text(
                    text = "${state.filtered.size} ${if (state.filtered.size == 1) "transaction" else "transactions"} · " +
                        MoneyFormat.formatPaise(state.filtered.filter { it.type.name == "EXPENSE" }.sumOf { it.amountPaise }),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (reviewMode) {
                items(state.reviewCards, key = { it.txn.id }) { card ->
                    ReviewInboxCard(
                        card = card,
                        onConfirm = { key, rule -> vm.confirm(card.txn, key, rule) },
                        onOpenDetail = { detailFor = card.txn },
                    )
                }
                items(state.suspectCards, key = { "dup-${it.keepId}-${it.mergeId}" }) { card ->
                    SuspectCard(
                        card = card,
                        onKeepBoth = { },
                        onMerge = { vm.mergeSuspect(card) },
                    )
                }
                items(state.transferCards, key = { "xfer-${it.debitId}" }) { card ->
                    TransferPairCard(card = card, onMarkTransfer = { vm.markTransfer(card) })
                }
                if (state.reviewCards.isEmpty() && state.suspectCards.isEmpty() && state.transferCards.isEmpty()) {
                    item { InboxZeroState() }
                }
            } else {
                items(state.filtered, key = { it.id }) { txn ->
                    val cat = txn.categoryKey?.let { state.categories[it] }
                    TxnRow(
                        txn = txn,
                        categoryName = cat?.name,
                        categoryIconName = cat?.icon,
                        categoryColorToken = cat?.colorToken,
                        onClick = { detailFor = txn },
                    )
                }
                if (state.filtered.isEmpty() && !state.loading) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 96.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Transactions appear as your bank SMS arrive — or add one with +",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    detailFor?.let { txn ->
        TransactionDetailSheet(
            txn = txn,
            categoryName = txn.categoryKey?.let { state.categories[it]?.name },
            onDelete = {
                vm.deleteWithUndo(txn.id)
                detailFor = null
            },
            onDismiss = { detailFor = null },
        )
    }
}

/** S10 review card: parsed txn + suggested category chips + confirm affordance. */
@Composable
private fun ReviewInboxCard(
    card: LedgerViewModel.ReviewCard,
    onConfirm: (categoryKey: String, createRule: Boolean) -> Unit,
    onOpenDetail: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        onClick = onOpenDetail,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.txn.merchantName ?: card.txn.vpa ?: "Unknown merchant",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "bank capture · needs your confirmation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = MoneyFormat.formatPaise(card.txn.amountPaise),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val suggestion = card.suggestion
            if (suggestion != null) {
                Text(
                    text = suggestion.reasonText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val suggestedKey = suggestion?.categoryKey
                if (suggestedKey != null) {
                    AssistChip(
                        onClick = { onConfirm(suggestedKey, false) },
                        label = { Text("Confirm: $suggestedKey") },
                    )
                }
                TextButton(onClick = { onConfirm("other", false) }) { Text("Other") }
                TextButton(onClick = onOpenDetail) { Text("Edit") }
            }
        }
    }
}

/** S10 special state: possible duplicate — [Keep both] [Merge]. */
@Composable
private fun SuspectCard(
    card: LedgerViewModel.SuspectCard,
    onKeepBoth: () -> Unit,
    onMerge: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Possible duplicate · ${MoneyFormat.formatPaise(card.amountPaise)}" +
                    (card.merchant?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onKeepBoth) { Text("Keep both") }
                TextButton(onClick = onMerge) { Text("Merge") }
            }
        }
    }
}

/** S10 special state: possible transfer — both legs, one tap to mark. */
@Composable
private fun TransferPairCard(
    card: LedgerViewModel.TransferPairCard,
    onMarkTransfer: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Money moved between your accounts · ${MoneyFormat.formatPaise(card.amountPaise)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = onMarkTransfer) { Text("Mark as transfer") }
        }
    }
}

@Composable
private fun InboxZeroState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("All clear", style = MaterialTheme.typography.titleMedium)
        Text(
            "New detections from your bank SMS will show up here",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** S11 detail sheet: plain-facts explainability + raw source (trust affordances). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(
    txn: TransactionEntity,
    categoryName: String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = MoneyFormat.formatPaise(txn.amountPaise),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            DetailLine("Merchant", txn.merchantName ?: txn.vpa ?: "—")
            DetailLine("Category", categoryName ?: "Not set yet")
            DetailLine("Detected", when (txn.provenance) {
                Provenance.AUTO_CONFIRMED -> "Automatically · confirmed"
                Provenance.AUTO_REVIEW -> "Automatically · needs review"
                Provenance.MANUAL -> "Added by you"
            })
            DetailLine(
                "Date",
                Instant.ofEpochMilli(txn.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().toString(),
            )
            if (txn.type == TxnType_TRANSFER()) DetailLine("Transfer", "Excluded from spending")
            DetailLine("Why this category?", if (txn.categoryKey == null) "You haven't tagged it yet" else "Matched your categories")
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun TxnType_TRANSFER() = dev.yashas.expensetracker.domain.model.TxnType.TRANSFER

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
