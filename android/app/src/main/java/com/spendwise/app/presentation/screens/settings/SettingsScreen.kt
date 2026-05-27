package com.spendwise.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.presentation.theme.*

@Composable
fun SettingsScreen(onLogout: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showSalaryDialog by remember { mutableStateOf(false) }
    var salaryInput by remember { mutableStateOf("") }
    var salaryDayInput by remember { mutableStateOf("1") }

    Column(Modifier.fillMaxSize().background(Background)) {
        Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(20.dp))

        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            // Profile
            item {
                SettingsSection("Profile") {
                    SettingsRow(Icons.Filled.Person, "Account", state.email ?: "Not logged in")
                }
            }

            // Salary
            item {
                SettingsSection("Salary") {
                    SettingsRow(Icons.Filled.AttachMoney, "Monthly Salary", state.salaryAmount?.let { "₹${it.toLong()}" } ?: "Not set",
                        onClick = { salaryInput = state.salaryAmount?.toString() ?: ""; salaryDayInput = state.salaryDay?.toString() ?: "1"; showSalaryDialog = true })
                }
            }

            // Sync
            item {
                SettingsSection("Data") {
                    SettingsRow(Icons.Filled.Sync, "Sync SMS Scan", "SMS inbox scan: ${if (state.smsScanFromMs > 0) "Active" else "Not set"}")
                    SettingsRow(Icons.Filled.Refresh, "Refresh Data", onClick = vm::refresh)
                }
            }

            // Danger zone
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

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = CardBg,
            title = { Text("Logout?", color = TextPrimary) },
            text = { Text("Are you sure you want to logout?", color = TextSecondary) },
            confirmButton = { Button(onClick = { vm.logout(); onLogout() }, colors = ButtonDefaults.buttonColors(containerColor = ErrorColor)) { Text("Logout") } },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }

    if (showSalaryDialog) {
        AlertDialog(
            onDismissRequest = { showSalaryDialog = false },
            containerColor = CardBg,
            title = { Text("Update Salary", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = salaryInput, onValueChange = { salaryInput = it }, label = { Text("Monthly Salary (₹)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = salaryDayInput, onValueChange = { salaryDayInput = it }, label = { Text("Expected Day (1-31)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.updateSalary(salaryInput.toDoubleOrNull() ?: 0.0, salaryDayInput.toIntOrNull() ?: 1)
                    showSalaryDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSalaryDialog = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

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
    val mod = if (onClick != null) Modifier.padding(14.dp).fillMaxWidth() else Modifier.padding(14.dp).fillMaxWidth()
    Row(mod, verticalAlignment = Alignment.CenterVertically) {
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
