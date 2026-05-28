package com.spendwise.app.presentation.screens.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.presentation.theme.*
import java.time.Month

@Composable
fun BudgetScreen(onBack: () -> Unit, vm: BudgetViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(4.dp, 16.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text(
                "Budget Planner",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        // Month selector
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = vm::prevMonth) {
                Icon(Icons.Filled.ChevronLeft, "Previous month", tint = TextSecondary)
            }
            val monthName = Month.of(state.month).name.lowercase().replaceFirstChar { it.uppercase() }
            Text(
                "$monthName ${state.year}",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            IconButton(onClick = vm::nextMonth) {
                Icon(Icons.Filled.ChevronRight, "Next month", tint = TextSecondary)
            }
        }

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            state.error != null -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error ?: "", color = ErrorColor)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = vm::load) { Text("Retry") }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Summary hero card
                    item {
                        BudgetSummaryCard(
                            totalBudget = state.totalBudget,
                            totalSpent  = state.totalSpent
                        )
                    }

                    item {
                        Text(
                            "Tap any category to set a budget",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Budgeted items
                    val budgeted   = state.items.filter { it.budget > 0 }
                    val unbudgeted = state.items.filter { it.budget == 0.0 && it.spent > 0 }

                    if (budgeted.isNotEmpty()) {
                        item {
                            Text(
                                "BUDGETED",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(budgeted) { item ->
                            BudgetItemCard(item = item, onClick = { vm.showEdit(item.slug, item.budget) })
                        }
                    }

                    if (unbudgeted.isNotEmpty()) {
                        item {
                            Text(
                                "NOT BUDGETED",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                            )
                        }
                        items(unbudgeted) { item ->
                            UnbudgetedItemCard(item = item, onClick = { vm.showEdit(item.slug, item.budget) })
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Edit budget dialog
    if (state.editSlug != null) {
        val item = state.items.find { it.slug == state.editSlug }
        AlertDialog(
            onDismissRequest = vm::hideEdit,
            containerColor  = CardBg,
            shape           = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item?.icon ?: "📦", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Set Budget: ${item?.label ?: state.editSlug}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item != null && item.spent > 0) {
                        Text(
                            "Spent this month: ${item.spent.formatCurrency()}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    OutlinedTextField(
                        value         = state.editAmount,
                        onValueChange = vm::onAmountChange,
                        label         = { Text("Budget Amount (₹)", color = TextSecondary) },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon   = { Text("₹", color = TextSecondary, fontWeight = FontWeight.Bold) },
                        modifier      = Modifier.fillMaxWidth(),
                        shape         = RoundedCornerShape(12.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Primary,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor     = TextPrimary,
                            unfocusedTextColor   = TextPrimary
                        )
                    )
                    if (state.error != null) {
                        Text(state.error ?: "", color = ErrorColor, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick  = vm::saveBudget,
                    enabled  = !state.isSaving,
                    colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Save Budget")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = vm::hideEdit) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun BudgetSummaryCard(totalBudget: Double, totalSpent: Double) {
    val overallPct   = if (totalBudget > 0) (totalSpent / totalBudget).coerceIn(0.0, 1.0).toFloat() else 0f
    val remaining    = (totalBudget - totalSpent).coerceAtLeast(0.0)
    val isOver       = totalBudget > 0 && totalSpent > totalBudget
    val barColor     = when {
        overallPct >= 1f    -> ErrorColor
        overallPct >= 0.8f  -> WarningColor
        else                -> SuccessColor
    }

    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(GradientAmber))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "MONTHLY BUDGET OVERVIEW",
                    fontSize     = 10.sp,
                    color        = Color.White.copy(0.65f),
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Bottom
                ) {
                    Column {
                        Text("Spent", fontSize = 11.sp, color = Color.White.copy(0.7f))
                        Text(
                            totalSpent.formatCurrency(),
                            fontSize    = 28.sp,
                            fontWeight  = FontWeight.Black,
                            color       = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Budget", fontSize = 11.sp, color = Color.White.copy(0.7f))
                        Text(
                            if (totalBudget > 0) totalBudget.formatCurrency() else "Not set",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (totalBudget > 0) {
                    Box(
                        Modifier.fillMaxWidth().height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(0.2f))
                    ) {
                        Box(
                            Modifier.fillMaxWidth(overallPct).height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isOver) Color(0xFFFF6B6B) else Color.White.copy(0.9f))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${(overallPct * 100).toInt()}% used",
                            fontSize = 11.sp,
                            color    = Color.White.copy(0.8f)
                        )
                        if (isOver) {
                            Text(
                                "Over budget by ${(totalSpent - totalBudget).formatCurrency()}",
                                fontSize   = 11.sp,
                                color      = Color(0xFFFF6B6B),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                "${remaining.formatCurrency()} remaining",
                                fontSize = 11.sp,
                                color    = Color.White.copy(0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetItemCard(item: BudgetItem, onClick: () -> Unit) {
    val barColor = when {
        item.pct >= 1f   -> ErrorColor
        item.pct >= 0.8f -> WarningColor
        item.pct >= 0.6f -> WarningColor.copy(0.7f)
        else             -> SuccessColor
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp)
                        .background(Primary.copy(0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text(item.icon, fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(
                        "${item.spent.formatCurrency()} of ${item.budget.formatCurrency()}",
                        fontSize = 11.sp,
                        color    = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (item.isOver) {
                        Box(
                            Modifier.background(ErrorColor.copy(0.15f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Over", fontSize = 10.sp, color = ErrorColor, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "${(item.pct * 100).toInt()}%",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color      = barColor
                        )
                    }
                    Text(
                        if (item.isOver) "−${(item.spent - item.budget).formatCurrency()}"
                        else "${item.remaining.formatCurrency()} left",
                        fontSize = 10.sp,
                        color    = if (item.isOver) ErrorColor else TextMuted
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BorderColor)
            ) {
                Box(
                    Modifier.fillMaxWidth(item.pct).height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(
                            when {
                                item.pct >= 1f   -> listOf(ErrorColor, Color(0xFFFF4D4D))
                                item.pct >= 0.8f -> listOf(WarningColor, Color(0xFFFFD166))
                                else             -> listOf(SuccessColor.copy(0.7f), SuccessColor)
                            }
                        ))
                )
            }
        }
    }
}

@Composable
private fun UnbudgetedItemCard(item: BudgetItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp)
                    .background(CardBg2, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text(item.icon, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Tap to set a budget", fontSize = 11.sp, color = TextMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    item.spent.formatCurrency(),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = TextPrimary
                )
                Text("spent", fontSize = 10.sp, color = TextMuted)
            }
        }
    }
}
