package com.spendwise.app.presentation.screens.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.core.toFriendlyDate
import com.spendwise.app.data.challenge.DailyChallenge
import com.spendwise.app.data.remote.dto.*
import com.spendwise.app.presentation.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import kotlin.math.ceil
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSettings: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    var showQuickAdd by remember { mutableStateOf(false) }
    var quickAmount by remember { mutableStateOf("") }
    var quickMerchant by remember { mutableStateOf("") }
    var quickCategory by remember { mutableStateOf("other") }

    // Auto-refresh every 30 seconds to pick up new SMS-imported transactions.
    // BUG FIX: repeatOnLifecycle(STARTED) suspends the loop whenever the screen
    // is not in the foreground (user switches tabs / backgrounds the app) and
    // resumes it on return — preventing ghost refreshes that drain battery and
    // network while the screen is gone.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(30_000L)
                vm.refresh()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        when {
            state.isLoading -> LoadingState()
            state.dashboard == null && state.dashboardError != null -> ErrorState(state.dashboardError!!, vm::load)
            else -> DashboardContent(state, onSettings, vm::refresh, vm)
        }

        // Quick-add FAB
        if (!state.isLoading) {
            FloatingActionButton(
                onClick = { showQuickAdd = true },
                containerColor = Primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Add, "Log Expense", tint = Color.White)
            }
        }
    }

    // Quick Expense Bottom Sheet
    if (showQuickAdd) {
        ModalBottomSheet(
            onDismissRequest = {
                showQuickAdd = false
                quickAmount = ""
                quickMerchant = ""
                quickCategory = "other"
            },
            containerColor = CardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Quick Log Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                OutlinedTextField(
                    value = quickAmount,
                    onValueChange = { quickAmount = it },
                    label = { Text("Amount (₹)", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = Primary, focusedLabelColor = Primary, unfocusedLabelColor = TextMuted
                    )
                )

                // Category chips
                Text("Category", fontSize = 12.sp, color = TextSecondary)
                val cats = listOf("food" to "🍽️ Food", "transport" to "🚗 Transport",
                    "shopping" to "🛒 Shopping", "bills" to "💡 Bills", "other" to "📦 Other")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cats) { (slug, label) ->
                        val selected = quickCategory == slug
                        Box(
                            Modifier
                                .background(
                                    if (selected) Primary else CardBg2,
                                    RoundedCornerShape(20.dp)
                                )
                                .border(1.dp, if (selected) Primary else BorderColor, RoundedCornerShape(20.dp))
                                .clickable { quickCategory = slug }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = if (selected) Color.White else TextSecondary)
                        }
                    }
                }

                OutlinedTextField(
                    value = quickMerchant,
                    onValueChange = { quickMerchant = it },
                    label = { Text("Merchant / Description (optional)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        cursorColor = Primary, focusedLabelColor = Primary, unfocusedLabelColor = TextMuted
                    )
                )

                Button(
                    onClick = {
                        val amt = quickAmount.toDoubleOrNull()
                        if (amt != null && amt > 0) {
                            vm.logQuickExpense(amt, quickMerchant, quickCategory)
                            showQuickAdd = false
                            quickAmount = ""
                            quickMerchant = ""
                            quickCategory = "other"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(14.dp),
                    enabled = quickAmount.toDoubleOrNull()?.let { it > 0 } == true
                ) {
                    Icon(Icons.Filled.Add, "", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add Expense", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a"
            )
            Box(
                Modifier.size(64.dp).background(Brush.radialGradient(GradientPurple), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("₹", fontSize = 28.sp, color = Color.White.copy(alpha = alpha), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(4.dp))
            Text("Loading your finances…", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(32.dp)) {
            Box(
                Modifier.size(72.dp).background(ErrorColor.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.WifiOff, "", tint = ErrorColor, modifier = Modifier.size(32.dp)) }
            Text("Could not connect", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(error, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(14.dp)) { Text("Retry") }
        }
    }
}

@Composable
private fun DashboardContent(state: HomeUiState, onSettings: () -> Unit, onRefresh: () -> Unit, vm: HomeViewModel = hiltViewModel()) {
    val dash       = state.dashboard
    val today      = LocalDate.now()
    val hourNow    = LocalTime.now().hour
    val greeting   = when { hourNow < 12 -> "Good morning"; hourNow < 17 -> "Good afternoon"; else -> "Good evening" }
    val firstName  = state.userName?.split(" ")?.firstOrNull() ?: "there"
    val monthName  = Month.of(dash?.month ?: today.monthValue).name.lowercase().replaceFirstChar { it.uppercase() }

    val salary      = dash?.salary?.amount ?: 0.0
    val spent       = dash?.totalSpent ?: 0.0
    val credit      = dash?.totalCredit ?: 0.0
    val netBal      = (salary + credit) - spent
    val savingsRate = dash?.savingsRate ?: 0
    val emiBurden   = dash?.emiBurdenPct ?: 0
    val spentPct    = if (salary > 0) (spent / salary).coerceIn(0.0, 1.0).toFloat() else 0f
    val daysLeft    = dash?.daysLeft ?: 0
    val projected   = dash?.projectedSpend ?: 0.0

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {

        // ── Header ───────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("$greeting,", fontSize = 13.sp, color = TextSecondary)
                    Text(firstName, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                    }
                    // Month badge
                    Box(
                        Modifier.background(CardBg, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("$monthName ${today.year}", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(40.dp).background(CardBg, CircleShape)
                    ) {
                        Icon(Icons.Filled.Settings, "Settings", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ── XP Bar ────────────────────────────────────────────────
        item {
            XpBar(
                xp = state.xp,
                levelName = state.levelName,
                progress = state.xpProgress,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // ── Motivational Banner ───────────────────────────────────
        item {
            MotivationalBanner(savingsRate, state.goals.filter { !it.isCompleted }.size, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // ── Salary Day Card ───────────────────────────────────────
        if (state.isSalaryDay) {
            item {
                SalaryDayCard(
                    amount      = state.salaryDayAmount,
                    suggestions = state.salaryDaySuggestions,
                    modifier    = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // ── Spending Streak Card ──────────────────────────────────
        if (state.spendingStreak >= 3) {
            item {
                SpendingStreakCard(
                    streak   = state.spendingStreak,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // ── Hero Balance Card ─────────────────────────────────────
        item {
            HeroBalanceCard(
                netBal = netBal, salary = salary, credit = credit, spent = spent,
                spentPct = spentPct, savingsRate = savingsRate, daysLeft = daysLeft,
                projected = projected, emiTotal = dash?.emiTotal ?: 0.0,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // ── Advisor Alerts ────────────────────────────────────────
        if (state.topInsights.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.topInsights) { insight ->
                        AdvisorInsightChip(insight)
                    }
                }
            }
        }

        // ── Financial Health Strip ────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            FinancialHealthStrip(
                savingsRate = savingsRate,
                emiBurden = emiBurden,
                ccOutstanding = dash?.ccOutstanding ?: 0.0,
                salary = salary
            )
        }

        // ── Emergency Fund Meter ──────────────────────────────────
        if (state.emergencyFundMonths < 6.0) {
            item {
                Spacer(Modifier.height(16.dp))
                EmergencyFundCard(
                    months = state.emergencyFundMonths,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // ── Daily Challenge Card ──────────────────────────────────
        state.todayChallenge?.let { challenge ->
            item {
                Spacer(Modifier.height(16.dp))
                DailyChallengeCard(
                    challenge = challenge,
                    accepted = state.challengeAccepted,
                    completed = state.challengeCompleted,
                    onAccept = vm::acceptChallenge,
                    onComplete = vm::completeChallenge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // ── Quick Stats Strip ─────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { QuickStat("Bank Balance", dash?.bankBalance?.formatCurrency() ?: "₹0", Icons.Filled.AccountBalance, GradientTeal) }
                item { QuickStat("CC Outstanding", dash?.ccOutstanding?.formatCurrency() ?: "₹0", Icons.Filled.CreditCard, if ((dash?.ccOutstanding ?: 0.0) > 0) GradientRose else GradientGreen) }
                item { QuickStat("EMI Burden", "$emiBurden% of salary", Icons.Filled.Money, if (emiBurden > 40) GradientRose else if (emiBurden > 30) GradientAmber else GradientGreen) }
                item { QuickStat("Burn Rate", "${dash?.burnRate?.formatCurrency() ?: "₹0"}/day", Icons.Filled.Whatshot, GradientOrange) }
                item { QuickStat("Pending TXNs", "${dash?.pendingCount ?: 0} items", Icons.Filled.HourglassEmpty, GradientPurple) }
                if (state.roundUpSavings > 0) {
                    item { QuickStat("Round-Up Saved", state.roundUpSavings.formatCurrency(), Icons.Filled.AccountBalance, GradientGold) }
                }
            }
        }

        // ── Financial Goals with Timeline ─────────────────────────
        val activeGoals = state.goals.filter { !it.isCompleted }.take(3)
        if (activeGoals.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Financial Goals", Icons.Filled.EmojiEvents, "${activeGoals.size} active")
                Spacer(Modifier.height(4.dp))
            }
            items(activeGoals) { goal ->
                EnhancedGoalCard(
                    goal = goal,
                    salary = salary,
                    savingsRate = savingsRate,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }
            // Goal summary CTA
            if (state.goals.filter { !it.isCompleted }.size > 3) {
                item {
                    TextButton(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        onClick = {}
                    ) {
                        Text("${state.goals.filter { !it.isCompleted }.size - 3} more goals → Money tab", color = Primary, fontSize = 12.sp)
                    }
                }
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
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        topCats.forEach { cat ->
                            val alert = dash?.budgetAlerts?.find { it.categorySlug == cat.categorySlug }
                            CategorySpendRow(
                                label = CATEGORY_META[cat.categorySlug]?.first ?: "📦",
                                name = CATEGORY_META[cat.categorySlug]?.second ?: cat.categorySlug,
                                amount = cat.total,
                                sharePct = (cat.total / totalSpentRef).coerceIn(0.0, 1.0).toFloat(),
                                budgetPct = alert?.pct ?: 0
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
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
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

        // ── Recent Transactions ───────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Recent Transactions", Icons.Filled.History, "${state.recentTransactions.size} shown")
            Spacer(Modifier.height(10.dp))
        }
        if (state.recentTransactions.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
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
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
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

// ── Motivational Banner ───────────────────────────────────────

@Composable
private fun MotivationalBanner(savingsRate: Int, activeGoals: Int, modifier: Modifier = Modifier) {
    val (emoji, message, bgGradient) = when {
        savingsRate >= 35 -> Triple("🔥", "You're crushing it! Saving ${savingsRate}% — top-tier financial health!", GradientGreen)
        savingsRate >= 25 -> Triple("💪", "Excellent! At ${savingsRate}% savings, your goals are within reach.", GradientTeal)
        savingsRate >= 15 -> Triple("📈", "Good progress! ${savingsRate}% savings rate. Boost to 25% for faster goals.", GradientPurple)
        savingsRate >= 5  -> Triple("💡", "Building momentum! Small wins today = big goals tomorrow.", GradientAmber)
        else              -> Triple("🚀", "Let's go! Every rupee saved is a step toward your dreams.", GradientPurple)
    }

    Box(
        modifier.fillMaxWidth()
            .background(Brush.linearGradient(bgGradient.map { it.copy(0.15f) }), RoundedCornerShape(16.dp))
            .border(0.5.dp, bgGradient.first().copy(0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f)
            )
            if (activeGoals > 0) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.background(bgGradient.first().copy(0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("$activeGoals goals", fontSize = 10.sp, color = bgGradient.first(), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Hero Balance Card ─────────────────────────────────────────

@Composable
private fun HeroBalanceCard(
    netBal: Double, salary: Double, credit: Double, spent: Double,
    spentPct: Float, savingsRate: Int, daysLeft: Int, projected: Double,
    emiTotal: Double, modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(GradientHero))
                .padding(22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("NET BALANCE", fontSize = 10.sp, color = Color.White.copy(0.55f), letterSpacing = 1.5.sp)
                            if (netBal > 0) {
                                Box(Modifier.background(SuccessColor.copy(0.18f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                                    Text("↑", fontSize = 9.sp, color = SuccessColor)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(netBal.formatCurrency(), fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-0.5).sp)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Savings rate ring
                        SavingsRateRing(savingsRate)
                        if (daysLeft > 0) {
                            Text("$daysLeft days left", fontSize = 10.sp, color = Color.White.copy(0.5f))
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Monthly spending progress bar
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spent ${spent.formatCurrency()} of ${salary.formatCurrency()}", fontSize = 12.sp, color = Color.White.copy(0.65f))
                        val overPct = if (projected > salary && salary > 0) "+${((projected - salary) / salary * 100).toInt()}% projected" else ""
                        if (overPct.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚠", fontSize = 10.sp)
                                Spacer(Modifier.width(3.dp))
                                Text(overPct, fontSize = 10.sp, color = WarningColor)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.12f))) {
                        Box(
                            Modifier.fillMaxWidth(spentPct).height(7.dp).clip(RoundedCornerShape(50))
                                .background(Brush.horizontalGradient(if (spentPct > 0.85f) listOf(Color(0xFFFF4D6A), Color(0xFFFF8C42)) else listOf(Color(0xFF7C6BFF), Color(0xFF00D4FF))))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${(spentPct * 100).toInt()}% of monthly budget used", fontSize = 10.sp, color = Color.White.copy(0.45f))
                }

                Spacer(Modifier.height(16.dp))

                // Pills row
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroPill("↑ Income", (salary + credit).formatCurrency(), Color(0xFF00E5A0), Modifier.weight(1f))
                    HeroPill("↓ Spent", spent.formatCurrency(), Color(0xFFFF6B8A), Modifier.weight(1f))
                    HeroPill("→ EMI", emiTotal.formatCurrency(), Color(0xFFFFB547), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SavingsRateRing(savingsRate: Int) {
    val pct = (savingsRate / 100f).coerceIn(0f, 1f)
    val ringColor = when {
        savingsRate >= 30 -> SuccessColor
        savingsRate >= 20 -> Color(0xFF00D4FF)
        savingsRate >= 10 -> WarningColor
        else              -> ErrorColor
    }
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(64.dp)) {
            val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = Color.White.copy(0.12f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(color = ringColor, startAngle = -90f, sweepAngle = 360f * pct, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$savingsRate%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("saved", fontSize = 8.sp, color = Color.White.copy(0.55f))
        }
    }
}

// ── Financial Health Strip ────────────────────────────────────

/**
 * Computes a 0–100 financial health score from three weighted components.
 * Returns a data class so the info dialog can show each contribution.
 */
private data class HealthScoreBreakdown(
    val savingsScore: Int,   // -10 to +20
    val emiScore: Int,       // -15 to +10
    val ccScore: Int,        // -15 to +10
    val totalScore: Int,     // 0–100
    val savingsRate: Int,
    val emiBurden: Int,
    val ccVsSalaryPct: Int
)

private fun computeHealthScore(
    savingsRate: Int,
    emiBurden: Int,
    ccOutstanding: Double,
    salary: Double
): HealthScoreBreakdown {
    // Savings rate component: how much of income is saved (most important factor)
    val savingsScore = when {
        savingsRate >= 30 -> 20
        savingsRate >= 25 -> 15
        savingsRate >= 20 -> 10
        savingsRate >= 15 ->  5
        savingsRate >= 10 ->  0
        savingsRate >=  5 -> -5
        else              -> -10
    }
    // EMI burden: monthly EMIs as % of salary
    val emiScore = when {
        emiBurden < 15    -> 10
        emiBurden < 25    ->  5
        emiBurden < 35    ->  0
        emiBurden < 45    -> -10
        else              -> -15
    }
    // CC outstanding vs monthly salary
    val ccVsSalaryPct = if (salary > 0) (ccOutstanding / salary * 100).toInt()
                        else if (ccOutstanding > 0) 100 else 0
    val ccScore = when {
        ccOutstanding == 0.0 -> 10
        ccVsSalaryPct < 25   ->  5
        ccVsSalaryPct < 50   ->  0
        ccVsSalaryPct < 75   -> -10
        else                 -> -15
    }
    val total = (50 + savingsScore + emiScore + ccScore).coerceIn(0, 100)
    return HealthScoreBreakdown(savingsScore, emiScore, ccScore, total, savingsRate, emiBurden, ccVsSalaryPct)
}

@Composable
private fun FinancialHealthStrip(savingsRate: Int, emiBurden: Int, ccOutstanding: Double, salary: Double) {
    var showInfo by remember { mutableStateOf(false) }
    val hsd = computeHealthScore(savingsRate, emiBurden, ccOutstanding, salary)
    val healthScore = hsd.totalScore
    val (healthLabel, healthColor) = when {
        healthScore >= 80 -> "Excellent" to SuccessColor
        healthScore >= 60 -> "Good"      to Color(0xFF00D4FF)
        healthScore >= 40 -> "Fair"      to WarningColor
        else              -> "Needs Attention" to ErrorColor
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Health score circle
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
                    val s = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(color = healthColor.copy(0.15f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = s)
                    drawArc(color = healthColor, startAngle = -90f, sweepAngle = 360f * (healthScore / 100f), useCenter = false, style = s)
                }
                Text("$healthScore", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = healthColor)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Financial Health", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(healthColor.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(healthLabel, fontSize = 10.sp, color = healthColor, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    // Info button — explains how the score is calculated
                    IconButton(
                        onClick = { showInfo = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.Info, "How is this calculated?", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    when {
                        healthScore >= 80 -> "Keep it up! You're managing money like a pro."
                        healthScore >= 60 -> "Good habits! Small tweaks can push you to excellent."
                        healthScore >= 40 -> "Pay down debt and boost savings to improve."
                        else              -> "Focus on reducing expenses and building savings."
                    },
                    fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp
                )
            }
        }
    }

    if (showInfo) {
        HealthScoreInfoDialog(hsd = hsd, healthLabel = healthLabel, healthColor = healthColor, onDismiss = { showInfo = false })
    }
}

@Composable
private fun HealthScoreInfoDialog(
    hsd: HealthScoreBreakdown,
    healthLabel: String,
    healthColor: Color,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(36.dp).background(healthColor.copy(0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("${hsd.totalScore}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = healthColor) }
                Column {
                    Text("Financial Health Score", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Score: $healthLabel", fontSize = 11.sp, color = healthColor)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your score is calculated from 3 components (base 50 points):", fontSize = 12.sp, color = TextSecondary)

                HealthScoreRow(
                    icon = "💰",
                    label = "Savings Rate",
                    detail = "${hsd.savingsRate}% of income saved",
                    score = hsd.savingsScore,
                    tip = when {
                        hsd.savingsRate >= 25 -> "Excellent! Saving ≥25% is best-in-class."
                        hsd.savingsRate >= 15 -> "Good. Push to 25%+ for maximum points."
                        hsd.savingsRate >= 5  -> "Try to cut non-essential spending to save more."
                        else                  -> "Start saving even ₹500/month to improve this."
                    }
                )

                HorizontalDivider(color = BorderColor.copy(0.4f), thickness = 0.5.dp)

                HealthScoreRow(
                    icon = "🏦",
                    label = "EMI Burden",
                    detail = "${hsd.emiBurden}% of salary on EMIs",
                    score = hsd.emiScore,
                    tip = when {
                        hsd.emiBurden < 20  -> "Healthy! EMIs under 20% of salary."
                        hsd.emiBurden < 35  -> "Manageable. Avoid taking new loans."
                        hsd.emiBurden < 45  -> "High EMI load — prepay loans if possible."
                        else                -> "Critical: EMIs consuming 45%+ of income."
                    }
                )

                HorizontalDivider(color = BorderColor.copy(0.4f), thickness = 0.5.dp)

                HealthScoreRow(
                    icon = "💳",
                    label = "Credit Card Debt",
                    detail = "${hsd.ccVsSalaryPct}% of monthly salary outstanding",
                    score = hsd.ccScore,
                    tip = when {
                        hsd.ccVsSalaryPct == 0  -> "Zero CC outstanding — perfect!"
                        hsd.ccVsSalaryPct < 30  -> "Low CC balance. Pay in full each month."
                        hsd.ccVsSalaryPct < 60  -> "Moderate. Pay more than the minimum due."
                        else                     -> "High CC debt. Prioritise clearing this."
                    }
                )

                HorizontalDivider(color = BorderColor.copy(0.4f), thickness = 0.5.dp)

                Row(Modifier.fillMaxWidth().background(healthColor.copy(0.08f), RoundedCornerShape(10.dp)).padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Score", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("${hsd.totalScore} / 100", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = healthColor)
                }
                Text(
                    "Score updates automatically as you add transactions, pay off debt, and save more.",
                    fontSize = 11.sp, color = TextMuted, lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Primary), shape = RoundedCornerShape(12.dp)) {
                Text("Got it")
            }
        }
    )
}

@Composable
private fun HealthScoreRow(icon: String, label: String, detail: String, score: Int, tip: String) {
    val scoreColor = when {
        score > 0  -> SuccessColor
        score == 0 -> WarningColor
        else       -> ErrorColor
    }
    val scoreStr = if (score > 0) "+$score" else "$score"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(detail, fontSize = 11.sp, color = TextSecondary)
            }
            Box(
                Modifier.background(scoreColor.copy(0.15f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
            ) { Text(scoreStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scoreColor) }
        }
        Text(tip, fontSize = 11.sp, color = TextMuted, lineHeight = 15.sp, modifier = Modifier.padding(start = 22.dp))
    }
}

// ── Enhanced Goal Card with Timeline ─────────────────────────

@Composable
private fun EnhancedGoalCard(
    goal: FinancialGoalDto,
    salary: Double,
    savingsRate: Int,
    modifier: Modifier = Modifier
) {
    val pct = if (goal.targetAmount > 0) (goal.currentAmount / goal.targetAmount).coerceIn(0.0, 1.0).toFloat() else 0f
    val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)

    // Calculate monthly contribution and ETA
    val monthlyContrib = when {
        goal.monthlyTarget > 0 -> goal.monthlyTarget
        salary > 0             -> salary * (savingsRate / 100.0) * 0.3  // assume 30% of savings to this goal
        else                   -> 0.0
    }
    val monthsToGo = if (monthlyContrib > 0 && remaining > 0) ceil(remaining / monthlyContrib).toInt() else 0

    // Check deadline
    val deadlineDate = goal.deadline?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    val monthsUntilDeadline = deadlineDate?.let {
        val now = LocalDate.now()
        ((it.year - now.year) * 12 + (it.monthValue - now.monthValue)).coerceAtLeast(0)
    }

    val (statusLabel, statusColor) = when {
        pct >= 1.0f -> "Completed! 🎉" to SuccessColor
        pct >= 0.9f -> "Almost there!" to SuccessColor
        monthsUntilDeadline != null && monthsToGo > monthsUntilDeadline -> "At Risk ⚠" to ErrorColor
        monthsUntilDeadline != null && monthsToGo <= monthsUntilDeadline -> "On Track ✓" to SuccessColor
        monthsToGo in 1..3  -> "Very Close!" to SuccessColor
        monthsToGo in 4..12 -> "On Track"    to Color(0xFF00D4FF)
        monthsToGo > 12     -> "Keep Going"  to WarningColor
        else                -> "Set a Target" to TextMuted
    }

    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon circle
                Box(
                    Modifier.size(44.dp).background(Brush.linearGradient(GradientPurple.map { it.copy(0.25f) }), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(goal.icon, fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.background(statusColor.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 7.dp, vertical = 3.dp)
                        ) { Text(statusLabel, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold) }
                        if (deadlineDate != null) {
                            Text("Due ${deadlineDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${deadlineDate.year}", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
                // Percentage ring
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(48.dp)) {
                        val s = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(color = BorderColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = s)
                        drawArc(color = statusColor, startAngle = -90f, sweepAngle = 360f * pct, useCenter = false, style = s)
                    }
                    Text("${(pct * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = statusColor)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(BorderColor)) {
                Box(
                    Modifier.fillMaxWidth(pct).height(5.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(GradientPurple))
                )
            }

            Spacer(Modifier.height(10.dp))

            // Stats row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                GoalStatPill("Saved", goal.currentAmount.formatCurrency(), Primary)
                GoalStatPill("Target", goal.targetAmount.formatCurrency(), TextSecondary)
                if (monthsToGo > 0 && remaining > 0) {
                    GoalStatPill(
                        "ETA",
                        if (monthsToGo >= 12) "${monthsToGo / 12}y ${monthsToGo % 12}m" else "${monthsToGo}mo",
                        statusColor
                    )
                } else if (monthlyContrib > 0) {
                    GoalStatPill("Monthly", monthlyContrib.formatCurrency(), Color(0xFF00D4FF))
                }
            }

            // Encouragement message
            if (remaining > 0 && monthlyContrib > 0 && monthsToGo > 0) {
                Spacer(Modifier.height(8.dp))
                val msg = when {
                    monthsToGo <= 1 -> "🎯 Just one more month — you're almost there!"
                    monthsToGo <= 3 -> "⚡ ${monthlyContrib.formatCurrency()}/mo for $monthsToGo more months and you're done!"
                    monthsToGo <= 6 -> "💪 Stay consistent! ${monthsToGo} months at ${monthlyContrib.formatCurrency()}/mo"
                    else            -> "📅 Increase to ${((remaining / 12)).formatCurrency()}/mo to finish in 1 year"
                }
                Box(
                    Modifier.fillMaxWidth().background(Primary.copy(0.07f), RoundedCornerShape(10.dp)).padding(10.dp)
                ) { Text(msg, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp) }
            }
        }
    }
}

@Composable
private fun GoalStatPill(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 9.sp, color = TextMuted)
    }
}

// ── Composables ───────────────────────────────────────────────

@Composable
private fun HeroPill(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(Color.Black.copy(0.25f), RoundedCornerShape(12.dp)).padding(10.dp, 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 9.sp, color = Color.White.copy(0.5f), letterSpacing = 0.3.sp)
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
        Box(
            Modifier.size(28.dp).background(Primary.copy(0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, title, tint = Primary, modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
        if (badge.isNotEmpty()) {
            Box(Modifier.background(CardBg2, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(badge, fontSize = 10.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun AdvisorInsightChip(insight: AdvisorInsightDto) {
    val color = if (insight.priority == "critical") ErrorColor else WarningColor
    Card(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(0.08f))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(color.copy(0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Text(insight.icon, fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(insight.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${insight.metric} ${insight.metricLabel}", fontSize = 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(8.dp).background(color, CircleShape)
            )
        }
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
            Box(Modifier.size(28.dp).background(CardBg2, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 13.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(name, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            if (budgetPct >= 80) {
                Box(Modifier.background(barColor.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("$budgetPct%", fontSize = 10.sp, color = barColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
            Text(amount.formatCurrency(), fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(BorderColor)) {
            Box(Modifier.fillMaxWidth(sharePct).height(4.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(barColor.copy(0.6f), barColor))))
        }
    }
}

@Composable
private fun BillRow(bill: BillDto) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).background(
                if (bill.paidThisMonth) SuccessColor.copy(0.12f) else Primary.copy(0.1f),
                RoundedCornerShape(12.dp)
            ), contentAlignment = Alignment.Center
        ) { Text(bill.icon, fontSize = 16.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(bill.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("Due ${bill.dueDay}${ordinal(bill.dueDay)} every month", fontSize = 11.sp, color = TextMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(bill.amount.formatCurrency(), fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(6.dp).background(if (bill.paidThisMonth) SuccessColor else WarningColor, CircleShape))
                Text(if (bill.paidThisMonth) "Paid" else "Unpaid", fontSize = 10.sp,
                    color = if (bill.paidThisMonth) SuccessColor else WarningColor)
            }
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
                append(tx.transactionDate.toFriendlyDate())
                if (tx.note.isNotBlank()) append(" · ${tx.note.take(25)}")
            }, fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (tx.isCredit) "+" else "−") + tx.amount.formatCurrency(),
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (tx.isCredit) SuccessColor else TextPrimary
            )
            if (tx.isPending) {
                Box(Modifier.background(WarningColor.copy(0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
                    Text("Pending", fontSize = 9.sp, color = WarningColor)
                }
            }
        }
    }
}

// ── Salary Day Card ───────────────────────────────────────────

@Composable
private fun SalaryDayCard(amount: Double, suggestions: List<String>, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .background(Brush.linearGradient(GradientGold), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎉", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Salary Day!",
                        fontSize    = 16.sp,
                        fontWeight  = FontWeight.ExtraBold,
                        color       = Color.White
                    )
                    Text(
                        amount.formatCurrency() + " credited",
                        fontSize = 12.sp,
                        color    = Color.White.copy(0.8f)
                    )
                }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Smart allocation tips:",
                    fontSize   = 11.sp,
                    color      = Color.White.copy(0.7f),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                suggestions.forEach { suggestion ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(suggestion, fontSize = 12.sp, color = Color.White.copy(0.9f), lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

// ── Spending Streak Card ──────────────────────────────────────

@Composable
private fun SpendingStreakCard(streak: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .background(Brush.linearGradient(GradientOrange.map { it.copy(0.85f) }), RoundedCornerShape(16.dp))
            .border(0.5.dp, Color(0xFFFF9800).copy(0.4f), RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔥", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "$streak-day spending streak!",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
                Text(
                    "Under ₹500 in non-essential spending — keep it up!",
                    fontSize    = 11.sp,
                    color       = Color.White.copy(0.8f),
                    lineHeight  = 15.sp
                )
            }
            Box(
                Modifier.background(Color.White.copy(0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("$streak days", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Emergency Fund Card ───────────────────────────────────────

@Composable
private fun EmergencyFundCard(months: Double, modifier: Modifier = Modifier) {
    val pct = (months / 6.0).coerceIn(0.0, 1.0).toFloat()
    val color = when {
        months >= 6 -> SuccessColor
        months >= 3 -> WarningColor
        else -> ErrorColor
    }
    val label = when {
        months >= 6 -> "You're fully covered!"
        months >= 3 -> "Getting there — target: 6 months"
        else -> "Build your safety net"
    }
    Card(
        Modifier.fillMaxWidth().then(modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shield  Emergency Fund", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${String.format("%.1f", months)}/6 months", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(BorderColor)
            ) {
                Box(
                    Modifier.fillMaxWidth(pct).height(6.dp).clip(RoundedCornerShape(50)).background(color)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

// ── Daily Challenge Card ──────────────────────────────────────

@Composable
private fun DailyChallengeCard(
    challenge: DailyChallenge,
    accepted: Boolean,
    completed: Boolean,
    onAccept: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Daily Challenge", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.sp)
                Box(
                    Modifier
                        .background(Primary.copy(0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("+${challenge.xpReward} XP", fontSize = 10.sp, color = Primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(challenge.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(challenge.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(challenge.description, fontSize = 11.sp, color = TextSecondary)
                    Text(challenge.savingsEstimate, fontSize = 10.sp, color = SuccessColor)
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                completed -> {
                    Text(
                        "Challenge completed! +${challenge.xpReward} XP earned",
                        fontSize = 12.sp,
                        color = SuccessColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                accepted -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Accepted! Mark done when complete",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onComplete,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Done!", fontSize = 12.sp)
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Accept Challenge")
                    }
                }
            }
        }
    }
}

// ── XP Bar ────────────────────────────────────────────────────

@Composable
private fun XpBar(xp: Int, levelName: String, progress: Float, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .background(Primary.copy(0.15f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("$levelName", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(BorderColor)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(GradientGold))
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$xp XP", fontSize = 10.sp, color = TextMuted)
    }
}

// ── Helpers ───────────────────────────────────────────────────

private fun ordinal(day: Int): String = when {
    day in 11..13 -> "th"; day % 10 == 1 -> "st"; day % 10 == 2 -> "nd"; day % 10 == 3 -> "rd"; else -> "th"
}

val CATEGORY_META = mapOf(
    "food"          to Pair("🍽️", "Food & Dining"),
    "transport"     to Pair("🚗", "Transport"),
    "shopping"      to Pair("🛒", "Shopping"),
    "bills"         to Pair("💡", "Bills"),
    "health"        to Pair("💊", "Health"),
    "entertainment" to Pair("🎬", "Entertainment"),
    "education"     to Pair("📚", "Education"),
    "salary"        to Pair("💰", "Salary"),
    "investment"    to Pair("📈", "Investment"),
    "transfer"      to Pair("🔄", "Transfer"),
    "emi"           to Pair("🏦", "EMI"),
    "income"        to Pair("💵", "Income"),
    "savings"       to Pair("🏦", "Savings"),
    "groceries"     to Pair("🛍️", "Groceries"),
    "fuel"          to Pair("⛽", "Fuel"),
    "travel"        to Pair("✈️", "Travel"),
    "rent"          to Pair("🏠", "Rent"),
    "insurance"     to Pair("🛡️", "Insurance"),
    "tax"           to Pair("📋", "Tax"),
    "other"         to Pair("📦", "Other"),
)
