package com.spendwise.app.presentation.screens.networth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import com.spendwise.app.data.remote.dto.BankAccountDto
import com.spendwise.app.data.remote.dto.CreditCardDto
import com.spendwise.app.data.remote.dto.InvestmentDto
import com.spendwise.app.data.remote.dto.LoanDto
import com.spendwise.app.presentation.theme.*

@Composable
fun NetWorthScreen(onBack: () -> Unit, vm: NetWorthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(4.dp, 16.dp, 16.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text(
                "Net Worth",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                modifier   = Modifier.weight(1f)
            )
            IconButton(onClick = vm::load) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = TextSecondary)
            }
        }

        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            state.error != null -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.error ?: "", color = ErrorColor, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = vm::load) { Text("Retry") }
                }
            }
            else -> {
                LazyColumn(
                    contentPadding    = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Hero card
                    item {
                        NetWorthHeroCard(
                            netWorth         = state.netWorth,
                            totalAssets      = state.totalAssets,
                            totalLiabilities = state.totalLiabilities
                        )
                    }

                    // ASSETS section header
                    item {
                        SectionLabel(
                            title = "ASSETS",
                            total = state.totalAssets,
                            color = SuccessColor
                        )
                    }

                    // Bank accounts
                    if (state.bankAccounts.isNotEmpty()) {
                        item {
                            SubSectionCard(title = "Bank Accounts", emoji = "🏦") {
                                state.bankAccounts.forEachIndexed { i, account ->
                                    BankAccountRow(account)
                                    if (i < state.bankAccounts.size - 1) {
                                        HorizontalDivider(color = BorderColor.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                                TotalRow(label = "Bank Total", amount = state.totalBankBalance, color = SuccessColor)
                            }
                        }
                    }

                    // Investments
                    if (state.investments.isNotEmpty()) {
                        item {
                            SubSectionCard(title = "Investments", emoji = "📈") {
                                state.investments.forEachIndexed { i, inv ->
                                    InvestmentRow(inv)
                                    if (i < state.investments.size - 1) {
                                        HorizontalDivider(color = BorderColor.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                                TotalRow(label = "Investments Total", amount = state.totalInvestments, color = SuccessColor)
                            }
                        }
                    }

                    // Goal savings
                    if (state.goalSavings > 0) {
                        item {
                            SubSectionCard(title = "Goal Savings", emoji = "🎯") {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text("Total across all goals", fontSize = 13.sp, color = TextSecondary)
                                    Text(
                                        state.goalSavings.formatCurrency(),
                                        fontSize   = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color      = SuccessColor
                                    )
                                }
                            }
                        }
                    }

                    // LIABILITIES section header
                    item {
                        SectionLabel(
                            title = "LIABILITIES",
                            total = state.totalLiabilities,
                            color = ErrorColor
                        )
                    }

                    // Credit cards
                    if (state.creditCards.isNotEmpty()) {
                        item {
                            SubSectionCard(title = "Credit Cards", emoji = "💳") {
                                state.creditCards.forEachIndexed { i, cc ->
                                    CreditCardRow(cc)
                                    if (i < state.creditCards.size - 1) {
                                        HorizontalDivider(color = BorderColor.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                                TotalRow(label = "CC Total", amount = state.totalCcDebt, color = ErrorColor)
                            }
                        }
                    }

                    // Loans
                    if (state.loans.isNotEmpty()) {
                        item {
                            SubSectionCard(title = "Loans & EMIs", emoji = "🏦") {
                                state.loans.forEachIndexed { i, loan ->
                                    LoanRow(loan)
                                    if (i < state.loans.size - 1) {
                                        HorizontalDivider(color = BorderColor.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                                TotalRow(label = "Loans Total", amount = state.totalLoanDebt, color = ErrorColor)
                            }
                        }
                    }

                    // Footer summary
                    item {
                        NetWorthFooter(
                            totalAssets      = state.totalAssets,
                            totalLiabilities = state.totalLiabilities,
                            netWorth         = state.netWorth
                        )
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun NetWorthHeroCard(netWorth: Double, totalAssets: Double, totalLiabilities: Double) {
    val isPositive = netWorth >= 0
    val gradient = if (isPositive) GradientGreen else GradientRose

    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(gradient))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "NET WORTH",
                    fontSize      = 11.sp,
                    color         = Color.White.copy(0.65f),
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    netWorth.formatCurrency(),
                    fontSize   = 38.sp,
                    fontWeight = FontWeight.Black,
                    color      = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isPositive) "Positive net worth" else "Working toward positive",
                    fontSize = 12.sp,
                    color    = Color.White.copy(0.75f)
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NetWorthPill(
                        label    = "Total Assets",
                        value    = totalAssets.formatCurrency(),
                        color    = Color(0xFF00E5A0),
                        modifier = Modifier.weight(1f)
                    )
                    NetWorthPill(
                        label    = "Total Liabilities",
                        value    = totalLiabilities.formatCurrency(),
                        color    = Color(0xFFFF6B8A),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetWorthPill(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier.background(Color.Black.copy(0.2f), RoundedCornerShape(12.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(0.6f))
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SectionLabel(title: String, total: Double, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(3.dp, 16.dp).background(color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 11.sp, color = color, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
        Text(total.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SubSectionCard(title: String, emoji: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: Double, color: Color) {
    HorizontalDivider(color = BorderColor.copy(0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
        Text(amount.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/** Leading bank-brand tile; renders nothing when [name] isn't a known bank. */
@Composable
private fun BrandLeading(name: String) {
    val brand = com.spendwise.app.presentation.components.BankBrands.find(name) ?: return
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(brand.gradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(brand.mark, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
    }
    Spacer(Modifier.width(10.dp))
}

@Composable
private fun BankAccountRow(account: BankAccountDto) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandLeading(account.name)
        Column(Modifier.weight(1f)) {
            Text(account.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            if (!account.lastFour.isNullOrBlank()) {
                Text("••••${account.lastFour}", fontSize = 11.sp, color = TextMuted)
            }
        }
        Text(account.balance.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SuccessColor)
    }
}

@Composable
private fun InvestmentRow(inv: InvestmentDto) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(inv.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("${inv.monthlyAmount.formatCurrency()}/mo", fontSize = 11.sp, color = TextMuted)
        }
        Text(inv.currentBalance.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SuccessColor)
    }
}

@Composable
private fun CreditCardRow(cc: CreditCardDto) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandLeading(cc.name)
        Column(Modifier.weight(1f)) {
            Text(cc.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            if (!cc.lastFour.isNullOrBlank()) {
                Text("••••${cc.lastFour}", fontSize = 11.sp, color = TextMuted)
            }
        }
        Text(cc.outstanding.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ErrorColor)
    }
}

@Composable
private fun LoanRow(loan: LoanDto) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandLeading(loan.name)
        Column(Modifier.weight(1f)) {
            Text(loan.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("${loan.interestRate}% • ${loan.monthsRemaining}mo left", fontSize = 11.sp, color = TextMuted)
        }
        Text(loan.outstanding.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ErrorColor)
    }
}

@Composable
private fun NetWorthFooter(totalAssets: Double, totalLiabilities: Double, netWorth: Double) {
    val isPositive = netWorth >= 0

    Card(
        Modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isPositive) SuccessColor.copy(0.08f) else ErrorColor.copy(0.08f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Assets", fontSize = 13.sp, color = TextSecondary)
                Text(totalAssets.formatCurrency(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SuccessColor)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Liabilities", fontSize = 13.sp, color = TextSecondary)
                Text("−${totalLiabilities.formatCurrency()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ErrorColor)
            }
            HorizontalDivider(color = BorderColor.copy(0.5f), thickness = 0.5.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Net Worth", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    netWorth.formatCurrency(),
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (isPositive) SuccessColor else ErrorColor
                )
            }
        }
    }
}
