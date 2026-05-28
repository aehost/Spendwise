package com.spendwise.app.presentation.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.presentation.screens.auth.SpendWiseLogoComposable
import com.spendwise.app.presentation.theme.*

@Composable
fun SetupScreen(
    onDone: () -> Unit,
    vm: SetupViewModel = hiltViewModel()
) {
    val selectedDate by vm.selectedDate.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        SpendWiseLogoComposable(iconSize = 80)
        Spacer(Modifier.height(12.dp))
        Text(
            "SpendWise",
            fontSize = 30.sp, fontWeight = FontWeight.Black,
            color = TextPrimary, letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(8.dp))

        Text("Welcome! 🎉", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(12.dp))

        Text(
            "SpendWise reads your bank SMS messages\nto track every transaction automatically.\nNo login to your bank. 100% private.",
            fontSize = 14.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 22.sp
        )
        Spacer(Modifier.height(32.dp))

        // Date picker card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "📅  START TRACKING FROM",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = TextMuted, letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = selectedDate,
                    onValueChange = vm::setDate,
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Primary,
                        unfocusedBorderColor = BorderColor,
                        focusedLabelColor    = Primary,
                        cursorColor          = Primary
                    )
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "SpendWise will import bank SMS from this date onwards.\nDefault is today — change only to import older transactions.",
                    fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Get started button
        Button(
            onClick = { vm.complete(); onDone() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text("Get Started →", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Skip — use today's date",
            fontSize = 12.sp, color = TextSecondary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { vm.completeWithToday(); onDone() }
        )
    }
}
