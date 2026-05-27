package com.spendwise.app.presentation.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.spendwise.app.core.Constants
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.TransactionDto
import com.spendwise.app.presentation.screens.home.TransactionRow
import com.spendwise.app.presentation.theme.*

@Composable
fun TransactionListScreen(vm: TransactionViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Transactions", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "${state.transactions.size} total",
                    fontSize = 13.sp, color = TextSecondary
                )
            }

            // Filter chips
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = state.categoryFilter == null, onClick = { vm.setFilter(null) }, label = { Text("All") })
                }
                item {
                    FilterChip(selected = state.pendingOnly, onClick = { vm.togglePending() }, label = { Text("Pending") })
                }
                Constants.CATEGORIES.forEach { cat ->
                    item {
                        FilterChip(
                            selected = state.categoryFilter == cat,
                            onClick  = { vm.setFilter(cat) },
                            label    = { Text("${Constants.CATEGORY_ICONS[cat] ?: ""} ${Constants.CATEGORY_LABELS[cat] ?: cat}") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Summary chips
            if (state.totalDebit > 0 || state.totalCredit > 0) {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryChip("Spent", state.totalDebit.formatCurrency(), ErrorColor)
                    SummaryChip("Received", state.totalCredit.formatCurrency(), SuccessColor)
                    if (state.pendingCount > 0)
                        SummaryChip("Pending", state.pendingCount.toString(), WarningColor)
                }
                Spacer(Modifier.height(8.dp))
            }

            // List
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
                state.transactions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No transactions found.\nSMS messages will appear here automatically.", color = TextSecondary, fontSize = 14.sp)
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(state.transactions) { tx ->
                        SwipeToDismissTransactionRow(tx, onDelete = { vm.delete(it) })
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = Primary
        ) {
            Icon(Icons.Filled.Add, "Add Transaction")
        }
    }
}

@Composable
fun SummaryChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.12f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 11.sp, color = color)
            Text(value, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SwipeToDismissTransactionRow(tx: TransactionDto, onDelete: (String) -> Unit) {
    // Simplified — no swipe gesture in this version
    TransactionRow(tx)
}
