package com.spendwise.app.presentation.screens.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.spendwise.app.core.Constants
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.TransactionDto
import com.spendwise.app.presentation.screens.home.TransactionRow
import com.spendwise.app.presentation.theme.*
import java.time.LocalDate

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

    // Add transaction dialog — shown when FAB is tapped
    if (showAddSheet) {
        AddTransactionDialog(
            onDismiss = { showAddSheet = false },
            onSave    = { amount, merchant, category, date, isCredit, note ->
                vm.createTransaction(amount, merchant, category, date, isCredit, note)
                showAddSheet = false
            }
        )
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

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (amount: Double, merchant: String, categorySlug: String, date: String, isCredit: Boolean, note: String) -> Unit
) {
    var amountText  by remember { mutableStateOf("") }
    var merchant    by remember { mutableStateOf("") }
    var note        by remember { mutableStateOf("") }
    var isCredit    by remember { mutableStateOf(false) }
    var dateText    by remember { mutableStateOf(LocalDate.now().toString()) }
    var catExpanded by remember { mutableStateOf(false) }
    var category    by remember { mutableStateOf("other") }
    var localError  by remember { mutableStateOf<String?>(null) }

    val categoryLabel = Constants.CATEGORY_LABELS[category] ?: category
    val categoryIcon  = Constants.CATEGORY_ICONS[category]  ?: "📦"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Add Transaction", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Credit / Debit toggle
                Row(
                    Modifier.fillMaxWidth()
                        .background(Background, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(false to "💸 Debit", true to "💵 Credit").forEach { (credit, label) ->
                        val selected = isCredit == credit
                        Box(
                            Modifier.weight(1f)
                                .background(
                                    if (selected) (if (credit) SuccessColor else ErrorColor).copy(0.15f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(onClick = { isCredit = credit }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (selected) (if (credit) SuccessColor else ErrorColor) else TextSecondary
                                )
                            }
                        }
                    }
                }

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₹)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Text("₹", color = TextSecondary, fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                // Merchant name
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / Description", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = !catExpanded }
                ) {
                    OutlinedTextField(
                        value = "$categoryIcon $categoryLabel",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category", color = TextSecondary) },
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, null, tint = TextSecondary,
                                modifier = Modifier.size(20.dp))
                        },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false },
                        modifier = Modifier.background(CardBg)
                    ) {
                        Constants.CATEGORIES.forEach { slug ->
                            val icon  = Constants.CATEGORY_ICONS[slug]  ?: "📦"
                            val label = Constants.CATEGORY_LABELS[slug] ?: slug
                            DropdownMenuItem(
                                text = { Text("$icon  $label", color = TextPrimary, fontSize = 13.sp) },
                                onClick = { category = slug; catExpanded = false }
                            )
                        }
                    }
                }

                // Date
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Date (YYYY-MM-DD)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(LocalDate.now().toString(), color = TextMuted) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                // Note (optional)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                localError?.let { Text(it, color = ErrorColor, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    localError = null
                    val amount = amountText.toDoubleOrNull()
                    when {
                        amount == null || amount <= 0 -> localError = "Enter a valid amount"
                        merchant.isBlank()            -> localError = "Enter a merchant / description"
                        dateText.isBlank()            -> localError = "Enter a date"
                        else -> {
                            val date = runCatching { LocalDate.parse(dateText).toString() }.getOrElse {
                                localError = "Date must be YYYY-MM-DD format"; return@Button
                            }
                            onSave(amount, merchant, category, date, isCredit, note)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save Transaction") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
