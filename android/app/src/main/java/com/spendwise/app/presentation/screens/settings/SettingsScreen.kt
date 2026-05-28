package com.spendwise.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    var showLogoutConfirm  by remember { mutableStateOf(false) }
    var showSalaryDialog   by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showTicketDialog   by remember { mutableStateOf(false) }
    var showGmailDialog    by remember { mutableStateOf(false) }

    var salaryInput    by remember { mutableStateOf("") }
    var salaryDayInput by remember { mutableStateOf("1") }

    LaunchedEffect(state.passwordChangeSuccess) {
        if (state.passwordChangeSuccess) { showPasswordDialog = false; vm.clearPasswordState() }
    }
    LaunchedEffect(state.ticketSuccess) {
        if (state.ticketSuccess) { showTicketDialog = false; vm.clearTicketState() }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            modifier = Modifier.padding(20.dp))

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
                        onClick = {
                            salaryInput    = state.salaryAmount?.toString() ?: ""
                            salaryDayInput = state.salaryDay?.toString() ?: "1"
                            showSalaryDialog = true
                        })
                }
            }

            // ── Gmail Sync ───────────────────────────────────────
            item {
                SettingsSection("Gmail Sync") {
                    // Info banner if no accounts
                    if (state.gmailAccounts.isEmpty() && !state.gmailLoading) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Scan financial emails automatically",
                                fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text(
                                "SpendWise reads your Gmail to auto-detect salary credits, CC statements, " +
                                "IMPS/NEFT transfers and bill amounts — no passwords shared.",
                                fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { showGmailDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Connect Gmail Account") }
                        }
                    }

                    // Connected accounts
                    state.gmailAccounts.forEach { account ->
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(36.dp).background(SuccessColor.copy(0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Filled.Email, "Gmail", tint = SuccessColor, modifier = Modifier.size(18.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.gmailEmail, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                val sync = account.lastSyncedAt?.take(10)?.let { "Synced $it" } ?: "Not synced yet"
                                Text(sync, fontSize = 11.sp, color = TextMuted)
                            }
                            IconButton(onClick = { vm.removeGmailAccount(account.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, "Remove", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(color = BorderColor.copy(0.3f), thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 14.dp))
                    }

                    // Add / Sync Now row (when accounts exist)
                    if (state.gmailAccounts.isNotEmpty()) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 6.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showGmailDialog = true }) {
                                Icon(Icons.Filled.Add, "", tint = Primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Account", color = Primary, fontSize = 13.sp)
                            }
                            TextButton(onClick = vm::syncGmailNow, enabled = !state.gmailSyncing) {
                                if (state.gmailSyncing) {
                                    CircularProgressIndicator(Modifier.size(12.dp), color = Primary, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(if (state.gmailSyncing) "Syncing…" else "Sync Now", color = Primary, fontSize = 13.sp)
                            }
                        }
                    }

                    if (state.gmailLoading) {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = Primary, strokeWidth = 2.dp)
                        }
                    }
                    state.gmailError?.let {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 4.dp).fillMaxWidth().clickable { vm.clearGmailError() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ErrorOutline, "", tint = ErrorColor, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(it, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ── Reports ───────────────────────────────────────────
            item {
                SettingsSection("Reports") {
                    SettingsRow(Icons.Filled.Assessment, "Monthly Report", "Detailed spending analysis by month",
                        onClick = onMonthlyReport)
                }
            }

            // ── Data ─────────────────────────────────────────────
            item {
                SettingsSection("Data") {
                    SettingsRow(Icons.Filled.Refresh, "Refresh All Data", onClick = vm::refresh)
                }
            }

            // ── Support ───────────────────────────────────────────
            item {
                SettingsSection("Support") {
                    SettingsRow(Icons.Filled.SupportAgent, "Create Support Ticket", "Report an issue or request help",
                        onClick = { showTicketDialog = true })
                }
            }

            // ── Danger zone ───────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(0.08f))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Danger Zone", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ErrorColor)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showLogoutConfirm = true }, modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorColor.copy(0.15f)),
                            shape = RoundedCornerShape(12.dp)) { Text("Logout", color = ErrorColor) }
                    }
                }
            }
        }
    }

    // ── Dialogs ─────────────────────────────────────────────────

    if (showLogoutConfirm) {
        AlertDialog(onDismissRequest = { showLogoutConfirm = false }, containerColor = CardBg,
            title = { Text("Logout?", color = TextPrimary) },
            text  = { Text("Are you sure you want to logout?", color = TextSecondary) },
            confirmButton = { Button(onClick = { vm.logout(); onLogout() }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Logout") } },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", color = TextSecondary) } })
    }

    if (showSalaryDialog) {
        AlertDialog(onDismissRequest = { showSalaryDialog = false }, containerColor = CardBg,
            title = { Text("Update Salary", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = salaryInput, onValueChange = { salaryInput = it },
                        label = { Text("Monthly Salary (₹)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = salaryDayInput, onValueChange = { salaryDayInput = it },
                        label = { Text("Expected Credit Day (1-31)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { vm.updateSalary(salaryInput.toDoubleOrNull() ?: 0.0, salaryDayInput.toIntOrNull() ?: 1); showSalaryDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSalaryDialog = false }) { Text("Cancel", color = TextSecondary) } })
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(isLoading = state.passwordChanging, error = state.passwordChangeError,
            onDismiss = { showPasswordDialog = false; vm.clearPasswordState() },
            onSubmit  = { cur, nw -> vm.changePassword(cur, nw) })
    }

    if (showTicketDialog) {
        SupportTicketDialog(isLoading = state.ticketSending, error = state.ticketError,
            onDismiss = { showTicketDialog = false; vm.clearTicketState() },
            onSubmit  = { s, d, c -> vm.createSupportTicket(s, d, c) })
    }

    if (showGmailDialog) {
        GmailConnectDialog(
            deviceAccounts  = state.deviceGoogleAccounts,
            alreadyAdded    = state.gmailAccounts.map { it.gmailEmail },
            onDismiss       = { showGmailDialog = false },
            onConnect       = { email -> vm.connectGmail(email); showGmailDialog = false }
        )
    }
}

// ── Gmail Connect Dialog ──────────────────────────────────────

@Composable
private fun GmailConnectDialog(
    deviceAccounts: List<String>,
    alreadyAdded: List<String>,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    var manualEmail by remember { mutableStateOf("") }
    val available = deviceAccounts.filter { it !in alreadyAdded }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Connect Gmail", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Select a Google account signed into this device, or enter your Gmail address manually.",
                    fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp
                )
                // Device accounts as tappable chips
                if (available.isNotEmpty()) {
                    Text("On this device:", fontSize = 12.sp, color = TextMuted)
                    available.forEach { email ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(Primary.copy(0.08f), RoundedCornerShape(10.dp))
                                .clickable { onConnect(email) }
                                .padding(12.dp, 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Email, "", tint = Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(email, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, "", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                    Text("Or enter manually:", fontSize = 12.sp, color = TextMuted)
                }
                OutlinedTextField(
                    value = manualEmail, onValueChange = { manualEmail = it },
                    label = { Text("Gmail address", color = TextSecondary) },
                    placeholder = { Text("you@gmail.com", color = TextMuted) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Text(
                    "Make sure this Gmail account is already signed into your device.",
                    fontSize = 11.sp, color = TextMuted, lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (manualEmail.isNotBlank()) onConnect(manualEmail.trim()) },
                enabled = manualEmail.isNotBlank() && manualEmail.contains("@"),
                colors  = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

// ── Reusable composables ──────────────────────────────────────

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), fontSize = 11.sp, color = TextMuted, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)) { content() }
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
fun ChangePasswordDialog(isLoading: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var currentPw   by remember { mutableStateOf("") }
    var newPw       by remember { mutableStateOf("") }
    var confirmPw   by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew     by remember { mutableStateOf(false) }
    var localError  by remember { mutableStateOf<String?>(null) }

    AlertDialog(onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Change Password", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = currentPw, onValueChange = { currentPw = it }, label = { Text("Current Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showCurrent = !showCurrent }) { Text(if (showCurrent) "Hide" else "Show", fontSize = 11.sp, color = Primary) } })
                OutlinedTextField(value = newPw, onValueChange = { newPw = it }, label = { Text("New Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showNew = !showNew }) { Text(if (showNew) "Hide" else "Show", fontSize = 11.sp, color = Primary) } })
                OutlinedTextField(value = confirmPw, onValueChange = { confirmPw = it }, label = { Text("Confirm Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                (localError ?: error)?.let { Text(it, color = ErrorColor, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                localError = null
                when {
                    currentPw.isBlank() -> localError = "Enter current password"
                    newPw.length < 8    -> localError = "Minimum 8 characters"
                    newPw != confirmPw  -> localError = "Passwords do not match"
                    else                -> onSubmit(currentPw, newPw)
                }
            }, enabled = !isLoading, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                else Text("Change Password")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } })
}

@Composable
fun SupportTicketDialog(isLoading: Boolean, error: String?, onDismiss: () -> Unit, onSubmit: (String, String, String?) -> Unit) {
    val categories = listOf("General", "Bug Report", "Billing", "Feature Request", "Other")
    var subject     by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category    by remember { mutableStateOf(categories[0]) }
    var expanded    by remember { mutableStateOf(false) }

    AlertDialog(onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Create Support Ticket", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Describe your issue", color = TextSecondary) }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Category", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
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
            Button(onClick = { if (subject.isNotBlank() && description.isNotBlank()) onSubmit(subject.trim(), description.trim(), category) },
                enabled = !isLoading && subject.isNotBlank() && description.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                else Text("Submit Ticket")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } })
}
