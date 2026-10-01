package dev.yashas.expensetracker.ui.budgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.yashas.expensetracker.data.repo.BudgetMath
import dev.yashas.expensetracker.data.repo.BudgetMath.State
import dev.yashas.expensetracker.data.repo.BudgetRepository
import dev.yashas.expensetracker.domain.model.MoneyFormat
import dev.yashas.expensetracker.ui.analytics.GlassCard
import dev.yashas.expensetracker.ui.components.categoryColor
import dev.yashas.expensetracker.ui.components.categoryIcon
import dev.yashas.expensetracker.ui.theme.SeriesAmber
import dev.yashas.expensetracker.ui.theme.SemanticCoral
import dev.yashas.expensetracker.ui.theme.SemanticGreen
import java.time.LocalDate

/** Editor target: overall (null), an existing category key, or a brand-new category. */
private sealed interface EditorTarget {
    data object Overall : EditorTarget
    data class Existing(val key: String) : EditorTarget
    data object NewCategory : EditorTarget
}

/**
 * S15 Budgets v2: hero pace ring + the daily-burn chart (per-day spend vs fair-share
 * line, today marker) + glass category cards. Category budgets are fully managed:
 * add (pick from unbudgeted), edit, or delete, all from the editor sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetScreen(repo: BudgetRepository, categories: List<dev.yashas.expensetracker.data.db.entity.CategoryEntity>) {
    val vm: BudgetViewModel = viewModel(factory = BudgetViewModel.factory(repo))
    val state by vm.state.collectAsState()
    var editor by remember { mutableStateOf<EditorTarget?>(null) }
    val data = state.data

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text("Budgets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // ── hero: pace ring + verdict ──
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
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
                            modifier = Modifier.size(150.dp),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // R4: beyond 2× show "Nx of budget" — "388%" reads like a grade
                            Text(
                                overall?.let {
                                    if (it.spendFraction >= 2f) "${it.spendFraction.toInt()}× of budget" else "${(it.spendFraction * 100).toInt()}%"
                                } ?: "—",
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
                    Text(overall?.verdict ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        overall?.let {
                            "${MoneyFormat.formatRupeesWhole(it.spentPaise)} spent" +
                                (it.limitPaise?.let { l -> " of ${MoneyFormat.formatRupeesWhole(l)} · projected ${MoneyFormat.formatRupeesWhole(it.projectedEndPaise)}" } ?: "")
                        } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        overall?.limitPaise?.let { "Tap to edit overall budget" } ?: "Tap to set overall budget",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── new graph: daily burn ──
        item {
            val d = data
            if (d != null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Daily burn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    DailyBurnChart(
                        dailySpend = d.dailySpend,
                        daysInMonth = d.daysInMonth,
                        limitPaise = d.overall.limitPaise,
                    )
                    val today = LocalDate.now()
                    val todayPaise = d.dailySpend.firstOrNull { it.first == today.toEpochDay() }?.second ?: 0L
                    val fairShare = d.overall.limitPaise?.let { it / d.daysInMonth }
                    Text(
                        buildString {
                            if (fairShare != null) append("Fair share ${MoneyFormat.formatRupeesWhole(fairShare)}/day · ")
                            append("today ${MoneyFormat.formatRupeesWhole(todayPaise)}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── category budgets ──
        val cats = data?.categories.orEmpty()
        if (cats.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Category budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { editor = EditorTarget.NewCategory }) { Text("+ Add") }
                }
            }
            items(cats, key = { it.categoryKey }) { cs ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val cat = categories.firstOrNull { it.key == cs.categoryKey }
                                if (cat != null) {
                                    Icon(
                                        imageVector = categoryIcon(cat.icon),
                                        contentDescription = cat.name,
                                        tint = categoryColor(cat.colorToken),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    cat?.name ?: cs.categoryKey,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            // tap the state pill to edit; long-press-free edit affordance
                            TextButton(onClick = { editor = EditorTarget.Existing(cs.categoryKey) }) {
                                Text(
                                    when (cs.state) {
                                        State.EXCEEDED -> "Over · edit"
                                        State.APPROACHING -> "Almost · edit"
                                        State.HEALTHY -> "On track · edit"
                                    },
                                    color = when (cs.state) {
                                        State.EXCEEDED -> SemanticCoral
                                        State.APPROACHING -> SeriesAmber
                                        State.HEALTHY -> SemanticGreen
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        BudgetBar(fraction = cs.fraction, state = cs.state)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                MoneyFormat.formatRupeesWhole(cs.spentPaise),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "of ${MoneyFormat.formatRupeesWhole(cs.limitPaise)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("No category budgets yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Set monthly limits per tag to see pace and projections here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = { editor = EditorTarget.NewCategory },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Add your first category budget") }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) } // R7: FAB clearance
    }

    editor?.let { target ->
        BudgetEditorSheet(
            target = target,
            categories = categories,
            existingLimitRupees = when (target) {
                is EditorTarget.Existing ->
                    data?.categories?.firstOrNull { it.categoryKey == target.key }?.limitPaise?.div(100)
                else -> null
            },
            unbudgeted = { vm.unbudgetedCategories() },
            onSave = { key, rupees ->
                vm.saveBudget(key, rupees)
                editor = null
            },
            onDelete = { key ->
                vm.deleteBudget(key)
                editor = null
            },
            onDismiss = { editor = null },
        )
    }
}

/**
 * Daily burn: one rounded bar per day of the month (zero days = nub), colored by
 * pace against the fair share, dashed fair-share line, today dot, entrance rise.
 */
@Composable
private fun DailyBurnChart(
    dailySpend: List<Pair<Long, Long>>,
    daysInMonth: Int,
    limitPaise: Long?,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(dailySpend) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val today = LocalDate.now()
    val byDay = remember(dailySpend) { dailySpend.toMap() }
    val fairPaise = limitPaise?.div(daysInMonth)
    val axisLabel = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val labelPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
    }
    LaunchedEffect(axisLabel) { labelPaint.color = axisLabel.toArgb() }

    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        val maxPaise = maxOf(
            byDay.values.maxOrNull() ?: 1L,
            fairPaise ?: 0L,
        ).coerceAtLeast(1L)
        val chartH = size.height * 0.80f
        val baseY = size.height * 0.88f
        val slot = size.width / daysInMonth
        val barW = (slot * 0.62f).coerceAtMost(14.dp.toPx())
        labelPaint.textSize = 9.sp.toPx()

        // bars (0-spend days get a 2px nub so the month's shape reads at a glance)
        for (day in 1..daysInMonth) {
            val epoch = today.withDayOfMonth(day).toEpochDay()
            val paise = byDay[epoch] ?: 0L
            val h = (paise.toFloat() / maxPaise) * chartH * progress.value
            val cx = slot * (day - 0.5f)
            val isToday = day == today.dayOfMonth
            val isFuture = day > today.dayOfMonth
            if (isFuture) continue
            val color = when {
                fairPaise == null -> SeriesAmber
                paise > fairPaise * 3 / 2 -> SemanticCoral
                paise > fairPaise -> SeriesAmber
                paise > 0L -> SemanticGreen
                else -> trackColor.copy(alpha = 0.5f)
            }
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.85f), color),
                    startY = baseY - h,
                    endY = baseY,
                ),
                topLeft = Offset(cx - barW / 2f, baseY - h.coerceAtLeast(if (paise > 0) 2f else 1.5f)),
                size = Size(barW, h.coerceAtLeast(if (paise > 0) 2f else 1.5f)),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
            if (isToday) {
                drawCircle(Color.White, radius = 2.6f, center = Offset(cx, baseY - h - 6f))
            }
        }

        // fair-share dashed line
        if (fairPaise != null && fairPaise > 0L) {
            val y = baseY - (fairPaise.toFloat() / maxPaise) * chartH * progress.value
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f, 9f)),
            )
        }

        // baseline + day labels (1 · 10 · 20 · 28)
        drawLine(
            color = trackColor.copy(alpha = 0.4f),
            start = Offset(0f, baseY),
            end = Offset(size.width, baseY),
            strokeWidth = 1.2f,
        )
        for (day in listOf(1, 10, 20, daysInMonth)) {
            if (day > today.dayOfMonth && day != 1) continue
            val cx = slot * (day - 0.5f)
            drawContext.canvas.nativeCanvas.drawText(day.toString(), cx, baseY + 15.sp.toPx(), labelPaint)
        }
    }
}

/** R4 thin progress bar; color encodes state, text pairs with it. */
@Composable
private fun BudgetBar(fraction: Float, state: State) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(
            color = Color(0x22FFFFFF),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = when (state) {
                State.EXCEEDED -> SemanticCoral
                State.APPROACHING -> SeriesAmber
                State.HEALTHY -> SemanticGreen
            },
            size = Size(width = size.width * fraction.coerceIn(0f, 1f) * progress.value, height = size.height),
            cornerRadius = CornerRadius(6f, 6f),
        )
    }
}

/** C6 v2 radial: rounded caps, gradient spend arc, pace tick underneath. */
@Composable
private fun RingGauge(
    spendFraction: Float,
    monthFraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(spendFraction) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    Canvas(modifier = modifier) {
        val strokeW = size.minDimension * 0.085f
        val stroke = Stroke(width = strokeW, cap = StrokeCap.Round)
        val inset = strokeW / 2
        val arcSize = Size(size.width - strokeW, size.height - strokeW)
        val topLeft = Offset(inset, inset)
        // track
        drawArc(
            color = Color(0x22FFFFFF),
            startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = stroke,
        )
        // pace tick (calendar progress)
        drawArc(
            color = Color(0x66FFFFFF),
            startAngle = -90f, sweepAngle = 360f * monthFraction.coerceIn(0f, 1f), useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(width = strokeW * 0.45f, cap = StrokeCap.Round),
        )
        // spend arc with a light gradient
        drawArc(
            brush = Brush.linearGradient(listOf(color.copy(alpha = 0.75f), color)),
            startAngle = -90f, sweepAngle = 360f * spendFraction.coerceIn(0f, 1f) * sweep.value, useCenter = false,
            topLeft = topLeft, size = arcSize, style = stroke,
        )
    }
}

/**
 * S16 v2 editor: amount (+ category picker when adding new) + Save; existing
 * category budgets also expose Delete. rupees in, paise stored.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BudgetEditorSheet(
    target: EditorTarget,
    categories: List<dev.yashas.expensetracker.data.db.entity.CategoryEntity>,
    existingLimitRupees: Long?,
    unbudgeted: suspend () -> List<dev.yashas.expensetracker.data.db.entity.CategoryEntity>,
    onSave: (categoryKey: String?, rupees: Long) -> Unit,
    onDelete: (categoryKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by remember(target) { mutableStateOf(existingLimitRupees?.toString() ?: "") }
    var pickedKey by remember(target) { mutableStateOf<String?>(null) }
    var unbudgetedCats by remember(target) { mutableStateOf<List<dev.yashas.expensetracker.data.db.entity.CategoryEntity>>(emptyList()) }

    LaunchedEffect(target) {
        if (target is EditorTarget.NewCategory) {
            unbudgetedCats = unbudgeted()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            val title = when (target) {
                EditorTarget.Overall -> "Overall monthly budget"
                is EditorTarget.Existing -> {
                    val name = categories.firstOrNull { it.key == target.key }?.name ?: target.key
                    "Budget for $name"
                }
                EditorTarget.NewCategory -> "New category budget"
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))

            if (target is EditorTarget.NewCategory) {
                if (unbudgetedCats.isEmpty()) {
                    Text(
                        "Every tag already has a budget — edit or delete one instead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Pick a tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        unbudgetedCats.forEach { cat ->
                            FilterChip(
                                selected = pickedKey == cat.key,
                                onClick = { pickedKey = cat.key },
                                label = { Text(cat.name) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = categoryIcon(cat.icon),
                                        contentDescription = null,
                                        tint = categoryColor(cat.colorToken),
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { s -> amountText = s.filter(Char::isDigit).take(8) },
                label = { Text("Monthly limit in rupees") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            val key: String? = when (target) {
                EditorTarget.Overall -> null
                is EditorTarget.Existing -> target.key
                EditorTarget.NewCategory -> pickedKey
            }
            val canSave = (amountText.toLongOrNull() ?: 0L) > 0 &&
                (target !is EditorTarget.NewCategory || pickedKey != null)
            Button(
                onClick = { amountText.toLongOrNull()?.let { onSave(key, it) } },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (target is EditorTarget.Existing) "Update budget" else "Save budget")
            }
            if (target is EditorTarget.Existing) {
                TextButton(
                    onClick = { onDelete(target.key) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete budget", color = MaterialTheme.colorScheme.error) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
