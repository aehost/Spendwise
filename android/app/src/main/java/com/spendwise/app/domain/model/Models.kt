package com.spendwise.app.domain.model

data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: String = "user",
    val currencyCode: String = "INR"
)

data class Transaction(
    val id: String,
    val userId: String = "",
    val amount: Double,
    val merchant: String,
    val categorySlug: String,
    val transactionDate: String,  // YYYY-MM-DD
    val note: String = "",
    val isWaste: Boolean = false,
    val isPending: Boolean = true,
    val isCredit: Boolean = false,
    val loanId: String? = null,
    val creditCardId: String? = null,
    val bankAccountId: String? = null,
    val contactName: String? = null,
    val smsRaw: String? = null,
    val smsId: String? = null,
    // joined
    val accountName: String? = null,
    val accountColor: String? = null
)

data class BankAccount(
    val id: String,
    val name: String,
    val lastFour: String?,
    val balance: Double,
    val color: String = "#6C63FF"
)

data class CreditCard(
    val id: String,
    val name: String,
    val creditLimit: Double,
    val outstanding: Double,
    val dueDay: Int,
    val minDue: Double,
    val color: String = "#EC4899"
)

data class Loan(
    val id: String,
    val name: String,
    val emiAmount: Double,
    val interestRate: Double,
    val outstanding: Double,
    val monthsRemaining: Int,
    val color: String = "#EF4444"
)

data class Bill(
    val id: String,
    val name: String,
    val icon: String,
    val amount: Double,
    val dueDay: Int,
    val paidThisMonth: Boolean
)

data class Budget(
    val categorySlug: String,
    val amount: Double,
    val spent: Double = 0.0,
    val pct: Int? = null
)

data class Investment(
    val id: String,
    val name: String,
    val monthlyAmount: Double,
    val currentBalance: Double
)

data class SalaryConfig(
    val amount: Double,
    val expectedDay: Int,
    val history: List<SalaryRecord> = emptyList()
)

data class SalaryRecord(
    val id: String,
    val amount: Double,
    val receivedDate: String,
    val note: String = ""
)

data class DashboardSummary(
    val month: Int,
    val year: Int,
    val salaryAmount: Double,
    val salaryExpectedDay: Int,
    val bankBalance: Double,
    val ccOutstanding: Double,
    val totalSpent: Double,
    val totalCredit: Double,
    val pendingCount: Int,
    val emiTotal: Double,
    val emiBurdenPct: Int,
    val savings: Double,
    val savingsRate: Int,
    val burnRate: Double,
    val projectedSpend: Double,
    val budgetAlerts: List<BudgetAlert>,
    val byCategory: List<CategoryTotal>
)

data class BudgetAlert(
    val categorySlug: String,
    val budget: Double,
    val spent: Double,
    val pct: Int
)

data class CategoryTotal(
    val categorySlug: String,
    val total: Double,
    val count: Int = 0
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)

// ── Intelligence / AI models ──────────────────────────────────

data class RecurringBillSuggestion(
    val merchant: String,
    val categorySlug: String,
    val avgAmount: Double,
    val occurrences: Int,
    val cycle: String,            // monthly | weekly | biweekly | quarterly | annual
    val avgIntervalDays: Double,
    val lastSeen: String,
    val dueDayEstimate: Int?,
    val confidence: Int           // 0-95
)

data class FinancialInsight(
    val type: String,             // warning | success | alert | tip | info
    val message: String,
    val action: String? = null    // review_spending | set_budget | view_bills …
)

data class CashFlowForecast(
    val salary: Double,
    val projectedSpend: Int,
    val remainingBills: Int,
    val emiTotal: Int,
    val monthEndBalance: Int,
    val isOverspending: Boolean,
    val burnRate: Int
)

data class SavingsOpportunity(
    val categorySlug: String,
    val thisMonth: Double,
    val historicalAvg: Int,
    val overspend: Double
)

data class IntelligenceReport(
    val recurringBills: List<RecurringBillSuggestion>,
    val insights: List<FinancialInsight>,
    val forecast: CashFlowForecast,
    val savingsOpportunities: List<SavingsOpportunity>
)

// ── Financial Goals ───────────────────────────────────────────

data class FinancialGoal(
    val id: String,
    val title: String,
    val description: String = "",
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val deadline: String? = null,
    val categorySlug: String = "savings",
    val icon: String = "🎯",
    val color: String = "#6C63FF",
    val isCompleted: Boolean = false,
    val autoContribute: Boolean = false,
    val monthlyTarget: Double = 0.0,
    val progressPct: Int = 0       // computed client-side
)

// ── Gmail integration ─────────────────────────────────────────

data class GmailAccount(
    val id: String,
    val gmailEmail: String,
    val lastSyncedAt: String?,
    val isActive: Boolean = true
)

data class GmailStatus(
    val connected: Boolean,
    val gmailEmail: String?,
    val lastSyncedAt: String?,
    val accounts: List<GmailAccount> = emptyList()
)

enum class EmailType {
    BILL,           // CC statement / recurring bill due
    SALARY_CREDIT,  // salary credited to bank account
    IMPS_CREDIT,    // money received via IMPS
    NEFT_CREDIT,    // money received via NEFT
    UPI_CREDIT,     // money received via UPI / BHIM
    CC_PAYMENT,     // credit card payment made / received by bank
    UNKNOWN
}

data class EmailBillDetection(
    val billName: String,
    val amount: Double?,
    val dueDate: String?,         // YYYY-MM-DD
    val dueDayOfMonth: Int?,
    val statementDate: String?,
    val cardOrAccount: String?,
    val bankName: String?,
    val emailSubject: String,
    val emailDate: String,
    val isMinimumDue: Boolean = false,
    val emailType: EmailType = EmailType.BILL,
    val senderName: String? = null  // payer name for IMPS/NEFT/UPI credits
)
