package com.spendwise.app.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.presentation.theme.*

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    vm: AuthViewModel = hiltViewModel()
) {
    val state       by vm.state.collectAsState()
    val forgotState by vm.forgotState.collectAsState()

    var isLogin     by remember { mutableStateOf(true) }
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var name        by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showForgot  by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onAuthSuccess() }

    // Reset forgot flow when dialog is dismissed
    LaunchedEffect(showForgot) { if (!showForgot) vm.resetForgotState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(60.dp))
        Text("₹", fontSize = 56.sp, color = Primary)
        Text("SpendWise", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Primary)
        Text("Your Personal Finance Tracker", fontSize = 13.sp, color = TextSecondary)

        Spacer(Modifier.height(40.dp))

        // Login / Sign Up tabs
        Row(Modifier.fillMaxWidth()) {
            listOf("Login", "Sign Up").forEachIndexed { i, label ->
                val sel = (i == 0) == isLogin
                TextButton(
                    onClick = { isLogin = i == 0; vm.clearError() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(contentColor = if (sel) Primary else TextSecondary)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp)
                        if (sel) HorizontalDivider(color = Primary, thickness = 2.dp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!isLogin) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full Name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                colors = outlinedColors()
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            colors = outlinedColors()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide" else "Show", fontSize = 12.sp, color = Primary)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = outlinedColors()
        )

        // Forgot password link (Login tab only)
        if (isLogin) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showForgot = true }) {
                    Text("Forgot Password?", fontSize = 13.sp, color = Primary)
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.12f)), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = ErrorColor, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                if (isLogin) vm.login(email, password)
                else vm.register(email, password, name)
            },
            enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(if (isLogin) "Login" else "Create Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Your data is end-to-end encrypted.", fontSize = 12.sp, color = TextMuted)
    }

    // ── Forgot Password dialog ────────────────────────────────
    if (showForgot) {
        ForgotPasswordDialog(
            forgotState = forgotState,
            onDismiss   = { showForgot = false },
            onSendOtp   = { em -> vm.sendForgotOtp(em) },
            onReset     = { em, otp, pw -> vm.resetPassword(em, otp, pw) },
            onDone      = { showForgot = false }
        )
    }
}

@Composable
fun ForgotPasswordDialog(
    forgotState: ForgotPwState,
    onDismiss: () -> Unit,
    onSendOtp: (String) -> Unit,
    onReset: (String, String, String) -> Unit,
    onDone: () -> Unit
) {
    var email    by remember { mutableStateOf("") }
    var otp      by remember { mutableStateOf("") }
    var newPw    by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }
    var localErr by remember { mutableStateOf<String?>(null) }

    // Pre-fill OTP if returned by server
    LaunchedEffect(forgotState.otp) {
        forgotState.otp?.let { otp = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardBg,
        title = { Text(when (forgotState.step) { 0 -> "Forgot Password"; 1 -> "Enter Reset Code"; else -> "Password Reset" }, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (forgotState.step) {
                    0 -> {
                        Text("Enter your email address to receive a reset code.", fontSize = 13.sp, color = TextSecondary)
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    }
                    1 -> {
                        forgotState.otp?.let {
                            Card(colors = CardDefaults.cardColors(containerColor = Primary.copy(0.1f)), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Your reset code:", fontSize = 12.sp, color = Primary)
                                    Text(it, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Primary)
                                    Text("(Code is pre-filled below)", fontSize = 11.sp, color = TextMuted)
                                }
                            }
                        }
                        OutlinedTextField(value = otp, onValueChange = { otp = it }, label = { Text("Reset Code (6 digits)", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(
                            value = newPw, onValueChange = { newPw = it },
                            label = { Text("New Password", color = TextSecondary) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = { TextButton(onClick = { showPw = !showPw }) { Text(if (showPw) "Hide" else "Show", fontSize = 11.sp, color = Primary) } }
                        )
                        OutlinedTextField(value = confirmPw, onValueChange = { confirmPw = it }, label = { Text("Confirm Password", color = TextSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                    }
                    2 -> {
                        Text(forgotState.successMessage ?: "Password reset successfully!", color = SuccessColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                (localErr ?: forgotState.error)?.let { Text(it, color = ErrorColor, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            when (forgotState.step) {
                0 -> Button(
                    onClick = { localErr = null; if (email.isBlank()) localErr = "Enter your email" else onSendOtp(email) },
                    enabled = !forgotState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { if (forgotState.isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp) else Text("Send Code") }
                1 -> Button(
                    onClick = {
                        localErr = null
                        when {
                            otp.length != 6  -> localErr = "Enter the 6-digit code"
                            newPw.length < 8 -> localErr = "Password must be at least 8 characters"
                            newPw != confirmPw -> localErr = "Passwords do not match"
                            else             -> onReset(email, otp, newPw)
                        }
                    },
                    enabled = !forgotState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { if (forgotState.isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp) else Text("Reset Password") }
                else -> Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = SuccessColor)) { Text("Done") }
            }
        },
        dismissButton = {
            if (forgotState.step < 2) TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor    = Primary,
    cursorColor          = Primary
)
