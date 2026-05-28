package com.spendwise.app.presentation.screens.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.data.remote.dto.ScoreFactorDto
import com.spendwise.app.presentation.theme.*

@Composable
fun HealthScoreScreen(onBack: () -> Unit, vm: HealthScoreViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp, 16.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Text("Financial Health Score", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(state.error ?: "", color = ErrorColor)
                Button(onClick = vm::load, colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Retry") }
            }
            else -> {
                val score = state.score
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        // Hero score ring
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(GradientPurple)).padding(24.dp)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                    Text("YOUR HEALTH SCORE", fontSize = 11.sp, color = Color.White.copy(0.7f), letterSpacing = 1.2.sp)
                                    Spacer(Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                                        Text(
                                            "${score?.score ?: 0}",
                                            fontSize = 72.sp, fontWeight = FontWeight.Black, color = Color.White
                                        )
                                        Text("/100", fontSize = 20.sp, color = Color.White.copy(0.7f), modifier = Modifier.padding(bottom = 10.dp))
                                    }
                                    Text(score?.grade ?: "—", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Amber)
                                    Text(score?.level ?: "", fontSize = 14.sp, color = Color.White.copy(0.85f))
                                    Spacer(Modifier.height(16.dp))
                                    // Score bar
                                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.2f))) {
                                        Box(
                                            Modifier.fillMaxWidth((score?.score ?: 0) / 100f).height(8.dp)
                                                .clip(RoundedCornerShape(50)).background(Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Text("Score Breakdown", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }

                    items(score?.factors ?: emptyList()) { factor ->
                        FactorCard(factor)
                    }
                }
            }
        }
    }
}

@Composable
private fun FactorCard(factor: ScoreFactorDto) {
    val color = when (factor.status) {
        "good"    -> SuccessColor
        "neutral" -> WarningColor
        else      -> ErrorColor
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(factor.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(factor.detail, fontSize = 12.sp, color = TextSecondary)
                }
                Text("${factor.score}/${factor.max}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(BorderColor)) {
                Box(Modifier.fillMaxWidth(factor.pct / 100f).height(6.dp).clip(RoundedCornerShape(50)).background(color))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                (if (factor.status == "good") "✅ " else if (factor.status == "neutral") "⚠️ " else "❌ ") + factor.tip,
                fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp
            )
        }
    }
}
