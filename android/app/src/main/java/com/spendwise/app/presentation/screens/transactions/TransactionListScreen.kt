package com.spendwise.app.presentation.screens.transactions

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.Constants
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.TransactionDto
import com.spendwise.app.presentation.screens.home.CATEGORY_META
import com.spendwise.app.presentation.screens.home.TransactionRow
import com.spendwise.app.presentation.theme.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(vm: TransactionViewModel = hiltViewModel()) {
    val state   by vm.state.collectAsState()
    val context = LocalContext.current

    var showAddSheet by remember { mutableStateOf(false) }
    var searchQuery  by remember { mutableStateOf("") }
    var selectedTx   by remember { mutableStateOf<TransactionDto?>(null) }

    // Local search over the loaded list (merchant + note + category).
    val visibleTx = remember(state.transactions, searchQuery) {
        if (searchQuery.isBlank()) state.transactions
        else state.transactions.filter { tx ->
            val q = searchQuery.trim().lowercase()
            tx.merchant.lowercase().contains(q) ||
                tx.note.lowercase().contains(q) ||
                tx.categorySlug.lowercase().contains(q)
        }
    }

    // Group by transaction date, preserving the newest-first order.
    val grouped = remember(visibleTx) {
        visibleTx.groupBy { it.transactionDate }
            .toList()
            .sortedByDescending { it.first }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Transactions", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                    Text(
                        "${state.transactions.size} total" +
                            (if (searchQuery.isNotBlank()) " · ${visibleTx.size} matching" else ""),
                        fontSize = 12.sp, color = TextMuted
                    )
                }
                IconButton(
                    onClick = { exportCsv(context, state.transactions) },
                    modifier = Modifier
                        .size(42.dp)
                        .background(CardBg, CircleShape)
                        .border(0.5.dp, BorderColor, CircleShape)
                ) {
                    Icon(Icons.Filled.Share, "Export CSV", tint = Primary, modifier = Modifier.size(18.dp))
                }
            }

            // ── Search bar ────────────────────────────────────────
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search merchant, note or category", color = TextMuted, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    AnimatedVisibility(visible = searchQuery.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Primary,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor     = TextPrimary,
                    unfocusedTextColor   = TextPrimary,
                    cursorColor          = Primary,
                    focusedContainerColor   = CardBg.copy(0.4f),
                    unfocusedContainerColor = CardBg.copy(0.4f)
                )
            )

            // ── Filter chips ──────────────────────────────────────
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.categoryFilter == null && !state.pendingOnly,
                        onClick = { vm.setFilter(null) },
                        label = { Text("All") },
                        colors = chipColors()
                    )
                }
                item {
                    FilterChip(
                        selected = state.pendingOnly,
                        onClick = { vm.togglePending() },
                        label = { Text("⏳ Pending") },
                        colors = chipColors()
                    )
                }
                Constants.CATEGORIES.forEach { cat ->
                    item {
                        FilterChip(
                            selected = state.categoryFilter == cat,
                            onClick  = { vm.setFilter(cat) },
                            label    = { Text("${Constants.CATEGORY_ICONS[cat] ?: ""} ${Constants.CATEGORY_LABELS[cat] ?: cat}") },
                            colors   = chipColors()
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Summary card ──────────────────────────────────────
            if (state.totalDebit > 0 || state.totalCredit > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(CardBg, CardBg2)))
                        .border(0.5.dp, BorderColor, RoundedCornerShape(16.dp))
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryStat("Spent", state.totalDebit.formatCurrency(), ErrorColor)
                    StatDivider()
                    SummaryStat("Received", state.totalCredit.formatCurrency(), SuccessColor)
                    StatDivider()
                    SummaryStat(
                        "Net",
                        (state.totalCredit - state.totalDebit).formatCurrency(),
                        if (state.totalCredit >= state.totalDebit) SuccessColor else WarningColor
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── List ──────────────────────────────────────────────
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }

                state.transactions.isEmpty() -> EmptyTransactions(onAdd = { showAddSheet = true })

                visibleTx.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No matches for \"$searchQuery\"", color = TextSecondary, fontSize = 14.sp)
                    }
                }

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    grouped.forEach { (date, txs) ->
                        item(key = "header_$date") {
                            DayHeader(date = date, transactions = txs)
                        }
                        items(txs, key = { it.id }) { tx ->
                            Box(Modifier.clickable { selectedTx = tx }) {
                                TransactionRow(tx)
                            }
                        }
                    }
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────
        FloatingActionButton(
            onClick = com.spendwise.app.presentation.components.hapticClick { showAddSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = Primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Filled.Add, "Add Transaction")
        }
    }

    // ── Transaction action sheet (restores delete access) ─────────
    selectedTx?.let { tx ->
        TransactionActionSheet(
            tx = tx,
            onDismiss = { selectedTx = null },
            onDelete = {
                vm.delete(tx.id)
                selectedTx = null
            }
        )
    }

    // ── Add transaction dialog ────────────────────────────────────
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

// ── Day group header with per-day net ─────────────────────────────
@Composable
private fun DayHeader(date: String, transactions: List<TransactionDto>) {
    val net = transactions.sumOf { if (it.isCredit) it.amount else -it.amount }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            friendlyDayLabel(date),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )
        Text(
            (if (net >= 0) "+" else "−") + kotlin.math.abs(net).formatCurrency(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (net >= 0) SuccessColor else TextMuted
        )
    }
}

// ── Per-transaction action bottom sheet ───────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionActionSheet(
    tx: TransactionDto,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val meta = CATEGORY_META[tx.categorySlug]

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Category avatar
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (tx.isCredit) SuccessColor.copy(0.12f) else Primary.copy(0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) { Text(meta?.first ?: "📦", fontSize = 28.sp) }

            Spacer(Modifier.height(14.dp))
            Text(tx.merchant, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                (if (tx.isCredit) "+" else "−") + tx.amount.formatCurrency(),
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                color = if (tx.isCredit) SuccessColor else TextPrimary
            )

            Spacer(Modifier.height(18.dp))

            // Detail rows
            DetailRow("Category", meta?.second ?: tx.categorySlug)
            DetailRow("Date", friendlyDayLabel(tx.transactionDate))
            DetailRow("Type", if (tx.isCredit) "Credit (money in)" else "Debit (money out)")
            if (tx.isPending) DetailRow("Status", "Pending")
            if (tx.note.isNotBlank()) DetailRow("Note", tx.note)

            Spacer(Modifier.height(22.dp))

            if (!confirmDelete) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorColor.copy(0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorColor)
                ) {
                    Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Transaction", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text(
                    "Delete this transaction permanently?",
                    fontSize = 13.sp, color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { confirmDelete = false },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Cancel", color = TextSecondary) }
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)
                    ) { Text("Delete", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = TextMuted)
        Text(
            value, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

// ── Beautiful empty state ─────────────────────────────────────────
@Composable
private fun EmptyTransactions(onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(
                        Brush.linearGradient(listOf(Primary.copy(0.18f), Secondary.copy(0.10f))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.ReceiptLong, null, tint = Primary, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("No transactions yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Bank SMS and Gmail receipts import here automatically — or add one manually to get started.",
                fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp,
                modifier = Modifier.padding(horizontal = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add Transaction", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Small reusable pieces ─────────────────────────────────────────
@Composable
private fun SummaryStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextMuted)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(0.5.dp).height(28.dp).background(BorderColor))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Primary.copy(0.18f),
    selectedLabelColor     = Primary,
    containerColor         = CardBg,
    labelColor             = TextSecondary
)

// Kept for backward-compat with any external references.
@Composable
fun SummaryChip(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.12f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 11.sp, color = color)
            Text(value, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────
private fun friendlyDayLabel(date: String): String = try {
    val d = LocalDate.parse(date)
    val today = LocalDate.now()
    when (d) {
        today               -> "TODAY"
        today.minusDays(1)  -> "YESTERDAY"
        else -> {
            val dow = d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
            val mon = d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
            "$dow, ${d.dayOfMonth} $mon"
        }
    }
} catch (_: Exception) { date }

private fun exportCsv(context: android.content.Context, transactions: List<TransactionDto>) {
    val csvText = buildString {
        appendLine("Date,Merchant,Amount,Type,Category,Note")
        transactions.forEach { tx ->
            appendLine("${tx.transactionDate},\"${tx.merchant}\",${tx.amount},${if (tx.isCredit) "Credit" else "Debit"},${tx.categorySlug},\"${tx.note}\"")
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, csvText)
        putExtra(Intent.EXTRA_SUBJECT, "SpendWise Transactions Export")
    }
    context.startActivity(Intent.createChooser(intent, "Export Transactions"))
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
