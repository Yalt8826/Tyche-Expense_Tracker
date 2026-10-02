package dev.yashas.expensetracker.ui.ledger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState

/** Default quick-confirm categories on review cards (one tap each, UI-AUDIT R1). */
private val QUICK_CHIPS = listOf("food" to "Food", "transport" to "Transport", "shopping" to "Shopping", "bills" to "Bills")

/**
 * S9 Transactions list + S10 Review Inbox (filtered mode) + S11 detail sheet.
 * Post-audit: month strip + sticky-style day headers (R2); review groups with
 * one-tap batch confirm, quick chips, category picker and undo (R1); FAB padding (R7).
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
    var pickerOpen by remember { mutableStateOf<String?>(null) } // group key awaiting category
    var showDatePicker by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // undo snackbar after a batch confirm
    if (state.lastUndoneCount > 0) {
        scope.launch {
            snackbar.showSnackbar(
                message = "Confirmed ${state.lastUndoneCount} transaction${if (state.lastUndoneCount == 1) "" else "s"}",
                actionLabel = "UNDO",
                duration = androidx.compose.material3.SnackbarDuration.Short,
            ).let { if (it == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoLastBatch() }
            vm.dismissUndo()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!reviewMode) {
                // header
                Text(
                    text = "Transactions",
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
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
                // period chips (This week / This month / Last month / All) + calendar range picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.presetOptions.forEach { opt ->
                        FilterChip(
                            selected = state.periodKey == opt.key,
                            onClick = { vm.setPeriod(opt.key) },
                            label = { Text(opt.label) },
                        )
                    }
                    // calendar button for custom From/To (icon-only until a range is active)
                    FilterChip(
                        selected = state.periodKey == LedgerFilters.RANGE,
                        onClick = { showDatePicker = true },
                        label = {
                            if (state.periodKey == LedgerFilters.RANGE) {
                                val f = state.customFrom
                                val t = state.customTo
                                if (f != null && t != null) Text(LedgerFilters.rangeLabel(f, t))
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = "Pick date range",
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
                if (state.query.isNotBlank()) {
                    Text(
                        text = "${state.filtered.size} ${if (state.filtered.size == 1) "transaction" else "transactions"} · " +
                            MoneyFormat.formatRupeesWhole(state.filtered.filter { it.type.name == "EXPENSE" }.sumOf { it.amountPaise }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        text = "${state.reviewCount} Unconfirmed Transactions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${state.reviewGroups.size} ${if (state.reviewGroups.size == 1) "payee" else "payees"} · " +
                            "hold a card and drop it on a tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (reviewMode) {
                    // bubble targets: the card's suggestion, then user-created tags
                    // (zero-spend but deliberate — a new tag must be usable immediately),
                    // then all-time spending-ranked tags, then "More".
                    val ranked = dev.yashas.expensetracker.data.repo.HomeBreakdown.slices(
                        rows = state.all.filter { it.type.name == "EXPENSE" },
                        nameByKey = state.categories.mapValues { c -> c.value.name },
                        colorTokenByKey = state.categories.mapValues { c -> c.value.colorToken },
                    )

                    items(state.reviewGroups, key = { it.key }) { group ->
                        // drag-to-tag targets assembled per group (dedup by key),
                        // capped at 8 + "More" so the ring never overcrowds
                        val targets = run {
                            val list = buildList {
                                group.suggestion?.let {
                                    add(TagTarget(it.categoryKey, it.categoryKey, colorTokenForKey(it.categoryKey, state.categories)))
                                }
                                // every user-created tag — new tags must be droppable on day one
                                state.categories.values
                                    .filter { it.key.startsWith("user_") }
                                    .sortedBy { it.name }
                                    .forEach { cat ->
                                        if (none { t -> t.key == cat.key }) {
                                            add(TagTarget(cat.key, cat.name, cat.colorToken))
                                        }
                                    }
                                ranked.forEach { slice ->
                                    if (none { t -> t.key == slice.categoryKey }) {
                                        add(TagTarget(slice.categoryKey, slice.label, slice.colorToken))
                                    }
                                }
                                if (none { t -> t.key == "more" }) add(TagTarget("more", "More", "series_grey"))
                            }
                            if (list.size <= 9) list else list.take(8) + list.last()
                        }
                        DragToTagCard(
                            targets = targets,
                            amountText = MoneyFormat.formatRupeesWhole(group.totalPaise),
                            initial = group.displayName.take(1).uppercase(),
                            onDropTarget = { key ->
                                if (key == "more") pickerOpen = group.key
                                else vm.confirmGroup(group, key)
                            },
                            onOpenDetail = {
                                state.all.firstOrNull { it.id == group.ids.first() }?.let { detailFor = it }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            ReviewGroupCardContent(group = group)
                        }
                    }
                    items(state.suspectCards, key = { "dup-${it.keepId}-${it.mergeId}" }) { card ->
                        SuspectCardUi(card = card, onKeepBoth = { }, onMerge = { vm.mergeSuspect(card) })
                    }
                    items(state.transferCards, key = { "xfer-${it.debitId}" }) { card ->
                        TransferPairCardUi(card = card, onMarkTransfer = { vm.markTransfer(card) })
                    }
                    if (state.reviewGroups.isEmpty() && state.suspectCards.isEmpty() && state.transferCards.isEmpty()) {
                        item { InboxZeroState() }
                    }
                } else {
                    val rows = state.filtered
                        .sortedByDescending { it.valueDate ?: it.timestamp / 86_400_000L }
                    var lastDay = -1L
                    val dayOf: (TransactionEntity) -> Long = { it.valueDate ?: it.timestamp / 86_400_000L }
                    rows.forEach { txn ->
                        val d = dayOf(txn)
                        if (d != lastDay) {
                            item(key = "h-$d") {
                                Text(
                                    text = LedgerFilters.dayLabel(d),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
                                )
                            }
                            lastDay = d
                        }
                        item(key = "t-${txn.id}") {
                            val cat = txn.categoryKey?.let { state.categories[it] }
                            TxnRow(
                                txn = txn,
                                categoryName = cat?.name,
                                categoryIconName = cat?.icon,
                                categoryColorToken = cat?.colorToken,
                                onClick = { detailFor = txn },
                            )
                        }
                    }
                    if (rows.isEmpty() && !state.loading) {
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
                item(key = "fab-space") { Spacer(Modifier.height(96.dp)) } // R7: FAB clearance
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = state.customFrom?.times(86_400_000L),
            initialSelectedEndDateMillis = state.customTo?.minus(1)?.times(86_400_000L),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMillis = dateRangePickerState.selectedStartDateMillis
                        val endMillis = dateRangePickerState.selectedEndDateMillis
                        if (startMillis != null && endMillis != null) {
                            // millis → epochDay; +1 on end so the picked day is included (end-exclusive bounds)
                            val from = startMillis / 86_400_000L
                            val to = endMillis / 86_400_000L + 1
                            vm.setCustomRange(from, to)
                        }
                        showDatePicker = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                headline = null,
                title = null,
                showModeToggle = false,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }

    detailFor?.let { txn ->
        TransactionDetailSheet(
            txn = txn,
            categoryName = txn.categoryKey?.let { state.categories[it]?.name },
            onQuickConfirm = if (txn.provenance == Provenance.AUTO_REVIEW) {
                { key ->
                    vm.confirm(txn.id, key)
                    detailFor = null
                }
            } else null,
            onDelete = {
                vm.deleteWithUndo(txn.id)
                detailFor = null
            },
            onDismiss = { detailFor = null },
        )
    }

    pickerOpen?.let { groupKey ->
        CategoryPickerSheet(
            categories = state.categories.values.filter { it.parentId == null }.sortedBy { it.name },
            onPick = { key ->
                state.reviewGroups.firstOrNull { it.key == groupKey }?.let { vm.confirmGroup(it, key) }
                pickerOpen = null
            },
            onDismiss = { pickerOpen = null },
        )
    }
}

/** Color token for a category key: real entity token, else violet for unknown/unsaved. */
private fun colorTokenForKey(
    key: String,
    categories: Map<String, dev.yashas.expensetracker.data.db.entity.CategoryEntity>,
): String = categories[key]?.colorToken ?: "series_violet"

/** R1 group card CONTENT (avatar, payee, N×, total, latest day) — wrapped by DragToTagCard. */
@Composable
private fun ReviewGroupCardContent(
    group: LedgerViewModel.ReviewGroupUi,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // initial avatar (consistent with the ledger list, R3)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = group.displayName.take(1).uppercase(),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.displayName + if (group.count > 1) "  ·  ${group.count}×" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "latest ${group.latestDayLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = MoneyFormat.formatRupeesWhole(group.totalPaise),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        group.suggestion?.let {
            Text(
                text = it.reasonText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** R1 category picker for "Other" — one sheet, whole group confirmed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    categories: List<dev.yashas.expensetracker.data.db.entity.CategoryEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Confirm as…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Applies to every transaction of this payee",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories, key = { it.key }) { cat ->
                    FilterChip(
                        selected = false,
                        onClick = { onPick(cat.key) },
                        label = { Text(cat.name) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** S10 special state: possible duplicate — [Keep both] [Merge]. */
@Composable
private fun SuspectCardUi(
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
private fun TransferPairCardUi(
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

/** S11 detail sheet: plain-facts explainability + optional quick-confirm + delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(
    txn: TransactionEntity,
    categoryName: String?,
    onQuickConfirm: ((String) -> Unit)?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = MoneyFormat.formatPaiseExact(txn.amountPaise),
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
            if (txn.type == dev.yashas.expensetracker.domain.model.TxnType.TRANSFER) {
                DetailLine("Transfer", "Excluded from spending")
            }
            DetailLine("Why this category?", if (txn.categoryKey == null) "You haven't tagged it yet" else "Matched your categories")
            if (onQuickConfirm != null) {
                Spacer(Modifier.height(6.dp))
                Text("Confirm as…", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_CHIPS.forEach { (key, label) ->
                        AssistChip(onClick = { onQuickConfirm(key) }, label = { Text(label) })
                    }
                }
            }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

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
