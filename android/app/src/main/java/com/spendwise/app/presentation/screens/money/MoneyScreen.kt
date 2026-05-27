package com.spendwise.app.presentation.screens.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.InvestmentDto
import com.spendwise.app.presentation.theme.*

@Composable
fun MoneyScreen(vm: MoneyViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var showInvestDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            item { Text("Money", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(20.dp)) }

            // Salary card
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("💰 Salary", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            if (state.salaryAmount > 0) Button(onClick = vm::markSalaryReceived, colors = ButtonDefaults.buttonColors(containerColor = SuccessColor), modifier = Modifier.height(34.dp)) { Text("Mark Received", fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (state.salaryAmount > 0) {
                            Text(state.salaryAmount.formatCurrency(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = SuccessColor)
                            Text("Expected on day ${state.salaryDay} of each month", fontSize = 12.sp, color = TextMuted)
                        } else {
                            Text("Salary not set", fontSize = 14.sp, color = TextSecondary)
                        }
                        Spacer(Modifier.height(12.dp))
                        // Savings rate
                        val savingsRate = if (state.salaryAmount > 0) ((state.salaryAmount - state.totalSpent) / state.salaryAmount * 100).toInt() else 0
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Savings Rate", fontSize = 11.sp, color = TextMuted); Text("$savingsRate%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (savingsRate >= 20) SuccessColor else WarningColor) }
                            Column(horizontalAlignment = Alignment.End) { Text("Target", fontSize = 11.sp, color = TextMuted); Text("20%", fontSize = 14.sp, color = TextSecondary) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Investments section
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("📈 Investments", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    IconButton(onClick = { showInvestDialog = true }) { Icon(Icons.Filled.Add, "Add", tint = Primary) }
                }
            }

            if (state.investments.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) { Text("No investments yet.", color = TextSecondary, fontSize = 14.sp) } }
            } else {
                items(state.investments) { inv ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(inv.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("${inv.monthlyAmount.formatCurrency()}/month", fontSize = 12.sp, color = TextMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Current", fontSize = 11.sp, color = TextMuted)
                                Text(inv.currentBalance.formatCurrency(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = SuccessColor.copy(0.12f))) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Portfolio Value", fontSize = 13.sp, color = SuccessColor)
                            Text(state.investments.sumOf { it.currentBalance }.formatCurrency(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                        }
                    }
                }
            }
        }

        if (showInvestDialog) {
            AddInvestmentDialog(onDismiss = { showInvestDialog = false }, onAdd = { name, monthly, current ->
                vm.addInvestment(name, monthly, current)
                showInvestDialog = false
            })
        }
    }
}

@Composable
fun AddInvestmentDialog(onDismiss: () -> Unit, onAdd: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var current by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Add Investment", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Zerodha, SIP)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = monthly, onValueChange = { monthly = it }, label = { Text("Monthly Amount (₹)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = current, onValueChange = { current = it }, label = { Text("Current Balance (₹)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, monthly.toDoubleOrNull() ?: 0.0, current.toDoubleOrNull() ?: 0.0) }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
