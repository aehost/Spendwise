package com.spendwise.app.presentation.screens.tax

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.app.core.formatCurrency
import com.spendwise.app.data.remote.dto.TaxEstimateDto
import com.spendwise.app.presentation.theme.*

@Composable
fun TaxPlanningScreen(onBack: () -> Unit, vm: TaxPlanningViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().background(Background)) {
        Row(Modifier.fillMaxWidth().padding(4.dp, 16.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Text("Tax Planning (FY 2024-25)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Income & Deductions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        TaxField("Annual Salary (₹)", state.annualSalary) { vm.update { copy(annualSalary = it) } }
                        TaxField("Other Income (₹)", state.otherIncome) { vm.update { copy(otherIncome = it) } }
                        TaxField("Section 80C (max ₹1,50,000)", state.section80c) { vm.update { copy(section80c = it) } }
                        TaxField("Section 80D - Health Insurance (max ₹50,000)", state.section80d) { vm.update { copy(section80d = it) } }
                        TaxField("HRA Exemption (₹)", state.hraExemption) { vm.update { copy(hraExemption = it) } }
                        TaxField("NPS 80CCD(1B) (max ₹50,000)", state.nps80ccd) { vm.update { copy(nps80ccd = it) } }
                        TaxField("Home Loan Interest 24b (max ₹2,00,000)", state.homeLoanInterest) { vm.update { copy(homeLoanInterest = it) } }
                        Button(
                            onClick = vm::calculate,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            enabled = !state.isLoading && state.annualSalary.isNotBlank()
                        ) {
                            if (state.isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            else Text("Calculate Tax")
                        }
                        state.error?.let { Text(it, color = ErrorColor, fontSize = 12.sp) }
                    }
                }
            }

            state.result?.let { r ->
                item { TaxComparisonCard(r) }
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Suggestions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            r.suggestions.forEach { s ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("💡", fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(s, fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp, color = TextSecondary) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary, unfocusedBorderColor = BorderColor,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
        )
    )
}

@Composable
private fun TaxComparisonCard(r: TaxEstimateDto) {
    val oldBetter = r.recommended == "old"
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tax Comparison", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Gross Income: ${r.grossIncome.formatCurrency()}", fontSize = 13.sp, color = TextSecondary)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RegimeCard(
                    "Old Regime",
                    r.oldRegime.totalTax,
                    r.oldRegime.effectiveRate,
                    r.oldRegime.monthlyTds,
                    r.oldRegime.totalDeductions,
                    recommended = oldBetter,
                    modifier = Modifier.weight(1f)
                )
                RegimeCard(
                    "New Regime",
                    r.newRegime.totalTax,
                    r.newRegime.effectiveRate,
                    r.newRegime.monthlyTds,
                    r.newRegime.totalDeductions,
                    recommended = !oldBetter,
                    modifier = Modifier.weight(1f)
                )
            }

            if (r.taxSavingsBySwitching > 0) {
                Box(
                    Modifier.fillMaxWidth().background(SuccessColor.copy(0.1f), RoundedCornerShape(12.dp)).padding(12.dp)
                ) {
                    Text(
                        "Switch to ${if (r.recommended == "new") "New" else "Old"} regime and save ${r.taxSavingsBySwitching.formatCurrency()}/year (${(r.taxSavingsBySwitching/12).formatCurrency()}/month)",
                        fontSize = 13.sp, color = SuccessColor, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RegimeCard(name: String, tax: Double, rate: Double, monthlyTds: Double, deductions: Double, recommended: Boolean, modifier: Modifier) {
    val border = if (recommended) SuccessColor else BorderColor
    Column(
        modifier.background(if (recommended) SuccessColor.copy(0.08f) else Surface, RoundedCornerShape(14.dp)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (recommended) Text("✅ Best", fontSize = 10.sp, color = SuccessColor, fontWeight = FontWeight.Bold)
        Text(name, fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(tax.formatCurrency(), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (recommended) SuccessColor else ErrorColor)
        Text("Tax payable", fontSize = 10.sp, color = TextMuted)
        Spacer(Modifier.height(6.dp))
        Text("${monthlyTds.formatCurrency()}/mo TDS", fontSize = 11.sp, color = TextSecondary)
        Text("${rate}% effective rate", fontSize = 10.sp, color = TextMuted)
        Text("₹${String.format("%,d", deductions.toLong())} deductions", fontSize = 10.sp, color = TextMuted)
    }
}
