package com.spendwise.app.presentation.screens.debt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.presentation.theme.*

@Composable
fun DebtPayoffScreen(onBack: () -> Unit, vm: DebtPayoffViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp, 16.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Text("Debt Payoff Planner", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) }
            state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(state.error ?: "", color = ErrorColor)
                Button(onClick = vm::load) { Text("Retry") }
            }
            state.data?.debts?.isEmpty() == true -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Debt Free!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SuccessColor)
                    Text("You have no active loans or CC balances.", fontSize = 14.sp, color = TextSecondary)
                }
            }
            else -> {
                val d = state.data!!
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        // Total debt hero
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFFF4757), Color(0xFFD63031)))).padding(20.dp)) {
                                Column {
                                    Text("TOTAL DEBT", fontSize = 11.sp, color = Color.White.copy(0.7f), letterSpacing = 1.2.sp)
                                    Text(d.totalDebt.formatCurrency(), fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Text("${d.totalMonthlyPayment.formatCurrency()}/month in payments", fontSize = 13.sp, color = Color.White.copy(0.8f))
                                    if (d.interestSavedByAvalanche > 0) {
                                        Spacer(Modifier.height(8.dp))
                                        Box(Modifier.background(Color.White.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                                            Text("Avalanche saves ${d.interestSavedByAvalanche.formatCurrency()} in interest", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        TabRow(selectedTabIndex = selectedTab, containerColor = CardBg, contentColor = Primary) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                                Text("Snowball", modifier = Modifier.padding(vertical = 12.dp), fontSize = 13.sp)
                            }
                            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                                Text("Avalanche", modifier = Modifier.padding(vertical = 12.dp), fontSize = 13.sp)
                            }
                        }
                    }

                    val strategy = if (selectedTab == 0) d.snowball else d.avalanche
                    val isRecommended = strategy.description.contains(d.recommended, ignoreCase = true) ||
                        (selectedTab == 1 && d.recommended == "avalanche") || (selectedTab == 0 && d.recommended == "snowball")

                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isRecommended) SuccessColor.copy(0.08f) else CardBg)) {
                            Column(Modifier.padding(16.dp)) {
                                if (isRecommended) Text("⭐ Recommended Strategy", fontSize = 12.sp, color = SuccessColor, fontWeight = FontWeight.SemiBold)
                                Text(strategy.description, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StratStat("Payoff In", "${strategy.months} months", Modifier.weight(1f))
                                    StratStat("Total Interest", strategy.totalInterest.formatCurrency(), Modifier.weight(1f))
                                    StratStat("Debt Free By", strategy.payoffDate.take(7), Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    item { Text("Payoff Order", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) }

                    itemsIndexed(strategy.orderNames) { i, name ->
                        val debt = d.debts.find { it.name == name } ?: return@itemsIndexed
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(36.dp).background(Primary.copy(0.15f), RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) { Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(debt.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                    Text("${debt.interestRate}% • ${debt.monthlyPayment.formatCurrency()}/mo", fontSize = 11.sp, color = TextSecondary)
                                }
                                Text(debt.outstanding.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ErrorColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StratStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}
