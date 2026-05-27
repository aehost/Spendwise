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
    val state by vm.state.collectAsState()
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) {
        if (state.success) onAuthSuccess()
    }

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

        // Tabs
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
                        if (sel) Divider(color = Primary, thickness = 2.dp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (!isLogin) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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

        Spacer(Modifier.height(8.dp))

        state.error?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = ErrorColor.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
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
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Primary,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor    = Primary,
    cursorColor          = Primary
)
