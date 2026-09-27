package dev.yashas.expensetracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.yashas.expensetracker.data.repo.HomeBreakdown
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.data.repo.UserPrefs
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.components.TxnRow
import dev.yashas.expensetracker.ui.components.categoryColor
import java.time.LocalTime

/** Default color for new tags: first honeycomb hue. */
private val DEFAULT_TAG_COLOR = dev.yashas.expensetracker.ui.components.HEX_CHOICES.first()

/** Series palette swatches (legacy tokens still recognized on old rows). */
private val TAG_COLORS = listOf(
    "series_violet" to "Violet",
    "series_cyan" to "Cyan",
    "series_amber" to "Amber",
    "series_pink" to "Pink",
    "series_lime" to "Lime",
    "series_blue" to "Blue",
)

/**
 * S6 Home dashboard, redesigned: greeting + avatar (rename), review banner BELOW the
 * hero, hero month number, tag proportion bar (multi-color divider + legend), recent 5.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repo: TxnRepository,
    onOpenReview: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { UserPrefs(context) }
    val vm: HomeViewModel = viewModel(factory = homeFactory(repo, prefs))
    val state by vm.state.collectAsState()

    var addTagOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // --- greeting + avatar (top, per redesign) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // avatar -> rename dialog until Settings (P6) lands
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .clickable { renameOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.userName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        // --- hero ---
        Column {
            Text(
                text = state.monthLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = MoneyFormat.formatRupeesWhole(state.monthNetPaise),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Spent this month · after refunds",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // --- tag proportion bar: one thin multi-color divider, no legend (details → Analytics) ---
        if (state.slices.isNotEmpty()) {
            ProportionBar(
                slices = HomeBreakdown.toDisplaySlices(state.slices),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
        }

        // --- twin stat squares: review queue + spending pace (replaces full-width banner) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatSquare(
                modifier = Modifier.weight(1f),
                onClick = onOpenReview,
                value = "${state.reviewCount}",
                label = "to confirm",
            )
            StatSquare(
                modifier = Modifier.weight(1f),
                onClick = null,
                value = MoneyFormat.formatRupeesWhole(state.avgDailyPaise),
                label = "avg / day · proj ${MoneyFormat.formatRupeesWhole(state.projectedPaise)}",
            )
        }

        // --- add-tag row + existing tags ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tags", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { addTagOpen = true }) { Text("+ New tag") }
        }
        // existing tags so the section never looks empty — ordered by spend to mirror the bar
        val chipKeys = HomeBreakdown.chipOrder(
            slices = state.slices,
            topKeys = state.categories.values.filter { it.parentId == null }.map { it.key },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chipKeys.forEach { key ->
                val cat = state.categories[key] ?: return@forEach
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(categoryColor(cat.colorToken), CircleShape),
                    )
                    Text(cat.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // --- recent ---
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
                        val cat = txn.categoryKey?.let { state.categories[it] }
                        TxnRow(
                            txn = txn,
                            categoryName = cat?.name,
                            categoryIconName = cat?.icon,
                            categoryColorToken = cat?.colorToken,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (addTagOpen) {
        AddTagDialog(
            onAdd = { name, color ->
                vm.createTag(name, color)
                addTagOpen = false
            },
            onDismiss = { addTagOpen = false },
        )
    }
    if (renameOpen) {
        RenameDialog(
            current = state.userName,
            onSave = {
                vm.renameUser(it)
                renameOpen = false
            },
            onDismiss = { renameOpen = false },
        )
    }
}

/** Twin stat square: big number + caption; optional tap. */
@Composable
private fun StatSquare(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(modifier = modifier, onClick = { onClick?.invoke() }) {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Multi-color divider: one rounded bar, segment widths = tag fractions. */
@Composable
private fun ProportionBar(
    slices: List<dev.yashas.expensetracker.data.repo.HomeBreakdown.Slice>,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val corner = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
        var x = 0f
        slices.forEach { slice ->
            val w = size.width * slice.fraction
            drawRoundRect(
                color = categoryColor(slice.colorToken),
                topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(w, size.height),
                cornerRadius = corner,
            )
            x += w
        }
    }
}

@Composable
private fun AddTagDialog(
    onAdd: (name: String, colorToken: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(DEFAULT_TAG_COLOR) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Tag name") },
                    singleLine = true,
                )
                Text("Color", style = MaterialTheme.typography.labelLarge)
                dev.yashas.expensetracker.ui.components.HexColorPicker(
                    selected = color,
                    onSelected = { color = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onAdd(name, color) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
