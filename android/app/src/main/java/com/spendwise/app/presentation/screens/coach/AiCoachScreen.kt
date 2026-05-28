package com.spendwise.app.presentation.screens.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.presentation.theme.*
import kotlinx.coroutines.launch

data class ChatMessage(val role: String, val content: String)

private val SUGGESTED_QUESTIONS = listOf(
    "Can I afford a ₹50,000 vacation next month?",
    "How can I reduce my monthly expenses?",
    "Which loan should I pay off first?",
    "Am I saving enough for retirement?",
    "How do I build a ₹5L emergency fund?",
)

@Composable
fun AiCoachScreen(onBack: () -> Unit, vm: AiCoachViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    Column(Modifier.fillMaxSize().background(Background)) {
        // ── Top bar ──────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF5B3FE8), Color(0xFF7C5CFC)))
            ).padding(top = 16.dp, bottom = 16.dp, start = 8.dp, end = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("AI Finance Coach", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Powered by Claude", fontSize = 11.sp, color = Color.White.copy(0.7f))
                }
            }
        }

        // ── Messages ─────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Welcome message
            if (state.messages.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🤖", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Your Personal Finance Advisor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "Ask me anything about your finances. I have access to your spending, loans, bills, and goals.",
                            fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Try asking:", fontSize = 12.sp, color = TextMuted)
                        Spacer(Modifier.height(8.dp))
                        SUGGESTED_QUESTIONS.forEach { q ->
                            SuggestionChip(q) {
                                input = q
                                vm.sendMessage(q)
                                input = ""
                            }
                        }
                    }
                }
            }

            items(state.messages) { msg ->
                MessageBubble(msg)
            }

            if (state.isTyping) {
                item {
                    Row(Modifier.padding(start = 4.dp)) {
                        Box(
                            Modifier.background(CardBg, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).padding(12.dp, 10.dp)
                        ) {
                            Text("●●●", color = TextMuted, fontSize = 14.sp, letterSpacing = 3.sp)
                        }
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Text(err, color = ErrorColor, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }

        // ── Input bar ─────────────────────────────────────────
        HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
        Row(
            Modifier.fillMaxWidth().background(CardBg).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask about your finances…", color = TextMuted, fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank() && !state.isTyping) {
                        vm.sendMessage(input.trim())
                        input = ""
                    }
                })
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank() && !state.isTyping) {
                        vm.sendMessage(input.trim())
                        input = ""
                    }
                },
                modifier = Modifier.size(46.dp).background(Primary, RoundedCornerShape(50)),
                enabled = input.isNotBlank() && !state.isTyping
            ) {
                Icon(Icons.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                Modifier.size(32.dp).background(Primary.copy(0.15f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) { Text("🤖", fontSize = 14.sp) }
            Spacer(Modifier.width(6.dp))
        }
        Box(
            Modifier
                .widthIn(max = 290.dp)
                .background(
                    if (isUser) Primary else CardBg,
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp
                    )
                )
                .padding(12.dp, 10.dp)
        ) {
            Text(
                msg.content,
                fontSize = 14.sp,
                color = if (isUser) Color.White else TextPrimary,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 13.sp, color = TextSecondary)
    }
}
