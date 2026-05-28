package com.spendwise.app.presentation.screens.iou

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.IouEntryDto
import com.spendwise.app.data.remote.dto.IouSummaryDto
import com.spendwise.app.presentation.theme.*
import java.time.LocalDate

@Composable
fun IouScreen(onBack: () -> Unit, vm: IouViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp, 16.dp, 8.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Text("IOU Tracker", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, "Add", tint = Primary)
            }
        }

        // Net summary bar
        val netLent = state.summaries.sumOf { it.totalLent }
        val netBorrowed = state.summaries.sumOf { it.totalBorrowed }
        if (state.summaries.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NetChip("You're Owed", netLent.formatCurrency(), SuccessColor, Modifier.weight(1f))
                NetChip("You Owe", netBorrowed.formatCurrency(), ErrorColor, Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
        }

        TabRow(selectedTabIndex = selectedTab, containerColor = CardBg, contentColor = Primary) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; vm.loadEntries(false) }) {
                Text("Active", modifier = Modifier.padding(vertical = 12.dp))
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; vm.loadEntries(true) }) {
                Text("Settled", modifier = Modifier.padding(vertical = 12.dp))
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
            state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (selectedTab == 0) "🤝" else "✅", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (selectedTab == 0) "No active IOUs" else "No settled IOUs", fontSize = 16.sp, color = TextSecondary)
                    if (selectedTab == 0) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Add Entry") }
                    }
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.entries) { entry ->
                    IouEntryCard(entry, onSettle = { vm.settle(entry.id) }, onDelete = { vm.delete(entry.id) })
                }
            }
        }
    }

    if (showAddDialog) {
        AddIouDialog(onDismiss = { showAddDialog = false }, onSubmit = { name, amount, dir, desc ->
            vm.create(name, amount, dir, desc)
            showAddDialog = false
        })
    }
}

@Composable
private fun NetChip(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(color.copy(0.1f), RoundedCornerShape(12.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
private fun IouEntryCard(entry: IouEntryDto, onSettle: () -> Unit, onDelete: () -> Unit) {
    val isLent = entry.direction == "lent"
    val color  = if (isLent) SuccessColor else ErrorColor
    val icon   = if (isLent) "💸" else "🤲"

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.contactName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    if (isLent) "You lent • ${entry.date}" else "You borrowed • ${entry.date}",
                    fontSize = 11.sp, color = TextMuted
                )
                entry.description?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (isLent) "+" else "-") + entry.amount.formatCurrency(),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color
                )
                if (!entry.isSettled) {
                    Row {
                        TextButton(onClick = onSettle, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Settle", color = SuccessColor, fontSize = 11.sp)
                        }
                        TextButton(onClick = onDelete, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("Delete", color = ErrorColor, fontSize = 11.sp)
                        }
                    }
                } else {
                    Text("✓ Settled", fontSize = 11.sp, color = SuccessColor)
                }
            }
        }
    }
}

@Composable
private fun AddIouDialog(onDismiss: () -> Unit, onSubmit: (String, Double, String, String?) -> Unit) {
    var name      by remember { mutableStateOf("") }
    var amount    by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("lent") }
    var desc      by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Add IOU Entry", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Contact Name", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount (₹)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description (optional)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = direction == "lent", onClick = { direction = "lent" }, label = { Text("💸 I Lent") })
                    FilterChip(selected = direction == "borrowed", onClick = { direction = "borrowed" }, label = { Text("🤲 I Borrowed") })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name.trim(), amount.toDoubleOrNull() ?: 0.0, direction, desc.ifBlank { null }) },
                enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
