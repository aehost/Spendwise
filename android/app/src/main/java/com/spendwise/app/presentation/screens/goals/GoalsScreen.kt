package com.spendwise.app.presentation.screens.goals

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.FinancialGoalDto
import com.spendwise.app.presentation.components.SwProgressRing
import com.spendwise.app.presentation.theme.*
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun GoalsScreen(
    onBack: () -> Unit,
    vm: GoalsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var contributeTarget by remember { mutableStateOf<FinancialGoalDto?>(null) }

    // Snackbar messages
    val snackbarState = remember { SnackbarHostState() }
    LaunchedEffect(state.successMessage, state.error) {
        state.successMessage?.let { snackbarState.showSnackbar(it); vm.clearMessages() }
        state.error?.let { snackbarState.showSnackbar("Error: $it"); vm.clearMessages() }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbarState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = com.spendwise.app.presentation.components.hapticClick { showAddDialog = true },
                containerColor = Primary,
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Filled.Add, "Add Goal") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Header
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(GradientHero))
                        .padding(top = 16.dp, bottom = 20.dp, start = 8.dp, end = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Financial Goals", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("Track progress · Plan achievement · Stay on target", fontSize = 12.sp, color = Color.White.copy(0.75f))
                        }
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                return@LazyColumn
            }

            state.error?.let {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(0.1f))
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(it, color = ErrorColor, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = vm::load, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                                Text("Retry")
                            }
                        }
                    }
                }
                return@LazyColumn
            }

            // Savings plan overview
            if (state.salary > 0) {
                item { SavingsPlanCard(state) }
            }

            // Short-term goals
            if (state.shortTermGoals.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Short-term Goals",
                        "< 12 months · Quick wins to build momentum",
                        SuccessColor
                    )
                }
                items(state.shortTermGoals) { goal ->
                    GoalCard(
                        goal = goal,
                        plan = vm.computePlan(goal),
                        onContribute = { contributeTarget = goal },
                        onDelete = { vm.deleteGoal(goal.id) }
                    )
                }
            }

            // Long-term goals
            if (state.longTermGoals.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Long-term Goals",
                        "12+ months · Big dreams worth planning",
                        GoldAccent
                    )
                }
                items(state.longTermGoals) { goal ->
                    GoalCard(
                        goal = goal,
                        plan = vm.computePlan(goal),
                        onContribute = { contributeTarget = goal },
                        onDelete = { vm.deleteGoal(goal.id) }
                    )
                }
            }

            // Completed
            if (state.completedGoals.isNotEmpty()) {
                item {
                    SectionHeader("Completed Goals", "Keep up the great work!", SuccessColor)
                }
                items(state.completedGoals) { goal ->
                    CompletedGoalRow(goal)
                }
            }

            // Empty state
            if (state.goals.isEmpty()) {
                item { EmptyGoalsPlaceholder { showAddDialog = true } }
            }
        }
    }

    // Add Goal Dialog
    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { title, desc, amount, deadline, icon, monthly ->
                vm.createGoal(title, desc, amount, deadline, icon, monthly)
                showAddDialog = false
            }
        )
    }

    // Contribute Dialog
    contributeTarget?.let { goal ->
        ContributeDialog(
            goal = goal,
            onDismiss = { contributeTarget = null },
            onContribute = { amount, note ->
                vm.contributeToGoal(goal.id, amount, note)
                contributeTarget = null
            }
        )
    }
}

@Composable
private fun SavingsPlanCard(state: GoalsState) {
    val surplus = state.availableMonthlySavings - state.totalMonthlyNeeded
    val surplusColor = if (surplus >= 0) SuccessColor else ErrorColor
    val coverageText = if (state.totalMonthlyNeeded <= 0) "No monthly targets set"
    else if (surplus >= 0) "You can cover all goals with ₹${"%,.0f".format(surplus)}/mo to spare"
    else "You need ₹${"%,.0f".format(-surplus)}/mo more — consider cutting non-essential spending"

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Monthly Savings Plan", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanStat(
                    "Savings Rate",
                    "${state.savingsRate}%",
                    if (state.savingsRate >= 20) SuccessColor else WarningColor,
                    Modifier.weight(1f)
                )
                PlanStat(
                    "Available/mo",
                    state.availableMonthlySavings.formatCurrency(),
                    SuccessColor,
                    Modifier.weight(1f)
                )
                PlanStat(
                    "Goals Need/mo",
                    if (state.totalMonthlyNeeded > 0) state.totalMonthlyNeeded.formatCurrency() else "—",
                    if (state.totalMonthlyNeeded <= state.availableMonthlySavings) SuccessColor else ErrorColor,
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(coverageText, fontSize = 12.sp, color = surplusColor, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun PlanStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(color.copy(0.1f), RoundedCornerShape(10.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, fontSize = 9.sp, color = TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, accentColor: Color) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accentColor))
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Text(subtitle, fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(start = 18.dp))
    }
}

@Composable
private fun GoalCard(
    goal: FinancialGoalDto,
    plan: GoalPlan,
    onContribute: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }
    val statusColor = when {
        plan.percentComplete >= 0.75f -> SuccessColor
        plan.isOnTrack               -> Primary
        else                         -> WarningColor
    }
    val statusLabel = when {
        plan.percentComplete >= 1f   -> "Complete!"
        plan.percentComplete >= 0.75f -> "Almost there"
        plan.isOnTrack               -> "On Track"
        else                         -> "Behind"
    }

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Top row: progress-ring icon + title + status badge + delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ring around the icon instantly communicates % complete.
                SwProgressRing(
                    progress    = plan.percentComplete,
                    size        = 48.dp,
                    strokeWidth = 4.dp,
                    ringColor   = statusColor,
                    trackColor  = statusColor.copy(0.15f)
                ) {
                    Text(goal.icon, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(goal.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (goal.description.isNotBlank()) {
                        Text(goal.description, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                    }
                }
                Box(
                    Modifier.background(statusColor.copy(0.15f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(statusLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Delete, "Delete",
                    tint = TextMuted.copy(0.5f),
                    modifier = Modifier.size(18.dp).clickable { showDelete = true }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Progress bar
            val pct = plan.percentComplete
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(0.15f))
            ) {
                Box(
                    Modifier.fillMaxWidth(pct).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(statusColor.copy(0.7f), statusColor)))
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(goal.currentAmount.formatCurrency(), fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                Text("${(pct * 100).roundToInt()}% of ${goal.targetAmount.formatCurrency()}", fontSize = 11.sp, color = TextMuted)
            }

            Spacer(Modifier.height(12.dp))

            // Stats row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalStat(
                    "Monthly Need",
                    plan.monthlyNeeded.formatCurrency(),
                    Primary,
                    Modifier.weight(1f)
                )
                GoalStat(
                    "Months Left",
                    if (plan.monthsToAchieve >= 999) "—" else "${plan.monthsToAchieve}mo",
                    if (plan.isOnTrack) SuccessColor else WarningColor,
                    Modifier.weight(1f)
                )
                goal.deadline?.let { dl ->
                    val deadlineLabel = runCatching { LocalDate.parse(dl.take(10)) }
                        .getOrNull()?.let { "${it.dayOfMonth} ${it.month.name.take(3)} ${it.year}" } ?: dl.take(10)
                    GoalStat("Deadline", deadlineLabel, GoldAccent, Modifier.weight(1.5f))
                }
            }

            // Achievement plan milestones (only for goals with > 3 months)
            if (plan.milestones.isNotEmpty() && plan.monthsToAchieve > 3) {
                Spacer(Modifier.height(12.dp))
                Text("Achievement Roadmap", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                plan.milestones.take(3).forEach { (month, amount) ->
                    Row(
                        Modifier.padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Primary.copy(0.5f)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Month $month → ${amount.formatCurrency()}",
                            fontSize = 11.sp, color = TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Contribute button
            Button(
                onClick = onContribute,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Contribute Now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete Goal?") },
            text = { Text("\"${goal.title}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDelete = false }) {
                    Text("Delete", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
            containerColor = CardBg,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
private fun GoalStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(color.copy(0.08f), RoundedCornerShape(10.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Text(label, fontSize = 9.sp, color = TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CompletedGoalRow(goal: FinancialGoalDto) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SuccessColor.copy(0.07f))
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(goal.icon, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(goal.targetAmount.formatCurrency(), fontSize = 12.sp, color = SuccessColor)
            }
            Text("Achieved!", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
        }
    }
}

@Composable
private fun EmptyGoalsPlaceholder(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎯", fontSize = 60.sp)
        Spacer(Modifier.height(16.dp))
        Text("No goals yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add your first goal — a vacation, emergency fund, phone upgrade, or anything worth saving for.",
            fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add First Goal")
        }
    }
}

// ── Add Goal Dialog ────────────────────────────────────────────

private val GOAL_ICONS = listOf("🎯", "🏖️", "🏠", "🚗", "💍", "📱", "🎓", "✈️", "💰", "🏋️", "📚", "🎸")

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, desc: String, amount: Double, deadline: String?, icon: String, monthly: Double) -> Unit
) {
    var title       by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var targetAmt   by remember { mutableStateOf("") }
    var deadline    by remember { mutableStateOf("") }
    var monthlyTgt  by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("🎯") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = TextPrimary,
        title = { Text("New Goal", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Icon picker
                Text("Pick an icon", fontSize = 11.sp, color = TextMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(GOAL_ICONS) { ico ->
                        Box(
                            Modifier.size(40.dp)
                                .border(
                                    1.5.dp,
                                    if (ico == selectedIcon) Primary else BorderColor,
                                    RoundedCornerShape(10.dp)
                                )
                                .background(
                                    if (ico == selectedIcon) Primary.copy(0.15f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedIcon = ico },
                            contentAlignment = Alignment.Center
                        ) { Text(ico, fontSize = 18.sp) }
                    }
                }

                GoalTextField("Goal title *", title, { title = it })
                GoalTextField("Description (optional)", description, { description = it })
                GoalTextField("Target amount (₹) *", targetAmt, { targetAmt = it }, KeyboardType.Decimal)
                GoalTextField("Monthly target (₹)", monthlyTgt, { monthlyTgt = it }, KeyboardType.Decimal, hint = "Leave blank to auto-calculate")
                GoalTextField("Deadline (YYYY-MM-DD)", deadline, { deadline = it }, hint = "Optional — e.g. 2026-12-31")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = targetAmt.toDoubleOrNull() ?: return@TextButton
                    val monthly = monthlyTgt.toDoubleOrNull() ?: 0.0
                    onCreate(title.trim(), description.trim(), amt, deadline.ifBlank { null }, selectedIcon, monthly)
                },
                enabled = title.isNotBlank() && targetAmt.isNotBlank()
            ) {
                Text("Create", color = Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun GoalTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    hint: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = if (hint.isNotEmpty()) ({ Text(hint, fontSize = 11.sp) }) else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Primary,
            unfocusedBorderColor = BorderColor,
            focusedLabelColor    = Primary,
            cursorColor          = Primary,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary
        )
    )
}

// ── Contribute Dialog ──────────────────────────────────────────

@Composable
private fun ContributeDialog(
    goal: FinancialGoalDto,
    onDismiss: () -> Unit,
    onContribute: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var note   by remember { mutableStateOf("") }
    val remaining = goal.targetAmount - goal.currentAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = TextPrimary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goal.icon, fontSize = 24.sp)
                Spacer(Modifier.width(8.dp))
                Text("Contribute to ${goal.title}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Remaining: ${remaining.formatCurrency()} | Current: ${goal.currentAmount.formatCurrency()}",
                    fontSize = 12.sp, color = TextSecondary
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedLabelColor = Primary, cursorColor = Primary,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedLabelColor = Primary, cursorColor = Primary,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                // Quick amounts
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(500.0, 1000.0, 5000.0, remaining).forEach { q ->
                        FilterChip(
                            selected = amount == q.roundToInt().toString(),
                            onClick = { amount = q.roundToInt().toString() },
                            label = { Text(if (q == remaining) "Full" else "₹${q.roundToInt()}", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor = Color.White,
                                containerColor = CardBg2,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: return@TextButton
                    onContribute(amt, note.trim())
                },
                enabled = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Confirm", color = SuccessColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
