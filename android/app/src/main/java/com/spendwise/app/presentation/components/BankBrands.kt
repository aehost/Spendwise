package com.spendwise.app.presentation.components

import androidx.compose.ui.graphics.Color

/**
 * Brand identity for a bank, used to render recognizable, premium card
 * visuals WITHOUT shipping copyrighted logo image files. We approximate each
 * bank's identity with its signature colour gradient + a short text mark, which
 * is legally safe and renders crisply at any size.
 */
data class BankBrand(
    val label: String,         // display name, e.g. "Axis Bank"
    val mark: String,          // short mark, e.g. "AXIS"
    val gradient: List<Color>  // brand gradient (top-left → bottom-right)
)

object BankBrands {

    // keyword (lowercase) → brand. First keyword match wins, so order specifics first.
    private val table: List<Pair<List<String>, BankBrand>> = listOf(
        listOf("hdfc") to BankBrand("HDFC Bank", "HDFC", listOf(Color(0xFF004C8F), Color(0xFF00264D))),
        listOf("icici") to BankBrand("ICICI Bank", "ICICI", listOf(Color(0xFFF37920), Color(0xFFAE282E))),
        listOf("axis") to BankBrand("Axis Bank", "AXIS", listOf(Color(0xFF97144D), Color(0xFF5E0C30))),
        listOf("sbi", "state bank") to BankBrand("SBI", "SBI", listOf(Color(0xFF1A3C8B), Color(0xFF0E2456))),
        listOf("kotak") to BankBrand("Kotak", "KOTAK", listOf(Color(0xFFC9252C), Color(0xFF12284B))),
        listOf("yes bank", "yesbank") to BankBrand("Yes Bank", "YES", listOf(Color(0xFF00518F), Color(0xFF00264D))),
        listOf("indusind") to BankBrand("IndusInd", "INDUS", listOf(Color(0xFF8B1A1A), Color(0xFF4D0E0E))),
        listOf("idfc") to BankBrand("IDFC First", "IDFC", listOf(Color(0xFF9C1D26), Color(0xFF5E1117))),
        listOf("amex", "american express") to BankBrand("American Express", "AMEX", listOf(Color(0xFF2671B9), Color(0xFF16487A))),
        listOf("citi") to BankBrand("Citi", "CITI", listOf(Color(0xFF003B70), Color(0xFF00224A))),
        listOf("hsbc") to BankBrand("HSBC", "HSBC", listOf(Color(0xFFDB0011), Color(0xFF7A000A))),
        listOf("rbl") to BankBrand("RBL Bank", "RBL", listOf(Color(0xFFD4202C), Color(0xFF7A1219))),
        listOf("standard chartered", "stanchart", "sc bank") to BankBrand("Standard Chartered", "SC", listOf(Color(0xFF00857C), Color(0xFF004D48))),
        listOf("federal") to BankBrand("Federal Bank", "FED", listOf(Color(0xFFF7A800), Color(0xFFB37700))),
        listOf("au ", "au small", "aubank") to BankBrand("AU Bank", "AU", listOf(Color(0xFF5B2D90), Color(0xFF34195A))),
        listOf("idbi") to BankBrand("IDBI Bank", "IDBI", listOf(Color(0xFF0E7C3A), Color(0xFF084D24))),
        listOf("pnb", "punjab national") to BankBrand("PNB", "PNB", listOf(Color(0xFFA8122B), Color(0xFF5E0A18))),
        listOf("bob", "bank of baroda", "baroda") to BankBrand("Bank of Baroda", "BOB", listOf(Color(0xFFF26522), Color(0xFFA8430F))),
        listOf("canara") to BankBrand("Canara Bank", "CANARA", listOf(Color(0xFF00529B), Color(0xFF002E57))),
        listOf("union") to BankBrand("Union Bank", "UNION", listOf(Color(0xFFC8102E), Color(0xFF7A0A1C))),
        listOf("paytm") to BankBrand("Paytm", "PAYTM", listOf(Color(0xFF00BAF2), Color(0xFF013E5C))),
    )

    // Fallback gradient for unknown banks (neutral premium slate).
    private val default = BankBrand("Card", "CARD", listOf(Color(0xFF334155), Color(0xFF1E293B)))

    fun of(name: String?): BankBrand {
        val n = name?.lowercase()?.trim() ?: return default
        if (n.isEmpty()) return default
        for ((keys, brand) in table) {
            if (keys.any { n.contains(it) }) return brand
        }
        return default
    }
}
