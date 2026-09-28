package dev.yashas.expensetracker.ui.analytics

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yashas.expensetracker.data.repo.AnalyticsMath
import dev.yashas.expensetracker.data.repo.AnalyticsRepository
import dev.yashas.expensetracker.data.repo.TxnRepository
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.components.categoryColor
import dev.yashas.expensetracker.ui.theme.TextSecondary

private val PALETTE = listOf(
    "series_violet", "series_cyan", "series_amber", "series_pink", "series_lime", "series_blue",
)

/** "2026-09" → "Sep" (year appended only when the window spans multiple years). */
private fun monthLabel(yearMonth: String, multiYear: Boolean = false): String {
    val ym = java.time.YearMonth.parse(yearMonth)
    val m = ym.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
    return if (multiYear) "$m ${ym.year}" else m
}

/**
 * S12 Analytics v2 — glass observatory: hero summary, extruded income-vs-spend columns,
 * glow trend with breathing live dot, pseudo-3D tap-to-lift donut, merchant rank bars,
 * insight cards. Zero chart dependencies — every visual is custom Canvas.
 */
@Composable
fun AnalyticsScreen(
    repo: AnalyticsRepository,
    txnRepo: TxnRepository? = null,
) {
    val vm: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.factory(repo))
    val state by vm.state.collectAsState()
    val data = state.data

    // real tag colors for the donut/legend (falls back to rotating palette)
    val catColorTokens = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    LaunchedEffect(txnRepo) {
        if (txnRepo == null) return@LaunchedEffect
        txnRepo.observeCategories().collect { cats ->
            catColorTokens.clear()
            cats.forEach { catColorTokens[it.key] = it.colorToken }
        }
    }

    // one orchestrated page-load moment: sections fade/rise in sequence
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    @Composable
    fun sectionReveal(order: Int): Modifier = Modifier.graphicsLayer(
        alpha = ((appear.value - order * 0.12f) / 0.25f).coerceIn(0f, 1f),
        translationY = (1f - ((appear.value - order * 0.12f) / 0.25f).coerceIn(0f, 1f)) * 34f,
    )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            Spacer(Modifier.height(16.dp))
            return@Column
        }

        // hero summary
        val totalSpend = data.monthBars.lastOrNull()?.expensePaise ?: 0L
        GlassCard(modifier = Modifier.then(sectionReveal(0))) {
            Text("This period", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                MoneyFormat.formatRupeesWhole(data.categoryTotals.sumOf { it.amountPaise }),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${data.txnCount} transactions tracked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // C4 v2: income vs spending extruded columns
        if (data.monthBars.isNotEmpty()) {
            GlassCard(modifier = Modifier.then(sectionReveal(1))) {
                Text("Income vs spending", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TrendyBars(
                    bars = data.monthBars.map {
                        MonthBarUi(
                            label = monthLabel(it.yearMonth),
                            incomeRupees = it.incomePaise / 100f,
                            expenseRupees = it.expensePaise / 100f,
                        )
                    },
                )
            }
        }

        // C2 v2: glow trend
        if (data.dailyExpense.size >= 2) {
            GlassCard(modifier = Modifier.then(sectionReveal(2))) {
                Text("Spending trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                GlowTrend(valuesRupees = data.dailyExpense.map { it.amountPaise / 100f })
            }
        }

        // C3 v2: 3D donut + legend with real tag colors
        if (data.categoryTotals.isNotEmpty()) {
            GlassCard(modifier = Modifier.then(sectionReveal(3))) {
                Text("Where money goes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val sliceData = data.categoryTotals.mapIndexed { i, row ->
                    DonutSlice(
                        label = row.label.substringAfterLast('.').lowercase().replaceFirstChar { it.uppercase() },
                        amountPaise = row.amountPaise,
                        colorToken = catColorTokens[row.label] ?: PALETTE[i % PALETTE.size],
                    )
                }
                DonutChart(slices = sliceData)
                // legend rows with color dots
                sliceData.take(5).forEach { slice ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .height(9.dp)
                                .width(9.dp)
                                .background(categoryColor(slice.colorToken), CircleShape),
                        )
                        Text(slice.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            MoneyFormat.formatPaise(slice.amountPaise),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // top merchants with rank bars
        if (data.topMerchants.isNotEmpty()) {
            GlassCard(modifier = Modifier.then(sectionReveal(4))) {
                Text("Top merchants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val maxM = data.topMerchants.maxOf { it.amountPaise }.coerceAtLeast(1L)
                data.topMerchants.forEachIndexed { i, m ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${i + 1}. ${m.label}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                MoneyFormat.formatPaise(m.amountPaise),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        val frac = m.amountPaise.toFloat() / maxM
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .height(5.dp)
                                .background(
                                    categoryColor(PALETTE[i % PALETTE.size]),
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }

        // deterministic insights (04 §7 — no ML)
        if (data.txnCount > 0) {
            Text("Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            AnalyticsMath.insightCards(data.categoryTotals, data.monthBars, data.txnCount) { MoneyFormat.formatPaise(it) }
                .forEachIndexed { i, insight ->
                    GlassCard(modifier = Modifier.then(sectionReveal(5 + i))) {
                        Text(insight.headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            insight.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
        } else {
            GlassCard {
                Text(
                    "Charts wake up as transactions arrive — check back after your first week.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
