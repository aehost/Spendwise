package com.spendwise.app.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.spendwise.app.core.Constants
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.DashboardDto
import com.spendwise.app.data.remote.dto.TransactionDto
import com.spendwise.app.presentation.theme.*

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(Background)) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("⚠️ Could not load data", color = ErrorColor, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Text(state.error ?: "", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = vm::load, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Retry") }
            }
            else -> HomeContent(state.dashboard, state.recentTransactions, onSettings)
        }
    }
}

@Composable
private fun HomeContent(dash: DashboardDto?, recent: List<TransactionDto>, onSettings: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Header ─────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SpendWise", fontSize = 13.sp, color = TextSecondary)
                    val month = java.time.Month.of(dash?.month ?: java.time.LocalDate.now().monthValue).name.lowercase().replaceFirstChar { it.uppercase() }
                    Text("$month Overview", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                IconButton(onClick = onSettings,
                    modifier = Modifier.background(CardBg, CircleShape)) {
                    Icon(Icons.Filled.Settings, "Settings", tint = TextSecondary)
                }
            }
        }

        // ── Balance Card ───────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(Color(0xFF6C63FF), Color(0xFF8B5CF6)))
                    ).padding(20.dp)
                ) {
                    Column {
                        Text("SALARY BALANCE", fontSize = 11.sp, color = Color.White.copy(0.7f), letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        val balance = (dash?.salary?.amount ?: 0.0) - (dash?.totalSpent ?: 0.0)
                        Text(balance.formatCurrency(), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Spent: ${dash?.totalSpent?.formatCurrency() ?: "₹0"}  •  Salary: ${dash?.salary?.amount?.formatCurrency() ?: "₹0"}",
                            fontSize = 13.sp, color = Color.White.copy(0.75f))
                    }
                }
            }
        }

        // ── Stats Row ──────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatMiniCard(Modifier.weight(1f), "Bank Balance", dash?.bankBalance?.formatCurrency() ?: "₹0", Color(0xFF10B981))
                StatMiniCard(Modifier.weight(1f), "EMI Burden",   "${dash?.emiBurdenPct ?: 0}%", if ((dash?.emiBurdenPct ?: 0) > 40) ErrorColor else WarningColor)
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatMiniCard(Modifier.weight(1f), "Savings Rate",  "${dash?.savingsRate ?: 0}%", if ((dash?.savingsRate ?: 0) >= 20) SuccessColor else WarningColor)
                StatMiniCard(Modifier.weight(1f), "Burn Rate",     "${dash?.burnRate?.formatCurrency() ?: "₹0"}/day", Primary)
            }
        }

        // ── Credit Card Outstanding ────────────────────────────
        if ((dash?.ccOutstanding ?: 0.0) > 0) {
            item {
                Spacer(Modifier.height(12.dp))
                InfoBanner(
                    "💳 Credit Outstanding",
                    dash?.ccOutstanding?.formatCurrency() ?: "",
                    Secondary
                )
            }
        }

        // ── Budget Alerts ──────────────────────────────────────
        if (!dash?.budgetAlerts.isNullOrEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("⚠️ Budget Alerts", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WarningColor, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(8.dp))
            }
            dash?.budgetAlerts?.forEach { alert ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(Constants.CATEGORY_ICONS[alert.categorySlug] + " " + (Constants.CATEGORY_LABELS[alert.categorySlug] ?: alert.categorySlug),
                                    fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("${alert.spent.formatCurrency()} / ${alert.budget.formatCurrency()}", fontSize = 12.sp, color = TextSecondary)
                            }
                            Text("${alert.pct}%", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (alert.pct >= 100) ErrorColor else WarningColor)
                        }
                    }
                }
            }
        }

        // ── Recent Transactions ────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            Text("Recent Transactions", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(8.dp))
        }

        if (recent.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Text("No transactions yet. SMS import will run automatically when bank messages arrive.",
                        fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(16.dp), lineHeight = 20.sp)
                }
            }
        }

        recent.forEach { tx ->
            item { TransactionRow(tx) }
        }
    }
}

@Composable
fun StatMiniCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun InfoBanner(label: String, value: String, color: Color) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun TransactionRow(tx: TransactionDto) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (tx.isCredit) SuccessColor.copy(0.15f) else ErrorColor.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(Constants.CATEGORY_ICONS[tx.categorySlug] ?: "📦", fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.merchant, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(tx.transactionDate, fontSize = 11.sp, color = TextMuted)
            }
            Text(
                (if (tx.isCredit) "+" else "-") + tx.amount.formatCurrency(),
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (tx.isCredit) SuccessColor else ErrorColor
            )
        }
    }
}
