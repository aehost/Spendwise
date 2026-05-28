package com.spendwise.app.presentation.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendwise.app.presentation.theme.*

data class ToolCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val badge: String? = null,
    val onClick: () -> Unit
)

@Composable
fun ToolsScreen(
    onAiCoach: () -> Unit,
    onHealthScore: () -> Unit,
    onCashFlow: () -> Unit,
    onDebtPayoff: () -> Unit,
    onTax: () -> Unit,
    onIou: () -> Unit,
    onLoans: () -> Unit,
    onReport: () -> Unit,
    onGoals: () -> Unit,
    onBudget: () -> Unit,
    onNetWorth: () -> Unit,
    onSettings: () -> Unit
) {
    val tools = listOf(
        ToolCard("Financial Advisor", "Personalised algorithm-powered insights", Icons.Filled.AutoAwesome, GradientPurple, "SMART", onAiCoach),
        ToolCard("Goals Planner", "Track & achieve short + long-term goals", Icons.Filled.Star, GradientGold, "NEW", onGoals),
        ToolCard("Budget Planner", "Set & track monthly spending limits", Icons.Filled.PieChart, GradientAmber, "SMART", onBudget),
        ToolCard("Net Worth", "Your total assets vs liabilities", Icons.Filled.AccountBalanceWallet, GradientGreen, null, onNetWorth),
        ToolCard("Health Score", "Your financial fitness score", Icons.Filled.Favorite, GradientGreen, null, onHealthScore),
        ToolCard("Cash Flow Forecast", "See your next 90 days cash flow", Icons.Filled.DateRange, GradientTeal, null, onCashFlow),
        ToolCard("Debt Payoff Planner", "Snowball vs Avalanche strategy", Icons.Filled.AccountBalance, GradientPink, null, onDebtPayoff),
        ToolCard("Tax Planning", "Old vs New regime comparison", Icons.Filled.Calculate, GradientAmber, "FY 24-25", onTax),
        ToolCard("IOU Tracker", "Track money lent and borrowed", Icons.Filled.People, listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)), null, onIou),
        ToolCard("Monthly Report", "Detailed spending analysis", Icons.Filled.Assessment, listOf(Color(0xFFFA709A), Color(0xFFFEE140)), null, onReport),
        ToolCard("Loan Manager", "Track all your EMIs", Icons.Filled.Money, listOf(Color(0xFF43E97B), Color(0xFF38F9D7)), null, onLoans),
        ToolCard("Settings", "Profile, Gmail, salary & more", Icons.Filled.Settings, listOf(Color(0xFF667EEA), Color(0xFF764BA2)), null, onSettings),
    )

    Column(Modifier.fillMaxSize().background(Background)) {
        Text(
            "Tools & Insights",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 4.dp)
        )
        Text(
            "AI-powered features to take control of your finances",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Featured: AI Coach (full-width hero)
            item {
                AiCoachHeroCard(onAiCoach)
            }

            // 2-column grid for remaining tools
            items(tools.drop(1).chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tool ->
                        SmallToolCard(tool, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AiCoachHeroCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth().background(Brush.linearGradient(GradientPurple)).padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Financial Advisor", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.background(Color.White.copy(0.2f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("NEW", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "12 smart rules analyse your spending, debt, bills & goals — updated without reinstalling",
                        fontSize = 13.sp, color = Color.White.copy(0.85f), lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.background(Color.White.copy(0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Start chatting", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Filled.ArrowForward, "", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("🤖", fontSize = 52.sp)
            }
        }
    }
}

@Composable
private fun SmallToolCard(tool: ToolCard, modifier: Modifier) {
    Card(
        modifier = modifier.clickable(onClick = tool.onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(tool.gradient)).padding(14.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(tool.icon, tool.title, tint = Color.White, modifier = Modifier.size(22.dp))
                    tool.badge?.let {
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.background(Color.White.copy(0.2f), RoundedCornerShape(20.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                            Text(it, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(tool.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(tool.subtitle, fontSize = 11.sp, color = Color.White.copy(0.8f), lineHeight = 15.sp)
            }
        }
    }
}
