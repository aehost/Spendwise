package com.spendwise.app.presentation.screens.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.text.KeyboardOptions
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
import com.spendwise.app.data.remote.dto.CreditCardDto
import com.spendwise.app.presentation.theme.*
import kotlinx.coroutines.delay

@Composable
fun CardsScreen(vm: CardsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    // Auto-refresh every 30s — picks up outstanding updates made by SmsSyncWorker
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            vm.refresh()
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Credit Cards", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    // Manual refresh — useful after a CC bill SMS arrives
                    IconButton(onClick = vm::refresh) {
                        if (state.isLoading)
                            CircularProgressIndicator(Modifier.size(18.dp), color = Primary, strokeWidth = 2.dp)
                        else
                            Icon(Icons.Filled.Refresh, "Refresh", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
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

        FloatingActionButton(onClick = com.spendwise.app.presentation.components.hapticClick(vm::showAddDialog), modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = Primary) {
            Icon(Icons.Filled.Add, "Add Card")
        }

        if (state.showDialog) AddCardDialog(onDismiss = vm::hideDialog,
            onAdd = { name, limit, dueDay, lastFour -> vm.addCard(name, limit, dueDay, lastFour) })
    }
}

@Composable
fun CreditCardItem(card: CreditCardDto, onDelete: (String) -> Unit) {
    val utilPct   = if (card.creditLimit > 0) (card.outstanding / card.creditLimit * 100).toInt() else 0
    val utilColor = when { utilPct >= 80 -> ErrorColor; utilPct >= 50 -> WarningColor; else -> SuccessColor }
    val brand     = com.spendwise.app.presentation.components.BankBrands.of(card.name)
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {

        // ── The virtual card ─────────────────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .height(196.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(brand.gradient))
                .border(0.5.dp, Color.White.copy(0.18f), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            // subtle decorative arc
            Box(
                Modifier.size(150.dp).align(Alignment.TopEnd)
                    .background(Color.White.copy(0.06f), RoundedCornerShape(75.dp))
            )
            Column(Modifier.fillMaxSize()) {
                // Top: bank brand + delete
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Text(brand.label, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("CREDIT CARD", fontSize = 8.sp, color = Color.White.copy(0.65f), letterSpacing = 2.sp)
                    }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.DeleteOutline, "Remove card", tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(14.dp))
                // EMV chip
                Box(
                    Modifier.size(width = 42.dp, height = 30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFF4D67E), Color(0xFFC9A227))))
                )

                Spacer(Modifier.weight(1f))
                // Masked number
                Text(
                    "••••  ••••  ••••  ${card.lastFour ?: "••••"}",
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 2.sp
                )
                Spacer(Modifier.height(12.dp))
                // Bottom: outstanding + due
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("OUTSTANDING", fontSize = 8.sp, color = Color.White.copy(0.6f), letterSpacing = 1.sp)
                        Text(card.outstanding.formatCurrency(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("DUE DAY", fontSize = 8.sp, color = Color.White.copy(0.6f), letterSpacing = 1.sp)
                        Text("${card.dueDay}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // ── Utilization strip below the card ─────────────────────
        Spacer(Modifier.height(10.dp))
        com.spendwise.app.presentation.components.SwLinearProgress(
            progress   = (utilPct / 100f).coerceIn(0f, 1f),
            height     = 6.dp,
            color      = utilColor,
            trackColor = BorderColor
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$utilPct% utilized", fontSize = 11.sp, color = utilColor, fontWeight = FontWeight.Medium)
            Text("Available ${(card.creditLimit - card.outstanding).coerceAtLeast(0.0).formatCurrency()}", fontSize = 11.sp, color = TextMuted)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CardBg,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Remove ${brand.label} card?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("This only removes it from SpendWise tracking.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = {
                Button(onClick = { onDelete(card.id); confirmDelete = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

@Composable
fun AddCardDialog(onDismiss: () -> Unit, onAdd: (name: String, limit: Double, dueDay: Int, lastFour: String?) -> Unit) {
    var name     by remember { mutableStateOf("") }
    var limit    by remember { mutableStateOf("") }
    var dueDay   by remember { mutableStateOf("1") }
    var lastFour by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Add Credit Card", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Card Name (e.g. Axis Bank Platinum)", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = limit, onValueChange = { limit = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Credit Limit (₹)", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = dueDay, onValueChange = { dueDay = it.filter { c -> c.isDigit() } },
                    label = { Text("Payment Due Day (1–31)", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = lastFour, onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) lastFour = it },
                    label = { Text("Last 4 digits of card (for SMS matching)", color = TextSecondary) },
                    placeholder = { Text("e.g. 9156", color = TextMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Used to auto-match CC bill reminders", fontSize = 11.sp, color = TextMuted) })
            }
        },
        confirmButton = {
            Button(onClick = {
                onAdd(
                    name,
                    limit.toDoubleOrNull() ?: 0.0,
                    dueDay.toIntOrNull() ?: 1,
                    lastFour.takeIf { it.length == 4 }
                )
                onDismiss()
            }, colors = ButtonDefaults.buttonColors(containerColor = Primary), enabled = name.isNotBlank()) {
                Text("Add Card")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
