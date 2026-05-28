package com.spendwise.app.presentation.screens.cashflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.CashFlowDayDto
import com.spendwise.app.presentation.theme.*

@Composable
fun CashFlowScreen(onBack: () -> Unit, vm: CashFlowViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp, 16.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Text("Cash Flow Forecast", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
            state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(state.error ?: "", color = ErrorColor)
                Button(onClick = vm::load, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Retry") }
            }
            else -> {
                val cf = state.cashFlow
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        // Summary card
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Monthly Overview", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SummaryChip("Balance Now", cf?.startingBalance?.formatCurrency() ?: "₹0", SuccessColor, Modifier.weight(1f))
                                    SummaryChip("Bills/mo", cf?.monthlyBillsTotal?.formatCurrency() ?: "₹0", WarningColor, Modifier.weight(1f))
                                    SummaryChip("EMI/mo", cf?.monthlyEmiTotal?.formatCurrency() ?: "₹0", ErrorColor, Modifier.weight(1f))
                                }
                                Text(
                                    "Salary of ${cf?.salaryAmount?.formatCurrency() ?: "₹0"} expected on ${cf?.salaryDay}th each month. " +
                                    "Daily spend estimate: ${cf?.dailySpendEstimate?.formatCurrency() ?: "₹0"}/day.",
                                    fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
                                )
                            }
                        }
                    }

                    item { Text("Next 90 Days", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextMuted) }

                    items(state.cashFlow?.events ?: emptyList()) { day ->
                        CashFlowDayCard(day)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(color.copy(0.1f), RoundedCornerShape(12.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun CashFlowDayCard(day: CashFlowDayDto) {
    val balColor = when {
        day.isNegative   -> ErrorColor
        day.isLowBalance -> WarningColor
        else             -> SuccessColor
    }
    val borderColor = if (day.isNegative) ErrorColor else if (day.isLowBalance) WarningColor else BorderColor

    Card(
        Modifier.fillMaxWidth().border(1.dp, borderColor.copy(0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(day.date, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (day.isLowBalance) Text("⚠️ Low balance day", fontSize = 11.sp, color = WarningColor)
                    if (day.isNegative)   Text("🚨 Negative balance", fontSize = 11.sp, color = ErrorColor)
                }
                Text(day.projectedBalance.formatCurrency(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = balColor)
            }
            if (day.events.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                day.events.forEach { ev ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ev.icon, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(ev.label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text(
                            (if (ev.type == "credit") "+" else "-") + ev.amount.formatCurrency(),
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (ev.type == "credit") SuccessColor else ErrorColor
                        )
                    }
                }
            }
        }
    }
}
