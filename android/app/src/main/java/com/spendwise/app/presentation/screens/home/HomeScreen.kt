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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.Constants
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.BillDto
import com.spendwise.app.data.remote.dto.DashboardDto
import com.spendwise.app.data.remote.dto.FinancialGoalDto
import com.spendwise.app.data.remote.dto.TransactionDto
import com.spendwise.app.presentation.theme.*
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    onSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(Background)) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
                    Text("Loading your financial summary…", color = TextSecondary, fontSize = 13.sp)
                }
            }
            state.error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("⚠️", fontSize = 40.sp)
                Spacer(Modifier.height(12.dp))
                Text("Could not load data", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(state.error ?: "", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = vm::load, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Retry") }
            }
            else -> DashboardContent(state, onSettings, vm::load)
        }
    }
}

@Composable
private fun DashboardContent(state: HomeUiState, onSettings: () -> Unit, onRefresh: () -> Unit) {
    val dash  = state.dashboard
    val today = LocalDate.now()
    val greeting = when (LocalTime.now().hour) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        else      -> "Good evening"
    }
    val firstName = state.userName?.split(" ")?.firstOrNull() ?: "there"
    val monthName = java.time.Month.of(dash?.month ?: today.monthValue)
        .name.lowercase().replaceFirstChar { it.uppercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {

        // ── Greeting header ────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$greeting, $firstName! 👋", fontSize = 13.sp, color = TextSecondary)
                    Text("$monthName Overview", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(44.dp).background(CardBg, CircleShape)
                ) {
                    Icon(Icons.Filled.Settings, "Settings", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ── Hero balance card ──────────────────────────────────
        item {
            Spacer(Modifier.height(8.dp))
            val spent   = dash?.totalSpent ?: 0.0
            val salary  = dash?.salary?.amount ?: 0.0
            val credit  = dash?.totalCredit ?: 0.0
            val balance = (salary + credit) - spent
            val savingsRate = dash?.savingsRate ?: 0

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    Modifier.fillMaxWidth().background(
                        Brush.linearGradient(listOf(Color(0xFF5B3FE8), Color(0xFF7C5CFC), Color(0xFF9B6BFF)))
                    ).padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NET BALANCE", fontSize = 11.sp, color = Color.White.copy(0.65f), letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                            // Savings rate badge
                            if (savingsRate > 0) {
                                Box(
                                    Modifier.background(Color.White.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("${savingsRate}% saved", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            balance.formatCurrency(),
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HeroStat(
                                Modifier.weight(1f), "Income",
                                (salary + credit).formatCurrency(),
                                Color(0xFF7DFFC3), Icons.Filled.TrendingUp
                            )
                            HeroStat(
                                Modifier.weight(1f), "Spent",
                                spent.formatCurrency(),
                                Color(0xFFFFB3B3), Icons.Filled.TrendingDown
                            )
                        }
                    }
                }
            }
        }

        // ── 3-stat quick row ───────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    QuickStatCard(
                        label = "Bank Balance",
                        value = dash?.bankBalance?.formatCurrency() ?: "₹0",
                        icon  = Icons.Filled.AccountBalance,
                        gradient = GradientTeal
                    )
                }
                item {
                    QuickStatCard(
                        label = "EMI Burden",
                        value = "${dash?.emiBurdenPct ?: 0}%",
                        icon  = Icons.Filled.MoneyOff,
                        gradient = if ((dash?.emiBurdenPct ?: 0) > 40) GradientPink else GradientGreen
                    )
                }
                item {
                    QuickStatCard(
                        label = "CC Outstanding",
                        value = dash?.ccOutstanding?.formatCurrency() ?: "₹0",
                        icon  = Icons.Filled.CreditCard,
                        gradient = if ((dash?.ccOutstanding ?: 0.0) > 0) GradientAmber else GradientGreen
                    )
                }
                item {
                    QuickStatCard(
                        label = "Burn Rate",
                        value = "${dash?.burnRate?.formatCurrency() ?: "₹0"}/day",
                        icon  = Icons.Filled.Whatshot,
                        gradient = GradientPurple
                    )
                }
            }
        }

        // ── Spending by category ───────────────────────────────
        val topCategories = dash?.byCategory?.sortedByDescending { it.total }?.take(5) ?: emptyList()
        val totalSpent    = dash?.totalSpent?.takeIf { it > 0 } ?: 1.0
        if (topCategories.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Spending Breakdown", Icons.Filled.PieChart)
                Spacer(Modifier.height(10.dp))
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        topCategories.forEach { cat ->
                            val pct   = (cat.total / totalSpent).coerceIn(0.0, 1.0).toFloat()
                            val label = Constants.CATEGORY_LABELS[cat.categorySlug] ?: cat.categorySlug
                            val icon  = Constants.CATEGORY_ICONS[cat.categorySlug] ?: "📦"
                            val budgetAlert = dash?.budgetAlerts?.find { it.categorySlug == cat.categorySlug }
                            CategoryBar(icon, label, cat.total, pct, budgetAlert?.pct)
                        }
                    }
                }
            }
        }

        // ── Budget alerts ──────────────────────────────────────
        val alerts = dash?.budgetAlerts?.filter { it.pct >= 80 } ?: emptyList()
        if (alerts.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Budget Alerts", Icons.Filled.Warning, WarningColor)
                Spacer(Modifier.height(10.dp))
            }
            alerts.forEach { alert ->
                item {
                    BudgetAlertCard(alert.categorySlug, alert.spent, alert.budget, alert.pct)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        // ── Bills due this month ───────────────────────────────
        val unpaidBills = state.bills.filter { !it.paidThisMonth }.sortedBy { it.dueDay }
        val paidBills   = state.bills.filter { it.paidThisMonth }
        if (state.bills.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader(
                    "Bills This Month",
                    Icons.Filled.Receipt,
                    label2 = "${paidBills.size}/${state.bills.size} paid"
                )
                Spacer(Modifier.height(10.dp))
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        state.bills.take(5).forEachIndexed { i, bill ->
                            BillRow(bill)
                            if (i < minOf(state.bills.size, 5) - 1)
                                HorizontalDivider(color = BorderColor.copy(0.5f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        if (state.bills.size > 5) {
                            TextButton(
                                onClick = {}, // navigate to Money tab
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View all ${state.bills.size} bills", color = Primary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── Financial goals ────────────────────────────────────
        val activeGoals = state.goals.filter { !it.isCompleted }.take(3)
        if (activeGoals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Financial Goals", Icons.Filled.Flag)
                Spacer(Modifier.height(10.dp))
            }
            activeGoals.forEach { goal ->
                item {
                    GoalCard(goal)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ── Pending transactions alert ─────────────────────────
        val pendingCount = dash?.pendingCount ?: 0
        if (pendingCount > 0) {
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningColor.copy(0.1f))
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.HourglassEmpty, "", tint = WarningColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "$pendingCount transaction${if (pendingCount > 1) "s" else ""} pending review",
                            fontSize = 13.sp, color = WarningColor, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // ── Recent transactions ────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Recent Transactions", Icons.Filled.History)
            Spacer(Modifier.height(10.dp))
        }

        if (state.recentTransactions.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("💳", fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No transactions yet", fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("SMS import will auto-detect bank messages", fontSize = 12.sp, color = TextMuted, lineHeight = 18.sp)
                    }
                }
            }
        } else {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(8.dp)) {
                        state.recentTransactions.forEachIndexed { i, tx ->
                            TransactionRow(tx)
                            if (i < state.recentTransactions.size - 1)
                                HorizontalDivider(color = BorderColor.copy(0.5f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector, tint: Color = Primary, label2: String? = null) {
    Row(
        Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, title, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        if (label2 != null) {
            Text(label2, fontSize = 12.sp, color = TextMuted)
        }
    }
}

// ── Hero sub-stat ──────────────────────────────────────────────────────────────

@Composable
private fun HeroStat(modifier: Modifier, label: String, value: String, color: Color, icon: ImageVector) {
    Row(
        modifier.background(Color.Black.copy(0.2f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(14.dp))
        Column {
            Text(label, fontSize = 10.sp, color = Color.White.copy(0.6f))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ── Quick stat card (scrollable row) ──────────────────────────────────────────

@Composable
private fun QuickStatCard(label: String, value: String, icon: ImageVector, gradient: List<Color>) {
    Card(
        modifier = Modifier.width(130.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth().background(Brush.linearGradient(gradient)).padding(14.dp)
        ) {
            Column {
                Icon(icon, label, tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(8.dp))
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, fontSize = 10.sp, color = Color.White.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Category spending bar ──────────────────────────────────────────────────────

@Composable
private fun CategoryBar(icon: String, label: String, amount: Double, pct: Float, budgetPct: Int?) {
    val barColor = when {
        (budgetPct ?: 0) >= 100 -> ErrorColor
        (budgetPct ?: 0) >= 80  -> WarningColor
        else                    -> Primary
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            if (budgetPct != null && budgetPct >= 80) {
                Text("${budgetPct}%", fontSize = 11.sp, color = barColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
            }
            Text(amount.formatCurrency(), fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(BorderColor)
        ) {
            Box(
                Modifier.fillMaxWidth(pct).height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(barColor.copy(0.7f), barColor)))
            )
        }
    }
}

// ── Budget alert card ──────────────────────────────────────────────────────────

@Composable
private fun BudgetAlertCard(categorySlug: String, spent: Double, budget: Double, pct: Int) {
    val label = Constants.CATEGORY_LABELS[categorySlug] ?: categorySlug
    val icon  = Constants.CATEGORY_ICONS[categorySlug] ?: "📦"
    val color = if (pct >= 100) ErrorColor else WarningColor

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.08f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("${spent.formatCurrency()} of ${budget.formatCurrency()}", fontSize = 12.sp, color = TextSecondary)
            }
            Box(
                Modifier.background(color.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("$pct%", fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Bill row ──────────────────────────────────────────────────────────────────

@Composable
private fun BillRow(bill: BillDto) {
    Row(
        Modifier.padding(horizontal = 4.dp, vertical = 8.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).background(
                if (bill.paidThisMonth) SuccessColor.copy(0.12f) else Primary.copy(0.12f),
                RoundedCornerShape(10.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(bill.icon, fontSize = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(bill.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(
                "Due ${bill.dueDay}${dayOrdinal(bill.dueDay)} of every month",
                fontSize = 11.sp, color = TextMuted
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(bill.amount.formatCurrency(), fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                if (bill.paidThisMonth) "✓ Paid" else "Unpaid",
                fontSize = 10.sp,
                color = if (bill.paidThisMonth) SuccessColor else WarningColor
            )
        }
    }
}

private fun dayOrdinal(day: Int): String = when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else          -> "th"
}

// ── Goal card ──────────────────────────────────────────────────────────────────

@Composable
private fun GoalCard(goal: FinancialGoalDto) {
    val progress = if (goal.targetAmount > 0)
        (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat()
    else 0f
    val pct = (progress * 100).toInt()

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.icon, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.title, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    goal.deadline?.let {
                        Text("Target: $it", fontSize = 11.sp, color = TextMuted)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$pct%", fontSize = 16.sp, color = Primary, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${goal.currentAmount.formatCurrency()} / ${goal.targetAmount.formatCurrency()}",
                        fontSize = 10.sp, color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(BorderColor)
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(GradientPurple))
                )
            }
        }
    }
}

// ── Transaction row ────────────────────────────────────────────────────────────

@Composable
fun TransactionRow(tx: TransactionDto) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(
                if (tx.isCredit) SuccessColor.copy(0.15f) else Primary.copy(0.12f)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(Constants.CATEGORY_ICONS[tx.categorySlug] ?: "📦", fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tx.merchant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(tx.transactionDate)
                    if (tx.note.isNotBlank()) append(" · ${tx.note.take(30)}")
                },
                fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (tx.isCredit) "+" else "-") + tx.amount.formatCurrency(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (tx.isCredit) SuccessColor else TextPrimary
            )
            if (tx.isPending) {
                Text("Pending", fontSize = 10.sp, color = WarningColor)
            }
        }
    }
}
