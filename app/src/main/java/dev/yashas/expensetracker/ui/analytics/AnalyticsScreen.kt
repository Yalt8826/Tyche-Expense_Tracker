package dev.yashas.expensetracker.ui.analytics

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import dev.yashas.expensetracker.data.repo.AnalyticsMath
import dev.yashas.expensetracker.data.repo.AnalyticsRepository
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.components.categoryColor

/** Rotating series palette for donut slices (02-CHARTS: distinguishable set; final ramp in token doc). */
private val donutPalette = listOf(
    "series_violet", "series_cyan", "series_amber", "series_pink", "series_lime", "series_blue",
)

/**
 * S12 Analytics dashboard (v1 core): period selector, income-vs-expense hero bars (C4,
 * Vico), spending trend line (C2, Vico), interactive donut (C3, custom Canvas),
 * top merchants, deterministic insight cards. Heatmap + Story are v1.1.
 */
@Composable
fun AnalyticsScreen(repo: AnalyticsRepository) {
    val vm: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.factory(repo))
    val state by vm.state.collectAsState()
    val data = state.data

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Analytics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // period selector (axis labels never animate — 03-ANIMATION)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsMath.Period.entries.forEach { p ->
                FilterChip(
                    selected = state.period == p,
                    onClick = { vm.setPeriod(p) },
                    label = { Text(p.label) },
                )
            }
        }

        if (data == null) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        // C4 hero bars: income vs expense per month (animated race comes with P6 polish)
        if (data.monthBars.isNotEmpty()) {
            Text("Income vs spending", style = MaterialTheme.typography.titleMedium)
            val producer = remember { CartesianChartModelProducer() }
            LaunchedEffect(data.monthBars) {
                producer.runTransaction {
                    columnModel {
                        series(data.monthBars.map { it.incomePaise.toFloat() / 100f })
                        series(data.monthBars.map { it.expensePaise.toFloat() / 100f })
                    }
                }
            }
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberColumnCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = producer,
            )
        }

        // C2 spending trend line
        if (data.dailyExpense.size >= 2) {
            Text("Spending trend", style = MaterialTheme.typography.titleMedium)
            val producer = remember { CartesianChartModelProducer() }
            LaunchedEffect(data.dailyExpense) {
                producer.runTransaction {
                    lineModel { series(data.dailyExpense.map { it.amountPaise.toFloat() / 100f }) }
                }
            }
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(),
                    startAxis = VerticalAxis.rememberStart(),
                    bottomAxis = HorizontalAxis.rememberBottom(),
                ),
                modelProducer = producer,
            )
        }

        // C3 interactive donut (custom Canvas); slices sorted desc by amount already
        if (data.categoryTotals.isNotEmpty()) {
            Text("Where money goes", style = MaterialTheme.typography.titleMedium)
            val sliceData = data.categoryTotals.mapIndexed { i, row ->
                DonutSlice(
                    label = row.label.substringAfterLast('.').lowercase().replaceFirstChar { it.uppercase() },
                    amountPaise = row.amountPaise,
                    colorToken = donutPalette[i % donutPalette.size],
                )
            }
            DonutChart(slices = sliceData)
            sliceData.take(5).forEach { slice ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("● ", color = categoryColor(slice.colorToken), style = MaterialTheme.typography.bodyMedium)
                    Text(slice.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        MoneyFormat.formatPaise(slice.amountPaise),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // top merchants
        if (data.topMerchants.isNotEmpty()) {
            Text("Top merchants", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.topMerchants.forEach { m ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${MoneyFormat.formatPaise(m.amountPaise)} · ${m.txnCount}×",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        // deterministic insights (04 §7 — no ML)
        if (data.txnCount > 0) {
            Text("Insights", style = MaterialTheme.typography.titleMedium)
            AnalyticsMath.insightCards(data.categoryTotals, data.monthBars, data.txnCount) { MoneyFormat.formatPaise(it) }
                .forEach { insight ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(insight.headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                insight.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Charts wake up as transactions arrive — check back after your first week.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
