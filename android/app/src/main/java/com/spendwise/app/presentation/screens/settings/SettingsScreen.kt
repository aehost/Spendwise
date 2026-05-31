package com.spendwise.app.presentation.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.presentation.theme.*

// ── Google brand colours (not in theme — only used here) ──────
private val GmailRed   = Color(0xFFEA4335)
private val GmailBlue  = Color(0xFF4285F4)
private val GmailGreen = Color(0xFF34A853)

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
    var showGmailSheet     by remember { mutableStateOf(false) }

    var salaryInput    by remember { mutableStateOf("") }
    var salaryDayInput by remember { mutableStateOf("1") }

    LaunchedEffect(state.passwordChangeSuccess) {
        if (state.passwordChangeSuccess) { showPasswordDialog = false; vm.clearPasswordState() }
    }
    LaunchedEffect(state.ticketSuccess) {
        if (state.ticketSuccess) { showTicketDialog = false; vm.clearTicketState() }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        Text(
            "Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 10.dp)
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {

            // ── Profile hero ─────────────────────────────────────
            item {
                ProfileHeroCard(
                    name             = state.name,
                    email            = state.email,
                    onChangePassword = { showPasswordDialog = true }
                )
                Spacer(Modifier.height(10.dp))
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

            // ── Gmail Sync — Full Redesign ────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                if (state.gmailAccounts.isEmpty()) {
                    GmailEmptyStateCard(
                        isLoading  = state.gmailLoading,
                        error      = state.gmailError,
                        onConnect  = { showGmailSheet = true },
                        onClearError = vm::clearGmailError
                    )
                } else {
                    GmailConnectedCard(
                        accounts  = state.gmailAccounts,
                        syncing   = state.gmailSyncing,
                        onRemove  = { vm.removeGmailAccount(it) },
                        onSyncNow = vm::syncGmailNow,
                        onAddMore = { showGmailSheet = true }
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Reports ──────────────────────────────────────────
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

            // ── Support ──────────────────────────────────────────
            item {
                SettingsSection("Support") {
                    SettingsRow(Icons.Filled.SupportAgent, "Create Support Ticket",
                        "Report an issue or request help", onClick = { showTicketDialog = true })
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

    // ── Dialogs & sheets ─────────────────────────────────────────

    if (showLogoutConfirm) {
        AlertDialog(onDismissRequest = { showLogoutConfirm = false }, containerColor = CardBg,
            title = { Text("Logout?", color = TextPrimary) },
            text  = { Text("Are you sure you want to logout?", color = TextSecondary) },
            confirmButton = {
                Button(onClick = { vm.logout(); onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Logout") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", color = TextSecondary) } })
    }

    if (showSalaryDialog) {
        AlertDialog(onDismissRequest = { showSalaryDialog = false }, containerColor = CardBg,
            title = { Text("Update Salary", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = salaryInput, onValueChange = { salaryInput = it },
                        label = { Text("Monthly Salary (₹)", color = TextSecondary) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = salaryDayInput, onValueChange = { salaryDayInput = it },
                        label = { Text("Expected Credit Day (1–31)", color = TextSecondary) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateSalary(salaryInput.toDoubleOrNull() ?: 0.0, salaryDayInput.toIntOrNull() ?: 1)
                    showSalaryDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Save") }
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

    if (showGmailSheet) {
        GmailAutoCloseEffect(state) { showGmailSheet = false; vm.clearGmailError() }
        GmailConnectSheet(
            alreadyAdded = state.gmailAccounts.map { it.gmailEmail },
            isLoading    = state.gmailLoading,
            error        = state.gmailError,
            onDismiss    = { showGmailSheet = false; vm.clearGmailError() },
            onConnect    = { email, appPwd -> vm.connectGmailManual(email, appPwd) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// Gmail Empty State — Hero card
// ─────────────────────────────────────────────────────────────────

@Composable
private fun GmailEmptyStateCard(
    isLoading: Boolean,
    error: String?,
    onConnect: () -> Unit,
    onClearError: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            "GMAIL SYNC", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Dark hero card
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0E0A2E), Color(0xFF16104A), Color(0xFF0B1230))
                    )
                )
                .border(0.5.dp, Primary.copy(0.18f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Google "G" badge
                Box(
                    Modifier
                        .size(72.dp)
                        .background(Color.White.copy(0.07f), CircleShape)
                        .border(1.5.dp, GmailRed.copy(0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("G", fontSize = 38.sp, fontWeight = FontWeight.Black, color = GmailRed)
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    "Gmail Smart Sync",
                    fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    color = Color.White, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect Gmail to automatically import\nevery bank transaction from your inbox",
                    fontSize = 13.sp, color = Color.White.copy(0.6f),
                    textAlign = TextAlign.Center, lineHeight = 19.sp
                )

                Spacer(Modifier.height(24.dp))

                // Benefit rows
                val benefits = listOf(
                    "🏦" to "HDFC, ICICI, Axis, SBI + 10 more banks",
                    "💸" to "Debit, credit, UPI, NEFT, IMPS — all captured",
                    "📄" to "Bill payments & salary credits detected",
                    "🔐" to "App Password only — your Gmail password is never stored"
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(0.05f), RoundedCornerShape(16.dp))
                        .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    benefits.forEach { (emoji, text) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(emoji, fontSize = 18.sp)
                            Text(text, fontSize = 12.sp, color = Color.White.copy(0.75f), lineHeight = 16.sp)
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // CTA button — white on dark
                Button(
                    onClick   = onConnect,
                    modifier  = Modifier.fillMaxWidth().height(54.dp),
                    enabled   = !isLoading,
                    colors    = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape     = RoundedCornerShape(16.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp), color = Color(0xFF16104A), strokeWidth = 2.5.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Connecting…", fontWeight = FontWeight.Bold, color = Color(0xFF16104A), fontSize = 16.sp)
                    } else {
                        Text("G", fontSize = 20.sp, fontWeight = FontWeight.Black, color = GmailRed)
                        Spacer(Modifier.width(10.dp))
                        Text("Connect Gmail Account", fontWeight = FontWeight.Bold, color = Color(0xFF16104A), fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Filled.Lock, "", tint = Color.White.copy(0.35f), modifier = Modifier.size(11.dp))
                    Text("App Password only — Gmail password never stored", fontSize = 11.sp, color = Color.White.copy(0.35f))
                }
            }
        }

        // Error banner below card
        AnimatedVisibility(visible = error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(ErrorColor.copy(0.1f), RoundedCornerShape(14.dp))
                        .border(0.5.dp, ErrorColor.copy(0.3f), RoundedCornerShape(14.dp))
                        .clickable { onClearError() }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.ErrorOutline, "", tint = ErrorColor, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text(error ?: "", color = ErrorColor, fontSize = 12.sp, modifier = Modifier.weight(1f), lineHeight = 16.sp)
                    Icon(Icons.Filled.Close, "", tint = ErrorColor.copy(0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────
// Gmail Connected Card — rich account list
// ─────────────────────────────────────────────────────────────────

@Composable
private fun GmailConnectedCard(
    accounts: List<com.spendwise.app.data.remote.dto.GmailAccountDto>,
    syncing: Boolean,
    onRemove: (String) -> Unit,
    onSyncNow: () -> Unit,
    onAddMore: () -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        // Header row with section label + sync button
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GMAIL SYNC", fontSize = 11.sp, color = TextMuted, letterSpacing = 1.2.sp)
            // Live status badge
            AnimatedContent(targetState = syncing, label = "sync_badge") { isSyncing ->
                if (isSyncing) {
                    Row(
                        Modifier
                            .background(Primary.copy(0.12f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(8.dp), color = Primary, strokeWidth = 1.5.dp)
                        Text("Syncing…", fontSize = 10.sp, color = Primary, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(
                        Modifier
                            .background(SuccessColor.copy(0.12f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(Modifier.size(6.dp).background(SuccessColor, CircleShape))
                        Text("Active", fontSize = 10.sp, color = SuccessColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(Modifier.padding(16.dp)) {

                // Gmail branding header inside card
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(GmailRed.copy(0.1f), RoundedCornerShape(12.dp))
                            .border(0.5.dp, GmailRed.copy(0.25f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("G", fontSize = 20.sp, fontWeight = FontWeight.Black, color = GmailRed) }
                    Column(Modifier.weight(1f)) {
                        Text("Gmail Smart Sync", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("${accounts.size} account${if (accounts.size > 1) "s" else ""} connected · Every 30 min",
                            fontSize = 11.sp, color = TextMuted)
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = BorderColor.copy(0.4f))
                Spacer(Modifier.height(12.dp))

                // Account rows
                accounts.forEachIndexed { index, account ->
                    GmailAccountRow(account, onRemove = { onRemove(account.id) })
                    if (index < accounts.lastIndex) {
                        HorizontalDivider(
                            color = BorderColor.copy(0.3f),
                            modifier = Modifier.padding(start = 52.dp, top = 6.dp, bottom = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BorderColor.copy(0.4f))

                // Action row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onAddMore) {
                        Icon(Icons.Filled.Add, "", tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Account", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = onSyncNow,
                        enabled = !syncing,
                        colors = ButtonDefaults.buttonColors(containerColor = Primary.copy(0.13f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Filled.Sync, "", tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Sync Now", color = Primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GmailAccountRow(
    account: com.spendwise.app.data.remote.dto.GmailAccountDto,
    onRemove: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Initials avatar with unique colour per account
        val avatarColor = listOf(Primary, GmailBlue, GmailGreen, WarningColor)
            .getOrElse(account.gmailEmail.hashCode().and(0xFFFF).rem(4)) { Primary }
        Box(
            Modifier.size(40.dp).background(avatarColor.copy(0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                account.gmailEmail.take(2).uppercase(),
                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = avatarColor
            )
        }
        Column(Modifier.weight(1f)) {
            Text(account.gmailEmail, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(5.dp).background(SuccessColor, CircleShape))
                Text(
                    account.lastSyncedAt?.take(10)?.let { "Last synced: $it" } ?: "Awaiting first sync",
                    fontSize = 11.sp, color = TextMuted
                )
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Delete, "Remove", tint = ErrorColor.copy(0.55f), modifier = Modifier.size(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Gmail Connect Sheet — 3-step ModalBottomSheet wizard
// ─────────────────────────────────────────────────────────────────

// Auto-close: only fires after a live connection attempt succeeds
@Composable
private fun GmailAutoCloseEffect(state: SettingsState, onClose: () -> Unit) {
    var connectionAttempted by remember { mutableStateOf(false) }
    LaunchedEffect(state.gmailLoading) {
        if (state.gmailLoading) connectionAttempted = true
        else if (connectionAttempted && state.gmailError == null) onClose()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GmailConnectSheet(
    alreadyAdded: List<String>,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit
) {
    var step        by remember { mutableIntStateOf(0) }
    var email       by remember { mutableStateOf("") }
    var appPassword by remember { mutableStateOf("") }  // stored raw, no spaces, max 16
    var showPwd     by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Drag handle
            Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).background(BorderColor, RoundedCornerShape(2.dp)))
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "gmail_wizard"
            ) { currentStep ->
                when (currentStep) {
                    0 -> SheetStepWelcome(
                        onContinue = { step = 1 },
                        onDismiss  = onDismiss
                    )
                    1 -> SheetStepEmail(
                        email         = email,
                        onEmailChange = { email = it },
                        alreadyAdded  = alreadyAdded,
                        onBack        = { step = 0 },
                        onContinue    = { step = 2 }
                    )
                    2 -> SheetStepPassword(
                        email           = email,
                        appPassword     = appPassword,
                        showPwd         = showPwd,
                        isLoading       = isLoading,
                        error           = error,
                        onPasswordChange = { raw ->
                            appPassword = raw.filter { it != ' ' }.take(16)
                        },
                        onToggleShow    = { showPwd = !showPwd },
                        onBack          = { if (!isLoading) step = 1 },
                        onConnect       = { onConnect(email.trim(), appPassword) }
                    )
                }
            }
        }
    }
}

// ── Step 0: Welcome / Benefits ────────────────────────────────

@Composable
private fun SheetStepWelcome(onContinue: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 36.dp, top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Google G hero
        Box(
            Modifier
                .size(80.dp)
                .background(
                    Brush.radialGradient(listOf(GmailRed.copy(0.18f), GmailRed.copy(0.04f))),
                    CircleShape
                )
                .border(1.5.dp, GmailRed.copy(0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("G", fontSize = 42.sp, fontWeight = FontWeight.Black, color = GmailRed)
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Gmail Smart Sync",
            fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Automatically capture every bank transaction\nstraight from your Gmail inbox",
            fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp
        )

        Spacer(Modifier.height(28.dp))

        // Feature cards
        val features = listOf(
            Triple("🏦", "10+ Banks Supported", "HDFC, ICICI, Axis, SBI, Kotak & more"),
            Triple("⚡", "Every 30 Minutes", "Background sync while you go about your day"),
            Triple("🔐", "Zero-Knowledge", "App Password only — Gmail login never touched"),
            Triple("✨", "Smart Parsing",  "Amounts, merchants & categories auto-detected")
        )
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                features.forEach { (emoji, title, desc) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier.size(40.dp).background(Primary.copy(0.1f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 18.sp) }
                        Column {
                            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(desc, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick   = onContinue,
            modifier  = Modifier.fillMaxWidth().height(56.dp),
            colors    = ButtonDefaults.buttonColors(containerColor = Primary),
            shape     = RoundedCornerShape(16.dp)
        ) {
            Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, "", Modifier.size(18.dp))
        }

        TextButton(onClick = onDismiss) {
            Text("Maybe later", color = TextMuted, fontSize = 13.sp)
        }
    }
}

// ── Step 1: Gmail address ─────────────────────────────────────

@Composable
private fun SheetStepEmail(
    email: String,
    onEmailChange: (String) -> Unit,
    alreadyAdded: List<String>,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val isValidEmail   = email.contains("@") && email.contains(".")
    val alreadyExists  = email.trim().lowercase() in alreadyAdded.map { it.lowercase() }
    val canContinue    = isValidEmail && !alreadyExists

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 36.dp, top = 4.dp)
    ) {
        // Navigation + step dots
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextSecondary)
            }
            StepDots(current = 0, total = 2)
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        Text("Your Gmail address", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter the Gmail you want to connect for bank email scanning.",
            fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp
        )

        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value         = email,
            onValueChange = onEmailChange,
            placeholder   = { Text("you@gmail.com", color = TextMuted, fontSize = 17.sp) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(16.dp),
            textStyle     = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, color = TextPrimary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { if (canContinue) onContinue() }),
            leadingIcon = {
                Box(
                    Modifier.padding(start = 6.dp).size(36.dp),
                    contentAlignment = Alignment.Center
                ) { Text("G", fontSize = 20.sp, fontWeight = FontWeight.Black, color = GmailRed) }
            },
            trailingIcon = {
                AnimatedVisibility(visible = isValidEmail) {
                    Icon(
                        if (alreadyExists) Icons.Filled.Info else Icons.Filled.CheckCircle,
                        "",
                        tint = if (alreadyExists) WarningColor else SuccessColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = if (alreadyExists) WarningColor else Primary,
                unfocusedBorderColor = if (alreadyExists) WarningColor.copy(0.5f) else BorderColor,
                focusedTextColor     = TextPrimary, unfocusedTextColor = TextPrimary,
                cursorColor          = Primary
            )
        )

        AnimatedVisibility(visible = alreadyExists) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(WarningColor.copy(0.08f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, "", tint = WarningColor, modifier = Modifier.size(14.dp))
                    Text("This account is already connected", fontSize = 12.sp, color = WarningColor)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick  = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled  = canContinue,
            colors   = ButtonDefaults.buttonColors(containerColor = Primary),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, "", Modifier.size(18.dp))
        }
    }
}

// ── Step 2: App Password ──────────────────────────────────────

@Composable
private fun SheetStepPassword(
    email: String,
    appPassword: String,
    showPwd: Boolean,
    isLoading: Boolean,
    error: String?,
    onPasswordChange: (String) -> Unit,
    onToggleShow: () -> Unit,
    onBack: () -> Unit,
    onConnect: () -> Unit
) {
    val isReady = appPassword.length == 16
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val pwState = rememberUpdatedState(appPassword)

    // Pull a 16-char App Password off the clipboard (Google shows it spaced).
    fun clipCode(): String? {
        val t = clipboard.getText()?.text?.replace(Regex("\\s"), "") ?: return null
        return if (t.length == 16 && t.all { c -> c.isLetterOrDigit() }) t else null
    }

    // Open Google's App Passwords page in an in-app Chrome Custom Tab (falls
    // back to the default browser if no Custom Tabs provider is available).
    fun openAppPasswords() {
        val uri = android.net.Uri.parse("https://myaccount.google.com/apppasswords")
        runCatching {
            androidx.browser.customtabs.CustomTabsIntent.Builder().build().launchUrl(ctx, uri)
        }.onFailure {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    // When the user returns from the tab with the code copied, auto-paste it.
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && pwState.value.isEmpty()) {
                clipCode()?.let { onPasswordChange(it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 36.dp, top = 4.dp)
    ) {
        // Navigation + step dots
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !isLoading) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = if (isLoading) TextMuted else TextSecondary)
            }
            StepDots(current = 1, total = 2)
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        Text("Create an App Password", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "For  ${email.take(34)}${if (email.length > 34) "…" else ""}",
            fontSize = 12.sp, color = TextMuted
        )

        Spacer(Modifier.height(20.dp))

        // Step-by-step guide
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "GET YOUR 16-DIGIT APP PASSWORD",
                    fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your normal Gmail password will NOT work — Gmail requires a one-time App Password.",
                    fontSize = 11.sp, color = WarningColor, lineHeight = 15.sp
                )
                Spacer(Modifier.height(12.dp))
                val guideSteps = listOf(
                    Icons.Filled.Security      to "Turn ON 2-Step Verification (required first)",
                    Icons.Filled.AccountCircle to "Open myaccount.google.com/apppasswords",
                    Icons.Filled.VpnKey        to "Create a password for \"Mail\"",
                    Icons.Filled.ContentCopy   to "Paste the 16 letters below (spaces are fine)"
                )
                guideSteps.forEachIndexed { i, (icon, text) ->
                    Row(
                        Modifier.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(26.dp).background(Primary.copy(0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Bold) }
                        Icon(icon, "", tint = Primary, modifier = Modifier.size(15.dp))
                        Text(text, fontSize = 12.sp, color = TextSecondary)
                    }
                    if (i < guideSteps.lastIndex) {
                        HorizontalDivider(color = BorderColor.copy(0.3f), modifier = Modifier.padding(start = 38.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))
                // 1) Open Google's App Passwords page inside the app (Custom Tab).
                Button(
                    onClick = { openAppPasswords() },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Google to create the code", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                // 2) Paste the copied 16-char code straight into the field.
                OutlinedButton(
                    onClick = { clipCode()?.let { onPasswordChange(it) } },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Paste copied code", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Tip: also enable IMAP in Gmail → Settings → Forwarding and POP/IMAP (on by default for most accounts).",
            fontSize = 11.sp, color = TextMuted, lineHeight = 15.sp
        )

        Spacer(Modifier.height(16.dp))

        // App password field
        OutlinedTextField(
            value         = appPassword,
            onValueChange = onPasswordChange,
            placeholder   = {
                Text(
                    "xxxx xxxx xxxx xxxx",
                    color = TextMuted, fontSize = 18.sp, letterSpacing = 4.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(16.dp),
            textStyle     = androidx.compose.ui.text.TextStyle(
                fontSize     = 22.sp,
                fontWeight   = FontWeight.Bold,
                color        = TextPrimary,
                letterSpacing = 3.sp
            ),
            visualTransformation = if (showPwd) AppPasswordVisualTransformation else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (isReady && !isLoading) onConnect() }),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isReady) {
                        Icon(Icons.Filled.CheckCircle, "", tint = SuccessColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                    }
                    IconButton(onClick = onToggleShow, modifier = Modifier.size(42.dp)) {
                        Icon(
                            if (showPwd) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            "Toggle visibility", tint = TextMuted, modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            supportingText = {
                Text(
                    "${appPassword.length} / 16 characters",
                    fontSize = 11.sp,
                    color = when {
                        isReady          -> SuccessColor
                        appPassword.isEmpty() -> TextMuted
                        else             -> Primary
                    }
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = if (isReady) SuccessColor else Primary,
                unfocusedBorderColor = if (isReady) SuccessColor.copy(0.5f) else BorderColor,
                focusedTextColor     = TextPrimary,
                unfocusedTextColor   = TextPrimary,
                cursorColor          = Primary
            )
        )

        // Error message
        AnimatedVisibility(visible = error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(ErrorColor.copy(0.09f), RoundedCornerShape(14.dp))
                        .border(0.5.dp, ErrorColor.copy(0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.ErrorOutline, "", tint = ErrorColor, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text(error ?: "", color = ErrorColor, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick  = onConnect,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled  = isReady && !isLoading,
            colors   = ButtonDefaults.buttonColors(containerColor = Primary),
            shape    = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
                Spacer(Modifier.width(10.dp))
                Text("Verifying connection…", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            } else {
                Icon(Icons.Filled.Lock, "", Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect Account", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

// ── App Password visual transformation (groups of 4) ──────────

private object AppPasswordVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val spaced = raw.chunked(4).joinToString(" ")
        return TransformedText(
            AnnotatedString(spaced),
            object : OffsetMapping {
                // original position → transformed position (insert 1 space per completed group of 4)
                override fun originalToTransformed(offset: Int): Int =
                    (offset + offset / 4).coerceAtMost(spaced.length)
                // transformed position → original position (remove spaces: every 5th char is a space)
                override fun transformedToOriginal(offset: Int): Int =
                    (offset - offset / 5).coerceIn(0, raw.length)
            }
        )
    }
}

// ── Step progress indicator ────────────────────────────────────

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        repeat(total) { i ->
            val isActive = i == current
            Box(
                Modifier
                    .size(if (isActive) 22.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isActive) Primary else Primary.copy(0.25f))
                    .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMedium))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// Reusable Settings components — unchanged public API
// ─────────────────────────────────────────────────────────────────

// ── Profile hero — premium identity card at the top of Settings ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHeroCard(name: String?, email: String?, onChangePassword: () -> Unit) {
    val initials = (
        name?.trim()?.split(Regex("\\s+"))
            ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
            ?.take(2)?.joinToString("")
            ?.takeIf { it.isNotBlank() }
            ?: email?.takeIf { it.isNotBlank() }?.take(2)?.uppercase()
            ?: "U"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(GradientHero))
            .border(0.5.dp, Primary.copy(0.30f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.14f))
                        .border(1.dp, Color.White.copy(0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name?.takeIf { it.isNotBlank() } ?: "SpendWise User",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        email ?: "Not logged in",
                        fontSize = 12.sp, color = Color.White.copy(0.72f),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = onChangePassword,
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(0.14f)
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Change Password", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(), fontSize = 11.sp, color = TextMuted, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg)
        ) { content() }
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

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text("Change Password", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = currentPw, onValueChange = { currentPw = it },
                    label = { Text("Current Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showCurrent = !showCurrent }) {
                        Text(if (showCurrent) "Hide" else "Show", fontSize = 11.sp, color = Primary) } })
                OutlinedTextField(value = newPw, onValueChange = { newPw = it },
                    label = { Text("New Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showNew = !showNew }) {
                        Text(if (showNew) "Hide" else "Show", fontSize = 11.sp, color = Primary) } })
                OutlinedTextField(value = confirmPw, onValueChange = { confirmPw = it },
                    label = { Text("Confirm Password", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                (localError ?: error)?.let { Text(it, color = ErrorColor, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    localError = null
                    when {
                        currentPw.isBlank() -> localError = "Enter current password"
                        newPw.length < 8    -> localError = "Minimum 8 characters"
                        newPw != confirmPw  -> localError = "Passwords do not match"
                        else                -> onSubmit(currentPw, newPw)
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Change Password")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun SupportTicketDialog(
    isLoading: Boolean, error: String?,
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
                OutlinedTextField(value = subject, onValueChange = { subject = it },
                    label = { Text("Subject", color = TextSecondary) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it },
                    label = { Text("Describe your issue", color = TextSecondary) },
                    minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true,
                        label = { Text("Category", color = TextSecondary) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = TextPrimary) },
                                onClick = { category = cat; expanded = false }
                            )
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
                colors  = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Submit Ticket")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
