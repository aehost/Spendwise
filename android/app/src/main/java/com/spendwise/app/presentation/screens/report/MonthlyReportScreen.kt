package com.spendwise.app.presentation.screens.report

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.MonthlyReportDto
import com.spendwise.app.presentation.theme.*
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    onBack: () -> Unit,
    vm: MonthlyReportViewModel = hiltViewModel()
) {
    val state   by vm.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Monthly Report", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) } },
                actions = {
                    if (state.report != null) {
                        IconButton(onClick = {
                            val text   = vm.buildShareText()
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                            context.startActivity(Intent.createChooser(intent, "Share Report"))
                        }) { Icon(Icons.Filled.Share, "Share", tint = Primary) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {

            // ── Month picker ─────────────────────────────────────
            item { MonthPicker(selectedMonth = state.selectedMonth, selectedYear = state.selectedYear, onSelect = { m, y -> vm.selectMonth(m, y) }) }

            if (state.isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) } }
                return@LazyColumn
            }

            state.error?.let { err ->
                item {
                    Card(Modifier.fillMaxWidth().padding(20.dp), colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(0.1f))) {
                        Text(err, color = ErrorColor, modifier = Modifier.padding(16.dp), fontSize = 13.sp)
                    }
                }
                return@LazyColumn
            }

            val report = state.report ?: return@LazyColumn

            // ── Summary card ─────────────────────────────────────
            item { SummaryCard(report) }

            // ── Health score ─────────────────────────────────────
            item { HealthScoreCard(report) }

            // ── Category breakdown ───────────────────────────────
            item { SectionHeader("📂 Category Breakdown") }
            items(report.categoryBreakdown.take(10)) { cat ->
                CategoryRow(
                    emoji      = categoryEmoji(cat.categorySlug),
                    name       = cat.categorySlug.replaceFirstChar { it.uppercase() },
                    amount     = cat.amount,
                    pct        = cat.pctOfTotal,
                    budgetPct  = cat.budgetPct,
                    status     = cat.budgetStatus
                )
            }

            // ── Top merchants ────────────────────────────────────
            if (report.topMerchants.isNotEmpty()) {
                item { SectionHeader("🏪 Top Merchants") }
                items(report.topMerchants.take(5)) { m ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("${m.visitCount} visits • avg ${m.avgAmount.formatCurrency()}", fontSize = 12.sp, color = TextMuted)
                            }
                            Text(m.totalSpent.formatCurrency(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }

            // ── Waste analysis ───────────────────────────────────
            item { WasteCard(report) }

            // ── Insights ─────────────────────────────────────────
            if (report.insights.isNotEmpty()) {
                item { SectionHeader("💡 Insights") }
                items(report.insights) { ins ->
                    val (bg, accent) = when (ins.type) {
                        "warning" -> WarningColor.copy(0.1f) to WarningColor
                        "alert"   -> ErrorColor.copy(0.1f)   to ErrorColor
                        "success" -> SuccessColor.copy(0.1f) to SuccessColor
                        else      -> Primary.copy(0.08f)     to Primary
                    }
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = bg)) {
                        Text(ins.message, color = accent, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // ── Anomalies ────────────────────────────────────────
            if (report.anomalies.isNotEmpty()) {
                item { SectionHeader("⚠️ Unusual Transactions") }
                items(report.anomalies.take(5)) { a ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp), colors = CardDefaults.cardColors(containerColor = WarningColor.copy(0.08f))) {
                        Row(Modifier.padding(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(a.merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text(a.reason, fontSize = 12.sp, color = WarningColor)
                                Text(a.date, fontSize = 11.sp, color = TextMuted)
                            }
                            Text(a.amount.formatCurrency(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WarningColor)
                        }
                    }
                }
            }

            // ── Cost Reduction Plan ───────────────────────────────
            item { CostReductionCard(report) }
        }
    }
}

@Composable
private fun MonthPicker(selectedMonth: Int, selectedYear: Int, onSelect: (Int, Int) -> Unit) {
    val now    = java.time.LocalDate.now()
    // Generate last 12 months
    val months = (0..11).map { offset ->
        val d = now.minusMonths(offset.toLong())
        Pair(d.monthValue, d.year)
    }.reversed()

    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(months) { (m, y) ->
            val selected = m == selectedMonth && y == selectedYear
            FilterChip(
                selected = selected,
                onClick  = { onSelect(m, y) },
                label    = {
                    Text(
                        "${Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault())} $y",
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor     = androidx.compose.ui.graphics.Color.White,
                    containerColor         = CardBg,
                    labelColor             = TextSecondary
                )
            )
        }
    }
}

@Composable
private fun SummaryCard(report: MonthlyReportDto) {
    val monthName = Month.of(report.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("$monthName ${report.year}", fontSize = 14.sp, color = TextMuted)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryItem("Income", report.summary.income.formatCurrency(), SuccessColor)
                SummaryItem("Spent", report.summary.totalSpent.formatCurrency(), ErrorColor)
                SummaryItem("Saved", report.summary.savings.formatCurrency(), if (report.summary.savings >= 0) SuccessColor else ErrorColor)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Savings Rate", fontSize = 11.sp, color = TextMuted); Text("${report.summary.savingsRate}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (report.summary.savingsRate >= 20) SuccessColor else WarningColor) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Transactions", fontSize = 11.sp, color = TextMuted); Text("${report.summary.transactionCount}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
                Column(horizontalAlignment = Alignment.End) {
                    Text("vs Last Month", fontSize = 11.sp, color = TextMuted)
                    val diff = report.summary.vsLastMonth
                    Text("${if (diff >= 0) "+" else ""}$diff%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (diff <= 0) SuccessColor else ErrorColor)
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = TextMuted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun HealthScoreCard(report: MonthlyReportDto) {
    val hs = report.healthScore
    val scoreColor = when {
        hs.score >= 80 -> SuccessColor
        hs.score >= 60 -> WarningColor
        else           -> ErrorColor
    }
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Score circle
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { hs.score / 100f }, modifier = Modifier.fillMaxSize(), color = scoreColor, strokeWidth = 6.dp, trackColor = scoreColor.copy(0.15f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${hs.score}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = scoreColor)
                    Text(hs.grade, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scoreColor)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Financial Health Score", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                hs.factors.take(3).forEach { f ->
                    val fColor = when (f.status) { "good" -> SuccessColor; "bad" -> ErrorColor; else -> WarningColor }
                    Text("${f.name}: ${f.detail}", fontSize = 11.sp, color = fColor)
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(emoji: String, name: String, amount: Double, pct: Int, budgetPct: Int?, status: String) {
    val statusColor = when (status) { "over" -> ErrorColor; "warning" -> WarningColor; else -> SuccessColor }
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(amount.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text("$pct%", fontSize = 12.sp, color = TextMuted, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            }
            if (budgetPct != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (budgetPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = statusColor,
                    trackColor = statusColor.copy(0.15f)
                )
                Text("${budgetPct}% of budget", fontSize = 10.sp, color = statusColor)
            }
        }
    }
}

@Composable
private fun WasteCard(report: MonthlyReportDto) {
    val w = report.wasteAnalysis
    if (w.totalWaste <= 0) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(0.08f)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🗑️", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Impulse / Waste Spending", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorColor)
                Text("${w.wasteTransactions} transactions tagged as waste", fontSize = 12.sp, color = TextMuted)
                w.topWasteCategory?.let { Text("Top: $it", fontSize = 12.sp, color = TextMuted) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(w.totalWaste.formatCurrency(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ErrorColor)
                Text("${w.wastePct}% of spend", fontSize = 11.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun CostReductionCard(report: MonthlyReportDto) {
    // Non-essential categories sorted by spend, only where budget is over or no budget set
    val nonEssential = setOf("food", "shopping", "entertainment", "transport", "travel", "streaming", "dining")
    val candidates = report.categoryBreakdown
        .filter { it.categorySlug.lowercase() in nonEssential && it.amount > 1000 }
        .sortedByDescending { it.amount }
        .take(4)

    if (candidates.isEmpty()) return

    val totalPotential = candidates.sumOf { it.amount * 0.20 }
    val savings = report.summary.savings
    val savingsRate = report.summary.savingsRate

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Cost Reduction Opportunities", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Save more to achieve your goals faster", fontSize = 11.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))

            candidates.forEach { cat ->
                val saving20 = cat.amount * 0.20
                val emoji = categoryEmoji(cat.categorySlug)
                val name  = cat.categorySlug.replaceFirstChar { it.uppercase() }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emoji, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 13.sp, color = TextPrimary)
                        Text("Spending: ${cat.amount.formatCurrency()}/mo", fontSize = 11.sp, color = TextMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Cut 20%", fontSize = 10.sp, color = WarningColor)
                        Text("Save ${saving20.formatCurrency()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderColor.copy(0.5f))
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Total potential savings", fontSize = 12.sp, color = TextSecondary)
                    Text(totalPotential.formatCurrency() + "/mo", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = SuccessColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val newRate = if (report.summary.income > 0)
                        ((savings + totalPotential) / report.summary.income * 100).toInt()
                    else savingsRate
                    Text("New savings rate", fontSize = 11.sp, color = TextMuted)
                    Text("~$newRate%", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (newRate >= 20) SuccessColor else WarningColor)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Go to Tools → Goals Planner to set targets and see exactly how fast you can reach them.",
                fontSize = 11.sp, color = Primary, lineHeight = 16.sp
            )
        }
    }
}
