package dev.yashas.expensetracker.ui.budgets

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yashas.expensetracker.data.repo.BudgetMath
import dev.yashas.expensetracker.data.repo.BudgetMath.State
import dev.yashas.expensetracker.data.repo.BudgetRepository
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.components.categoryColor
import dev.yashas.expensetracker.ui.theme.SeriesAmber
import dev.yashas.expensetracker.ui.theme.SemanticCoral
import dev.yashas.expensetracker.ui.theme.SemanticGreen

/**
 * S15 Budget overview: hero dual-progress radial (spend fraction vs calendar fraction,
 * custom Canvas) + category cards with icon+text state (never color alone). Ring sweep
 * + haptic tick on state transition land with P6 polish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(repo: BudgetRepository, categories: List<dev.yashas.expensetracker.data.db.entity.CategoryEntity>) {
    val vm: BudgetViewModel = viewModel(factory = BudgetViewModel.factory(repo))
    val state by vm.state.collectAsState()
    var editorFor by remember { mutableStateOf<String?>(null) } // null = overall
    var editorOpen by remember { mutableStateOf(false) }

    val data = state.data

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text("Budgets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), onClick = { editorFor = null; editorOpen = true }) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val overall = data?.overall
                    Box(contentAlignment = Alignment.Center) {
                        RingGauge(
                            spendFraction = overall?.spendFraction ?: 0f,
                            monthFraction = overall?.monthFraction ?: 0f,
                            color = when (overall?.state) {
                                State.EXCEEDED -> SemanticCoral
                                State.APPROACHING -> SeriesAmber
                                else -> SemanticGreen
                            },
                            modifier = Modifier.size(160.dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                overall?.let { "${(it.spendFraction * 100).toInt()}%" } ?: "—",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                overall?.let { "Month ${(it.monthFraction * 100).toInt()}%" } ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        overall?.verdict ?: "Loading…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        overall?.let {
                            "${MoneyFormat.formatPaise(it.spentPaise)} spent" +
                                (it.limitPaise?.let { l -> " of ${MoneyFormat.formatPaise(l)} · projected ${MoneyFormat.formatPaise(it.projectedEndPaise)}" } ?: "")
                        } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Tap to set overall budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        val cats = data?.categories.orEmpty()
        if (cats.isNotEmpty()) {
            item { Text("Category budgets", style = MaterialTheme.typography.titleMedium) }
            items(cats, key = { it.categoryKey }) { cs ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { editorFor = cs.categoryKey; editorOpen = true }) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(cs.categoryKey, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                when (cs.state) {
                                    State.EXCEEDED -> "Over budget"
                                    State.APPROACHING -> "Almost there"
                                    State.HEALTHY -> "On track"
                                },
                                color = when (cs.state) {
                                    State.EXCEEDED -> SemanticCoral
                                    State.APPROACHING -> SeriesAmber
                                    State.HEALTHY -> SemanticGreen
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                MoneyFormat.formatPaise(cs.spentPaise),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "of ${MoneyFormat.formatPaise(cs.limitPaise)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (editorOpen) {
        BudgetEditorSheet(
            categoryKey = editorFor,
            categoryName = editorFor?.let { key -> categories.firstOrNull { it.key == key }?.name } ?: "Overall",
            onSave = { rupees ->
                vm.saveBudget(editorFor, rupees)
                editorOpen = false
            },
            onDismiss = { editorOpen = false },
        )
    }
}

/** C6 radial gauge: sweep + pace tick; sweep animation + haptic tick arrive in P6. */
@Composable
private fun RingGauge(
    spendFraction: Float,
    monthFraction: Float,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round)
        val inset = stroke.width / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
        // track
        drawArc(
            color = androidx.compose.ui.graphics.Color(0x22FFFFFF),
            startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = stroke,
        )
        // pace tick (calendar progress)
        drawArc(
            color = androidx.compose.ui.graphics.Color(0x66FFFFFF),
            startAngle = -90f, sweepAngle = 360f * monthFraction.coerceIn(0f, 1f), useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = stroke,
        )
        // spend arc
        drawArc(
            color = color,
            startAngle = -90f, sweepAngle = 360f * spendFraction.coerceIn(0f, 1f), useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = arcSize, style = stroke,
        )
    }
}

/** S16 budget editor: amount + category picker (rollover/create-rule hooks are v1.1+). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditorSheet(
    categoryKey: String?,
    categoryName: String,
    onSave: (rupees: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Budget for $categoryName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { s -> amountText = s.filter(Char::isDigit).take(8) },
                label = { Text("Monthly limit in rupees") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { amountText.toLongOrNull()?.let(onSave) },
                enabled = (amountText.toLongOrNull() ?: 0L) > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save budget") }
            if (categoryKey == null) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
