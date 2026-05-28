package com.spendwise.app.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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

    var isLogin      by remember { mutableStateOf(true) }
    var email        by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var name         by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showForgot   by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onAuthSuccess() }
    LaunchedEffect(showForgot) { if (!showForgot) vm.resetForgotState() }

    Box(Modifier.fillMaxSize().background(Background)) {
        // Background gradient orbs
        Box(
            Modifier.size(300.dp)
                .offset((-80).dp, (-60).dp)
                .background(Brush.radialGradient(listOf(Primary.copy(0.15f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.size(250.dp).align(Alignment.BottomEnd)
                .offset(60.dp, 60.dp)
                .background(Brush.radialGradient(listOf(Secondary.copy(0.1f), Color.Transparent)), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // ── Logo ──────────────────────────────────────────────
            SpendWiseLogoComposable()

            Spacer(Modifier.height(10.dp))
            Text("SpendWise", fontSize = 30.sp, fontWeight = FontWeight.Black,
                color = TextPrimary, letterSpacing = (-0.5).sp)
            Text("Smart money. Smarter life.", fontSize = 13.sp, color = TextMuted)

            Spacer(Modifier.height(36.dp))

            // ── Auth Card ─────────────────────────────────────────
            Box(
                Modifier.fillMaxWidth()
                    .background(CardBg, RoundedCornerShape(28.dp))
                    .border(0.5.dp, BorderColor, RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Column {
                    // Tab row
                    Row(
                        Modifier.fillMaxWidth()
                            .background(Surface, RoundedCornerShape(14.dp))
                            .padding(4.dp)
                    ) {
                        listOf("Login", "Sign Up").forEachIndexed { i, label ->
                            val sel = (i == 0) == isLogin
                            Box(
                                modifier = Modifier.weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Brush.linearGradient(GradientPurple) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                    .clickable { isLogin = i == 0; vm.clearError() }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) Color.White else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Name field (sign up only)
                    if (!isLogin) {
                        AuthTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "Full Name",
                            leadingIcon = Icons.Filled.Person
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Email
                    AuthTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email address",
                        leadingIcon = Icons.Filled.Email,
                        keyboardType = KeyboardType.Email
                    )
                    Spacer(Modifier.height(12.dp))

                    // Password
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        leadingIcon = Icons.Filled.Lock,
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        showPassword = showPassword,
                        onTogglePassword = { showPassword = !showPassword }
                    )

                    // Forgot password
                    if (isLogin) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showForgot = true }) {
                                Text("Forgot Password?", fontSize = 12.sp, color = Primary)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }

                    // Error
                    state.error?.let { errMsg ->
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth()
                                .background(ErrorColor.copy(0.1f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.ErrorOutline, "", tint = ErrorColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(errMsg, color = ErrorColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // CTA button
                    Button(
                        onClick = {
                            if (isLogin) vm.login(email, password)
                            else vm.register(email, password, name)
                        },
                        enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .background(
                                    if (!state.isLoading && email.isNotBlank() && password.isNotBlank())
                                        Brush.linearGradient(GradientPurple)
                                    else Brush.linearGradient(listOf(Primary.copy(0.4f), PrimaryDark.copy(0.4f))),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        if (isLogin) "Sign In" else "Create Account",
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                                    )
                                    Icon(Icons.Filled.ArrowForward, "", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Security note
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Lock, "", tint = TextMuted, modifier = Modifier.size(12.dp))
                Text("End-to-end encrypted · No bank login required", fontSize = 11.sp, color = TextMuted)
            }

            // Feature bullets
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeatureBullet("📲", "Auto SMS\nTracking")
                FeatureBullet("🎯", "Smart\nGoals")
                FeatureBullet("📊", "AI\nInsights")
                FeatureBullet("🔒", "100%\nPrivate")
            }

            Spacer(Modifier.height(32.dp))
        }
    }

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

// ── Logo composable ───────────────────────────────────────────

@Composable
fun SpendWiseLogoComposable(iconSize: Int = 80) {
    Box(Modifier.size(iconSize.dp), contentAlignment = Alignment.Center) {
        // Outer glow
        Box(
            Modifier.size(iconSize.dp)
                .background(Brush.radialGradient(listOf(Primary.copy(0.25f), Color.Transparent)), CircleShape)
        )
        // Main circle
        Box(
            Modifier.size((iconSize * 0.8f).dp)
                .background(Brush.linearGradient(GradientHero), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Capture iconSize before entering DrawScope to avoid name clash with DrawScope.size
            val canvasDp = (iconSize * 0.5f).dp
            androidx.compose.foundation.Canvas(Modifier.size(canvasDp)) {
                val w     = canvasDp.toPx()
                val h     = w
                val barW  = w * 0.22f
                val gap   = w * 0.07f
                val baseY = h * 0.85f

                // Bar heights: short (30%), medium (50%), tall (65%) — fits comfortably with arrow room
                val heights = listOf(h * 0.30f, h * 0.50f, h * 0.65f)
                val barColors = listOf(
                    Color.White.copy(0.6f),
                    Color.White.copy(0.8f),
                    Color.White
                )
                heights.forEachIndexed { i, bh ->
                    val x = (barW + gap) * i
                    drawRoundRect(
                        color = barColors[i],
                        topLeft = androidx.compose.ui.geometry.Offset(x, baseY - bh),
                        size = androidx.compose.ui.geometry.Size(barW, bh),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    )
                }
                // Upward arrow above tallest bar (fits within canvas: top ~0.20h = ~8dp clearance)
                val arrowCx = (barW + gap) * 2 + barW / 2
                val arrowTip = baseY - heights[2] - 5.dp.toPx()
                val arrowPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(arrowCx, arrowTip)
                    lineTo(arrowCx - 4.dp.toPx(), arrowTip + 6.dp.toPx())
                    lineTo(arrowCx + 4.dp.toPx(), arrowTip + 6.dp.toPx())
                    close()
                }
                drawPath(arrowPath, color = GoldAccent)
            }
        }
        // Gold dot accent
        Box(
            Modifier.size(12.dp)
                .align(Alignment.TopEnd)
                .offset(2.dp, 2.dp)
                .background(Brush.radialGradient(listOf(GoldAccent, GoldSoft)), CircleShape)
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    showPassword: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        leadingIcon = {
            Icon(leadingIcon, label, tint = TextMuted, modifier = Modifier.size(18.dp))
        },
        trailingIcon = if (isPassword && onTogglePassword != null) {
            {
                IconButton(onClick = onTogglePassword, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (showPassword) "Hide" else "Show",
                        tint = TextMuted, modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Primary,
            unfocusedBorderColor = BorderColor,
            focusedLabelColor    = Primary,
            unfocusedLabelColor  = TextMuted,
            cursorColor          = Primary,
            focusedTextColor     = TextPrimary,
            unfocusedTextColor   = TextPrimary,
            focusedLeadingIconColor   = Primary,
            unfocusedLeadingIconColor = TextMuted
        )
    )
}

@Composable
private fun FeatureBullet(emoji: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(44.dp).background(CardBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 20.sp) }
        Text(label, fontSize = 10.sp, color = TextMuted, textAlign = TextAlign.Center, lineHeight = 14.sp)
    }
}

// ── Forgot Password dialog ────────────────────────────────────

@Composable
fun ForgotPasswordDialog(
    forgotState: ForgotPwState,
    onDismiss: () -> Unit,
    onSendOtp: (String) -> Unit,
    onReset: (String, String, String) -> Unit,
    onDone: () -> Unit
) {
    var email     by remember { mutableStateOf("") }
    var otp       by remember { mutableStateOf("") }
    var newPw     by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var showPw    by remember { mutableStateOf(false) }
    var localErr  by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(forgotState.otp) { forgotState.otp?.let { otp = it } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                when (forgotState.step) { 0 -> "Reset Password"; 1 -> "Enter Reset Code"; else -> "All Done!" },
                color = TextPrimary, fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (forgotState.step) {
                    0 -> {
                        Text("Enter your email to receive a reset code.", fontSize = 13.sp, color = TextSecondary)
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            label = { Text("Email", color = TextSecondary) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = forgotFieldColors()
                        )
                    }
                    1 -> {
                        forgotState.otp?.let {
                            Box(
                                Modifier.fillMaxWidth()
                                    .background(Primary.copy(0.1f), RoundedCornerShape(12.dp))
                                    .border(0.5.dp, Primary.copy(0.3f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("Your reset code:", fontSize = 11.sp, color = Primary)
                                    Text(it, fontSize = 28.sp, fontWeight = FontWeight.Black, color = Primary, letterSpacing = 6.sp)
                                }
                            }
                        }
                        OutlinedTextField(value = otp, onValueChange = { otp = it }, label = { Text("Reset code (6 digits)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = forgotFieldColors())
                        OutlinedTextField(
                            value = newPw, onValueChange = { newPw = it },
                            label = { Text("New Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = { TextButton(onClick = { showPw = !showPw }) { Text(if (showPw) "Hide" else "Show", fontSize = 11.sp, color = Primary) } },
                            colors = forgotFieldColors()
                        )
                        OutlinedTextField(value = confirmPw, onValueChange = { confirmPw = it }, label = { Text("Confirm Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), visualTransformation = PasswordVisualTransformation(), colors = forgotFieldColors())
                    }
                    2 -> {
                        Box(
                            Modifier.fillMaxWidth()
                                .background(SuccessColor.copy(0.1f), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("✓", fontSize = 24.sp, color = SuccessColor)
                                Text(forgotState.successMessage ?: "Password reset successfully!", color = SuccessColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                (localErr ?: forgotState.error)?.let {
                    Row(
                        Modifier.fillMaxWidth().background(ErrorColor.copy(0.1f), RoundedCornerShape(10.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.ErrorOutline, "", tint = ErrorColor, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(it, color = ErrorColor, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            when (forgotState.step) {
                0 -> Button(
                    onClick = { localErr = null; if (email.isBlank()) localErr = "Enter your email" else onSendOtp(email) },
                    enabled = !forgotState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (forgotState.isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Send Code")
                }
                1 -> Button(
                    onClick = {
                        localErr = null
                        when {
                            otp.length != 6    -> localErr = "Enter the 6-digit code"
                            newPw.length < 8   -> localErr = "Minimum 8 characters"
                            newPw != confirmPw -> localErr = "Passwords do not match"
                            else               -> onReset(email, otp, newPw)
                        }
                    },
                    enabled = !forgotState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (forgotState.isLoading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Reset Password")
                }
                else -> Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = SuccessColor), shape = RoundedCornerShape(12.dp)) { Text("Done!") }
            }
        },
        dismissButton = {
            if (forgotState.step < 2) TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun forgotFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor    = Primary,
    unfocusedLabelColor  = TextSecondary,
    cursorColor          = Primary,
    focusedTextColor     = TextPrimary,
    unfocusedTextColor   = TextPrimary
)

