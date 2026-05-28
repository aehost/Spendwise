package com.spendwise.app.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.*
import com.spendwise.app.presentation.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month

@Composable
fun HomeScreen(onSettings: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(Background)) {
        when {
            state.isLoading -> LoadingState()
            state.dashboard == null && state.dashboardError != null -> ErrorState(state.dashboardError!!, vm::load)
            else -> DashboardContent(state, onSettings, vm::refresh)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = Primary, strokeWidth = 2.dp, modifier = Modifier.size(40.dp))
            Text("Loading your finances…", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Filled.WifiOff, "", tint = TextMuted, modifier = Modifier.size(48.dp))
            Text("Could not connect", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(error, fontSize = 13.sp, color = TextSecondary)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Retry") }
        }
    }
}

@Composable
private fun DashboardContent(state: HomeUiState, onSettings: () -> Unit, onRefresh: () -> Unit) {
    val dash       = state.dashboard
    val today      = LocalDate.now()
    val hourNow    = LocalTime.now().hour
    val greeting   = when {
        hourNow < 12 -> "Good morning"
        hourNow < 17 -> "Good afternoon"
        else         -> "Good evening"
    }
    val firstName  = state.userName?.split(" ")?.firstOrNull() ?: "there"
    val monthName  = Month.of(dash?.month ?: today.monthValue).name
        .lowercase().replaceFirstChar { it.uppercase() }

    // Core financial figures
    val salary     = dash?.salary?.amount ?: 0.0
    val spent      = dash?.totalSpent ?: 0.0
    val credit     = dash?.totalCredit ?: 0.0
    val netBal     = (salary + credit) - spent
    val savings    = dash?.savings?.toDouble() ?: (salary - spent)
    val savingsRate = dash?.savingsRate ?: 0
    val emiBurden  = dash?.emiBurdenPct ?: 0
    val spentPct   = if (salary > 0) (spent / salary).coerceIn(0.0, 1.0).toFloat() else 0f
    val daysLeft   = dash?.daysLeft ?: 0
    val projected  = dash?.projectedSpend ?: 0.0

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {

        // ── Header ───────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$greeting, $firstName", fontSize = 13.sp, color = TextSecondary)
                    Text("$monthName ${today.year}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.isRefreshing) CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    IconButton(onClick = onSettings, modifier = Modifier.size(44.dp).background(CardBg, CircleShape)) {
                        Icon(Icons.Filled.Settings, "Settings", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── Hero Balance Card ─────────────────────────────────────
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF3730A3)))).padding(20.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("NET BALANCE", fontSize = 10.sp, color = Color.White.copy(0.6f), letterSpacing = 1.5.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(netBal.formatCurrency(), fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                            // Savings rate badge
                            Column(horizontalAlignment = Alignment.End) {
                                Box(Modifier.background(Color.White.copy(0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("${savingsRate}% saved", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                                if (daysLeft > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("$daysLeft days left", fontSize = 11.sp, color = Color.White.copy(0.6f))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Monthly spending progress bar
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Spent ${spent.formatCurrency()} of ${salary.formatCurrency()}", fontSize = 12.sp, color = Color.White.copy(0.7f))
                                val overPct = if (projected > salary && salary > 0) "+${((projected - salary) / salary * 100).toInt()}%" else ""
                                if (overPct.isNotEmpty()) Text("⚠ $overPct projected", fontSize = 11.sp, color = Color(0xFFFBBF24))
                            }
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.15f))) {
                                Box(
                                    Modifier.fillMaxWidth(spentPct).height(6.dp).clip(RoundedCornerShape(50))
                                        .background(if (spentPct > 0.85f) Color(0xFFEF4444) else Color.White)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Income vs Spent pills
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            HeroPill("↑ Income", (salary + credit).formatCurrency(), Color(0xFF86EFAC), Modifier.weight(1f))
                            HeroPill("↓ Spent",  spent.formatCurrency(),             Color(0xFFFCA5A5), Modifier.weight(1f))
                            HeroPill("→ EMI",    (dash?.emiTotal ?: 0.0).formatCurrency(), Color(0xFFFDE68A), Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── Advisor Alerts (critical ones on home screen) ─────────
        if (state.topInsights.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.topInsights) { insight ->
                        val color = if (insight.priority == "critical") ErrorColor else WarningColor
                        Card(
                            modifier = Modifier.width(260.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = color.copy(0.1f))
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(insight.icon, fontSize = 20.sp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(insight.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(insight.metric + " " + insight.metricLabel, fontSize = 11.sp, color = color)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Quick Stats Strip ─────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { QuickStat("Bank Balance",   dash?.bankBalance?.formatCurrency() ?: "₹0",   Icons.Filled.AccountBalance, GradientTeal) }
                item { QuickStat("CC Outstanding", dash?.ccOutstanding?.formatCurrency() ?: "₹0", Icons.Filled.CreditCard,
                    if ((dash?.ccOutstanding ?: 0.0) > 0) GradientRose else GradientGreen) }
                item { QuickStat("EMI Burden",     "$emiBurden% of salary",                       Icons.Filled.Money,
                    if (emiBurden > 40) GradientRose else if (emiBurden > 30) GradientAmber else GradientGreen) }
                item { QuickStat("Burn Rate",      "${dash?.burnRate?.formatCurrency() ?: "₹0"}/day", Icons.Filled.Whatshot, GradientOrange) }
                item { QuickStat("Pending TXNs",   "${dash?.pendingCount ?: 0} items",            Icons.Filled.HourglassEmpty, GradientPurple) }
            }
        }

        // ── Spending Breakdown ────────────────────────────────────
        val topCats = dash?.byCategory?.sortedByDescending { it.total }?.take(6) ?: emptyList()
        val totalSpentRef = spent.takeIf { it > 0 } ?: 1.0
        if (topCats.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Spending Breakdown", Icons.Filled.PieChart, "${topCats.size} categories")
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        topCats.forEach { cat ->
                            val alert = dash?.budgetAlerts?.find { it.categorySlug == cat.categorySlug }
                            val pct   = (cat.total / totalSpentRef).coerceIn(0.0, 1.0).toFloat()
                            val budgetPct = alert?.pct ?: 0
                            CategorySpendRow(
                                label     = CATEGORY_META[cat.categorySlug]?.first  ?: "📦",
                                name      = CATEGORY_META[cat.categorySlug]?.second ?: cat.categorySlug,
                                amount    = cat.total,
                                sharePct  = pct,
                                budgetPct = budgetPct
                            )
                        }
                    }
                }
            }
        }

        // ── Bills This Month ──────────────────────────────────────
        if (state.bills.isNotEmpty()) {
            val unpaid = state.bills.filter { !it.paidThisMonth }
            val paid   = state.bills.filter { it.paidThisMonth }
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Bills", Icons.Filled.Receipt, "${paid.size}/${state.bills.size} paid")
                Spacer(Modifier.height(10.dp))
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        // Unpaid first
                        (unpaid + paid).take(6).forEachIndexed { i, bill ->
                            BillRow(bill)
                            if (i < minOf(state.bills.size, 6) - 1)
                                HorizontalDivider(color = BorderColor.copy(0.4f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                        if (state.bills.size > 6) {
                            TextButton(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                                Text("${state.bills.size - 6} more bills → Money tab", color = Primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Financial Goals ───────────────────────────────────────
        val activeGoals = state.goals.filter { !it.isCompleted }.take(3)
        if (activeGoals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Financial Goals", Icons.Filled.Flag, "${activeGoals.size} active")
                Spacer(Modifier.height(10.dp))
            }
            items(activeGoals) { goal ->
                GoalCard(goal, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        // ── Recent Transactions ───────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Recent Transactions", Icons.Filled.History, "${state.recentTransactions.size} shown")
            Spacer(Modifier.height(10.dp))
        }

        if (state.recentTransactions.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💳", fontSize = 32.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No transactions yet", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("SMS import detects bank messages automatically", fontSize = 12.sp, color = TextMuted, lineHeight = 18.sp)
                    }
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        state.recentTransactions.forEachIndexed { i, tx ->
                            TransactionRow(tx)
                            if (i < state.recentTransactions.size - 1)
                                HorizontalDivider(color = BorderColor.copy(0.4f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Composables ───────────────────────────────────────────────

@Composable
private fun HeroPill(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(Color.Black.copy(0.25f), RoundedCornerShape(12.dp)).padding(10.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.55f), letterSpacing = 0.5.sp)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickStat(label: String, value: String, icon: ImageVector, gradient: List<Color>) {
    Card(Modifier.width(140.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(gradient)).padding(14.dp)) {
            Column {
                Icon(icon, label, tint = Color.White.copy(0.85f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(8.dp))
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, fontSize = 10.sp, color = Color.White.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector, badge: String = "") {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, title, tint = Primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        if (badge.isNotEmpty()) Text(badge, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun CategorySpendRow(label: String, name: String, amount: Double, sharePct: Float, budgetPct: Int) {
    val barColor = when {
        budgetPct >= 100 -> ErrorColor
        budgetPct >= 80  -> WarningColor
        else             -> Primary.copy(0.8f)
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(name, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            if (budgetPct >= 80) {
                Box(Modifier.background(barColor.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("$budgetPct%", fontSize = 10.sp, color = barColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(amount.formatCurrency(), fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(BorderColor)) {
            Box(Modifier.fillMaxWidth(sharePct).height(4.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(barColor.copy(0.6f), barColor))))
        }
    }
}

@Composable
private fun BillRow(bill: BillDto) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).background(if (bill.paidThisMonth) SuccessColor.copy(0.12f) else Primary.copy(0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Text(bill.icon, fontSize = 16.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(bill.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("Due ${bill.dueDay}${ordinal(bill.dueDay)} every month", fontSize = 11.sp, color = TextMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(bill.amount.formatCurrency(), fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(if (bill.paidThisMonth) "✓ Paid" else "Unpaid", fontSize = 10.sp,
                color = if (bill.paidThisMonth) SuccessColor else WarningColor)
        }
    }
}

@Composable
private fun GoalCard(goal: FinancialGoalDto, modifier: Modifier = Modifier) {
    val pct = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat() else 0f
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(goal.icon, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(BorderColor)) {
                    Box(Modifier.fillMaxWidth(pct).height(5.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(GradientPurple)))
                }
                Spacer(Modifier.height(3.dp))
                Text("${goal.currentAmount.formatCurrency()} / ${goal.targetAmount.formatCurrency()}", fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Text("${(pct * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Primary)
        }
    }
}

@Composable
fun TransactionRow(tx: TransactionDto) {
    val meta = CATEGORY_META[tx.categorySlug]
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(if (tx.isCredit) SuccessColor.copy(0.12f) else Primary.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) { Text(meta?.first ?: "📦", fontSize = 16.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.merchant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(buildString {
                append(tx.transactionDate)
                if (tx.note.isNotBlank()) append(" · ${tx.note.take(25)}")
            }, fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (tx.isCredit) "+" else "−") + tx.amount.formatCurrency(),
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (tx.isCredit) SuccessColor else TextPrimary
            )
            if (tx.isPending) Text("Pending", fontSize = 10.sp, color = WarningColor)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────

private fun ordinal(day: Int): String = when {
    day in 11..13 -> "th"; day % 10 == 1 -> "st"; day % 10 == 2 -> "nd"; day % 10 == 3 -> "rd"; else -> "th"
}

// Emoji + display name per category slug
val CATEGORY_META = mapOf(
    "food"         to Pair("🍽️", "Food & Dining"),
    "transport"    to Pair("🚗", "Transport"),
    "shopping"     to Pair("🛒", "Shopping"),
    "bills"        to Pair("💡", "Bills"),
    "health"       to Pair("💊", "Health"),
    "entertainment" to Pair("🎬", "Entertainment"),
    "education"    to Pair("📚", "Education"),
    "salary"       to Pair("💰", "Salary"),
    "investment"   to Pair("📈", "Investment"),
    "transfer"     to Pair("🔄", "Transfer"),
    "emi"          to Pair("🏦", "EMI"),
    "income"       to Pair("💵", "Income"),
    "savings"      to Pair("🏦", "Savings"),
    "groceries"    to Pair("🛍️", "Groceries"),
    "fuel"         to Pair("⛽", "Fuel"),
    "travel"       to Pair("✈️", "Travel"),
    "rent"         to Pair("🏠", "Rent"),
    "insurance"    to Pair("🛡️", "Insurance"),
    "tax"          to Pair("📋", "Tax"),
    "other"        to Pair("📦", "Other"),
)
