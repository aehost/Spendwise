package com.spendwise.app.presentation.screens.cards

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.CreditCardDto
import com.spendwise.app.presentation.theme.*

@Composable
fun CardsScreen(vm: CardsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Box(Modifier.fillMaxSize().background(Background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Credit Cards", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
            }
            if (state.cards.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Secondary.copy(0.12f))) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Total Outstanding", fontSize = 13.sp, color = TextSecondary)
                            Text(state.cards.sumOf { it.outstanding }.formatCurrency(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Secondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            items(state.cards) { card -> CreditCardItem(card, onDelete = vm::delete) }
            if (state.cards.isEmpty() && !state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("No credit cards added.\nTap + to add your first card.", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }

        FloatingActionButton(onClick = vm::showAddDialog, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Primary) {
            Icon(Icons.Filled.Add, "Add Card")
        }

        if (state.showDialog) AddCardDialog(onDismiss = vm::hideDialog, onAdd = vm::addCard)
    }
}

@Composable
fun CreditCardItem(card: CreditCardDto, onDelete: (String) -> Unit) {
    val utilPct = if (card.creditLimit > 0) (card.outstanding / card.creditLimit * 100).toInt() else 0
    val utilColor = when { utilPct >= 80 -> ErrorColor; utilPct >= 50 -> WarningColor; else -> SuccessColor }

    Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(card.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Due: ${card.dueDay}", fontSize = 12.sp, color = TextMuted)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Outstanding", fontSize = 11.sp, color = TextMuted)
                    Text(card.outstanding.formatCurrency(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ErrorColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Limit", fontSize = 11.sp, color = TextMuted)
                    Text(card.creditLimit.formatCurrency(), fontSize = 14.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (utilPct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = utilColor, trackColor = BorderColor
            )
            Text("$utilPct% utilized  •  Available: ${(card.creditLimit - card.outstanding).formatCurrency()}", fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun AddCardDialog(onDismiss: () -> Unit, onAdd: (name: String, limit: Double, dueDay: Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = { Text("Add Credit Card", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Card Name", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("Credit Limit (₹)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Due Day (1-31)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, limit.toDoubleOrNull() ?: 0.0, dueDay.toIntOrNull() ?: 1); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
