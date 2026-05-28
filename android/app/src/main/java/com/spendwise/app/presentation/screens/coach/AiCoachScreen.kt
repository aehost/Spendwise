package com.spendwise.app.presentation.screens.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.AdvisorContextDto
import com.spendwise.app.data.remote.dto.AdvisorInsightDto
import com.spendwise.app.presentation.theme.*

@Composable
fun AiCoachScreen(onBack: () -> Unit, vm: AiCoachViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        // Header
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(GradientPurple))
                .padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text("Financial Advisor", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        if (state.engineVersion.isNotEmpty()) "Engine v${state.engineVersion} • ${state.insights.size} insights"
                        else "Algorithm-powered • Updates instantly",
                        fontSize = 11.sp, color = Color.White.copy(0.75f)
                    )
                }
                IconButton(onClick = vm::load) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White)
                }
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Primary)
                    Text("Analysing your finances…", color = TextSecondary, fontSize = 13.sp)
                }
            }
            state.error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("⚠️", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(state.error ?: "", color = ErrorColor, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::load, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Retry") }
            }
            state.insights.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("All Clear!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                    Text("No critical issues found in your finances.", fontSize = 14.sp, color = TextSecondary)
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Context snapshot
                state.context?.let { ctx ->
                    item { ContextCard(ctx) }
                }

                // Priority sections
                val critical = state.insights.filter { it.priority == "critical" }
                val high     = state.insights.filter { it.priority == "high" }
                val medium   = state.insights.filter { it.priority == "medium" }
                val low      = state.insights.filter { it.priority == "low" }

                if (critical.isNotEmpty()) {
                    item { PriorityHeader("Needs Immediate Attention", ErrorColor) }
                    items(critical) { InsightCard(it) }
                }
                if (high.isNotEmpty()) {
                    item { PriorityHeader("High Priority", WarningColor) }
                    items(high) { InsightCard(it) }
                }
                if (medium.isNotEmpty()) {
                    item { PriorityHeader("Worth Reviewing", InfoColor) }
                    items(medium) { InsightCard(it) }
                }
                if (low.isNotEmpty()) {
                    item { PriorityHeader("Good News", SuccessColor) }
                    items(low) { InsightCard(it) }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun ContextCard(ctx: AdvisorContextDto) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Your Financial Snapshot", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnapStat("Savings Rate", "${ctx.savingsRate}%",
                    if (ctx.savingsRate >= 20) SuccessColor else if (ctx.savingsRate >= 10) WarningColor else ErrorColor,
                    Modifier.weight(1f))
                SnapStat("EMI Burden", "${ctx.emiBurden}%",
                    if (ctx.emiBurden <= 30) SuccessColor else if (ctx.emiBurden <= 40) WarningColor else ErrorColor,
                    Modifier.weight(1f))
                SnapStat("Emergency Fund", "${ctx.emergencyMonths}mo",
                    if (ctx.emergencyMonths >= 3) SuccessColor else if (ctx.emergencyMonths >= 1) WarningColor else ErrorColor,
                    Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SnapStat("Days Left", "${ctx.daysLeft}d",  Primary, Modifier.weight(1f))
                SnapStat("Projected", ctx.projectedSpend.formatCurrency(),
                    if (ctx.projectedSpend <= ctx.salary) SuccessColor else ErrorColor, Modifier.weight(1f))
                SnapStat("Spent", ctx.spent.formatCurrency(), TextSecondary, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SnapStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(color.copy(0.08f), RoundedCornerShape(10.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, fontSize = 9.sp, color = TextMuted, maxLines = 1)
    }
}

@Composable
private fun PriorityHeader(label: String, color: Color) {
    Row(
        Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun InsightCard(insight: AdvisorInsightDto) {
    val priorityColor = when (insight.priority) {
        "critical" -> ErrorColor
        "high"     -> WarningColor
        "medium"   -> InfoColor
        else       -> SuccessColor
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            Modifier.background(
                Brush.horizontalGradient(listOf(priorityColor.copy(0.12f), Color.Transparent))
            ).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(insight.icon, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(insight.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(insight.detail, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(insight.metric, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = priorityColor)
                    Text(insight.metricLabel, fontSize = 9.sp, color = TextMuted)
                }
            }
        }
    }
}
