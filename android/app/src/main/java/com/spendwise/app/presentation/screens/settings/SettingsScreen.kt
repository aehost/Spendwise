package com.spendwise.app.presentation.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.presentation.theme.*

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onMonthlyReport: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    // Dialog visibility
    var showLogoutConfirm    by remember { mutableStateOf(false) }
    var showSalaryDialog     by remember { mutableStateOf(false) }
    var showPasswordDialog   by remember { mutableStateOf(false) }
    var showTicketDialog     by remember { mutableStateOf(false) }
    var showGmailDisconnect  by remember { mutableStateOf(false) }

    // Salary inputs
    var salaryInput    by remember { mutableStateOf("") }
    var salaryDayInput by remember { mutableStateOf("1") }

    // Gmail launcher
    val gmailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onGmailSignInResult(result.data)
    }

    // Show password change success snackbar
    LaunchedEffect(state.passwordChangeSuccess) {
        if (state.passwordChangeSuccess) {
            showPasswordDialog = false
            vm.clearPasswordState()
        }
    }
    // Show ticket success
    LaunchedEffect(state.ticketSuccess) {
        if (state.ticketSuccess) {
            showTicketDialog = false
            vm.clearTicketState()
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(20.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {

            // ── Profile ──────────────────────────────────────────
            item {
                SettingsSection("Profile") {
                    SettingsRow(Icons.Filled.Person, "Account", state.email ?: "Not logged in")
                    HorizontalDivider(color = BorderColor.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                    SettingsRow(Icons.Filled.Lock, "Change Password", "Update your login password",
                        onClick = { showPasswordDialog = true })
                }
            }

            // ── Salary ───────────────────────────────────────────
            item {
                SettingsSection("Salary") {
                    SettingsRow(Icons.Filled.AttachMoney, "Monthly Salary",
                        state.salaryAmount?.let { "₹${it.toLong()}" } ?: "Not set",
                        onClick = { salaryInput = state.salaryAmount?.toString() ?: ""; salaryDayInput = state.salaryDay?.toString() ?: "1"; showSalaryDialog = true })
                }
            }

            // ── Gmail ─────────────────────────────────────────────
            item {
                SettingsSection("Gmail Sync") {
                    if (state.gmailConnected) {
                        SettingsRow(Icons.Filled.Email, "Gmail Connected", state.gmailEmail ?: "Connected",
                            onClick = { showGmailDisconnect = true })
                    } else {
                        SettingsRow(Icons.Filled.Email, "Connect Gmail", "Scan credit card bills from email",
                            onClick = {
                                val intent = vm.getGmailSignInIntent()
                                gmailLauncher.launch(intent)
                            })
                    }
                    if (state.gmailLoading) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Primary, strokeWidth = 2.dp)
                        }
                    }
                    state.gmailError?.let {
                        Text(it, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    }
                }
            }

            // ── Reports ───────────────────────────────────────────
            item {
                SettingsSection("Reports") {
                    SettingsRow(Icons.Filled.Assessment, "Monthly Report", "Download spending analysis by month",
                        onClick = onMonthlyReport)
                }
            }

            // ── Data ─────────────────────────────────────────────
            item {
                SettingsSection("Data") {
                    SettingsRow(Icons.Filled.Sync, "Sync SMS Scan", "SMS inbox scan: ${if (state.smsScanFromMs > 0) "Active" else "Not set"}")
                    HorizontalDivider(color = BorderColor.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 14.dp))
                    SettingsRow(Icons.Filled.Refresh, "Refresh Data", onClick = vm::refresh)
                }
            }

            // ── Support ───────────────────────────────────────────
            item {
                SettingsSection("Support") {
                    SettingsRow(Icons.Filled.SupportAgent, "Create Support Ticket", "Report an issue or ask for help",
                        onClick = { showTicketDialog = true })
                }
            }

            // ── Danger zone ───────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(0.08f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Danger Zone", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ErrorColor)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { showLogoutConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorColor.copy(0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Logout", color = ErrorColor) }
                    }
                }
            }
        }
    }

    // ── Logout confirm ─────────────────────────────────────────
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false }, containerColor = CardBg,
            title = { Text("Logout?", color = TextPrimary) },
            text  = { Text("Are you sure you want to logout?", color = TextSecondary) },
            confirmButton = { Button(onClick = { vm.logout(); onLogout() }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Logout") } },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    // ── Salary dialog ─────────────────────────────────────────
    if (showSalaryDialog) {
        AlertDialog(
            onDismissRequest = { showSalaryDialog = false }, containerColor = CardBg,
            title = { Text("Update Salary", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = salaryInput, onValueChange = { salaryInput = it }, label = { Text("Monthly Salary (₹)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = salaryDayInput, onValueChange = { salaryDayInput = it }, label = { Text("Expected Day (1-31)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { vm.updateSalary(salaryInput.toDoubleOrNull() ?: 0.0, salaryDayInput.toIntOrNull() ?: 1); showSalaryDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSalaryDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    // ── Change Password dialog ─────────────────────────────────
    if (showPasswordDialog) {
        ChangePasswordDialog(
            isLoading = state.passwordChanging,
            error     = state.passwordChangeError,
            onDismiss = { showPasswordDialog = false; vm.clearPasswordState() },
            onSubmit  = { cur, nw -> vm.changePassword(cur, nw) }
        )
    }

    // ── Support ticket dialog ──────────────────────────────────
    if (showTicketDialog) {
        SupportTicketDialog(
            isLoading = state.ticketSending,
            error     = state.ticketError,
            onDismiss = { showTicketDialog = false; vm.clearTicketState() },
            onSubmit  = { subject, desc, cat -> vm.createSupportTicket(subject, desc, cat) }
        )
    }

    // ── Gmail disconnect confirm ───────────────────────────────
    if (showGmailDisconnect) {
        AlertDialog(
            onDismissRequest = { showGmailDisconnect = false }, containerColor = CardBg,
            title = { Text("Disconnect Gmail?", color = TextPrimary) },
            text  = { Text("Gmail bill scanning will be disabled.", color = TextSecondary) },
            confirmButton = { Button(onClick = { vm.disconnectGmail(); showGmailDisconnect = false }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { showGmailDisconnect = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

// ── Reusable composables ──────────────────────────────────────

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), fontSize = 11.sp, color = TextMuted, letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
            content()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SettingsRow(icon: ImageVector, label: String, value: String = "", onClick: (() -> Unit)? = null) {
    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, label, tint = Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = TextPrimary)
            if (value.isNotEmpty()) Text(value, fontSize = 12.sp, color = TextSecondary)
        }
        if (onClick != null) {
            IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.ChevronRight, "", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ChangePasswordDialog(
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var currentPw      by remember { mutableStateOf("") }
    var newPw          by remember { mutableStateOf("") }
    var confirmPw      by remember { mutableStateOf("") }
    var showCurrent    by remember { mutableStateOf(false) }
    var showNew        by remember { mutableStateOf(false) }
    var localError     by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Change Password", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currentPw, onValueChange = { currentPw = it },
                    label = { Text("Current Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showCurrent = !showCurrent }) { Text(if (showCurrent) "Hide" else "Show", fontSize = 11.sp, color = Primary) } }
                )
                OutlinedTextField(
                    value = newPw, onValueChange = { newPw = it },
                    label = { Text("New Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showNew = !showNew }) { Text(if (showNew) "Hide" else "Show", fontSize = 11.sp, color = Primary) } }
                )
                OutlinedTextField(
                    value = confirmPw, onValueChange = { confirmPw = it },
                    label = { Text("Confirm New Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                (localError ?: error)?.let {
                    Text(it, color = ErrorColor, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    localError = null
                    when {
                        currentPw.isBlank() -> localError = "Enter current password"
                        newPw.length < 8    -> localError = "New password must be at least 8 characters"
                        newPw != confirmPw  -> localError = "Passwords do not match"
                        else                -> onSubmit(currentPw, newPw)
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                else Text("Change Password")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun SupportTicketDialog(
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String?) -> Unit
) {
    val categories = listOf("General", "Bug Report", "Billing", "Feature Request", "Other")
    var subject     by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category    by remember { mutableStateOf(categories[0]) }
    var expanded    by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Create Support Ticket", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Describe your issue", color = TextSecondary) }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                // Category dropdown
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true, label = { Text("Category", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat, color = TextPrimary) }, onClick = { category = cat; expanded = false })
                        }
                    }
                }
                error?.let { Text(it, color = ErrorColor, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (subject.isNotBlank() && description.isNotBlank()) onSubmit(subject.trim(), description.trim(), category) },
                enabled = !isLoading && subject.isNotBlank() && description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                else Text("Submit Ticket")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
