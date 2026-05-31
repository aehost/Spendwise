package com.spendwise.app.presentation.screens.loans

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.LoanDto
import com.spendwise.app.presentation.theme.*

@Composable
fun LoansScreen(vm: LoansViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(Background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                Text("Loans & EMI", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(20.dp))
            }
            if (state.loans.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(0.12f))) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Avalanche Strategy: Pay highest rate first", fontSize = 12.sp, color = WarningColor)
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("Total EMI / month", fontSize = 11.sp, color = TextMuted); Text(state.loans.sumOf { it.emiAmount }.formatCurrency(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ErrorColor) }
                                Column(horizontalAlignment = Alignment.End) { Text("Total Outstanding", fontSize = 11.sp, color = TextMuted); Text(state.loans.sumOf { it.outstanding }.formatCurrency(), fontSize = 14.sp, color = TextSecondary) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(state.loans) { loan -> LoanItem(loan, onDelete = vm::delete) }
            if (state.loans.isEmpty() && !state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No loans added.\nTap + to add EMI / loan.", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }

        FloatingActionButton(onClick = vm::showAddDialog, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Primary) {
            Icon(Icons.Filled.Add, "Add Loan")
        }

        if (state.showDialog) AddLoanDialog(onDismiss = vm::hideDialog, onAdd = vm::addLoan)
    }
}

@Composable
fun LoanItem(loan: LoanDto, onDelete: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Brand tile when the loan references a bank, else a neutral EMI tile.
                val brand = com.spendwise.app.presentation.components.BankBrands.find(loan.name)
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (brand != null) Brush.linearGradient(brand.gradient) else Brush.linearGradient(GradientRose)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(brand?.mark ?: "EMI", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                }
                Spacer(Modifier.width(10.dp))
                Text(loan.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                Text("${loan.interestRate}% p.a.", fontSize = 12.sp, color = ErrorColor, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("EMI / month", fontSize = 11.sp, color = TextMuted); Text(loan.emiAmount.formatCurrency(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Outstanding", fontSize = 11.sp, color = TextMuted); Text(loan.outstanding.formatCurrency(), fontSize = 14.sp, color = TextSecondary) }
                Column(horizontalAlignment = Alignment.End) { Text("Months Left", fontSize = 11.sp, color = TextMuted); Text("${loan.monthsRemaining}", fontSize = 14.sp, color = TextSecondary) }
            }
        }
    }
}

@Composable
fun AddLoanDialog(onDismiss: () -> Unit, onAdd: (name: String, emi: Double, rate: Double, outstanding: Double, months: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emi by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var outstanding by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text("Add Loan / EMI", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("Loan Name", name) { v: String -> name = v },
                    Triple("EMI Amount (₹)", emi) { v: String -> emi = v },
                    Triple("Interest Rate (%)", rate) { v: String -> rate = v },
                    Triple("Outstanding Amount (₹)", outstanding) { v: String -> outstanding = v },
                    Triple("Months Remaining", months) { v: String -> months = v },
                ).forEach { (label, value, onChange) ->
                    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onAdd(name, emi.toDoubleOrNull() ?: 0.0, rate.toDoubleOrNull() ?: 0.0, outstanding.toDoubleOrNull() ?: 0.0, months.toIntOrNull() ?: 0)
                onDismiss()
            }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
