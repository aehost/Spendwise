package com.spendwise.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.spendwise.app.domain.model.User

// ── Generic wrapper ────────────────────────────────────────────
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data")    val data: T?,
    @SerializedName("error")   val error: String?,
    @SerializedName("code")    val code: String?
)

// ── Auth ───────────────────────────────────────────────────────
data class LoginRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String
)
data class RegisterRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("name")     val name: String = ""
)
data class RefreshRequest(@SerializedName("refreshToken") val refreshToken: String)
data class LogoutRequest(@SerializedName("refreshToken") val refreshToken: String)

data class AuthResponse(
    @SerializedName("accessToken")  val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("user")         val user: UserDto
)

data class UserDto(
    @SerializedName("id")            val id: String,
    @SerializedName("email")         val email: String,
    @SerializedName("name")          val name: String,
    @SerializedName("role")          val role: String? = null,        // nullable: Gson bypasses constructor defaults
    @SerializedName("currency_code") val currencyCode: String? = null  // nullable: server may omit this field
) {
    /** Safe mapping to domain model — Gson bypasses constructors so defaults
     *  like "INR" are never set; always guard with ?: here. */
    fun toDomain() = User(
        id           = id,
        email        = email,
        name         = name        ?: "",
        role         = role        ?: "user",
        currencyCode = currencyCode ?: "INR"
    )
}

// ── Transaction ────────────────────────────────────────────────
data class TransactionDto(
    val id: String,
    val amount: Double,
    val merchant: String,
    @SerializedName("category_slug")    val categorySlug: String,
    @SerializedName("transaction_date") val transactionDate: String,
    val note: String = "",
    @SerializedName("is_waste")         val isWaste: Boolean = false,
    @SerializedName("is_pending")       val isPending: Boolean = true,
    @SerializedName("is_credit")        val isCredit: Boolean = false,
    @SerializedName("loan_id")          val loanId: String? = null,
    @SerializedName("credit_card_id")   val creditCardId: String? = null,
    @SerializedName("bank_account_id")  val bankAccountId: String? = null,
    @SerializedName("contact_name")     val contactName: String? = null,
    @SerializedName("sms_raw")          val smsRaw: String? = null,
    @SerializedName("sms_id")           val smsId: String? = null,
    @SerializedName("account_name")     val accountName: String? = null,
    @SerializedName("account_color")    val accountColor: String? = null
)

data class TransactionListResponse(
    @SerializedName("transactions") val transactions: List<TransactionDto>,
    @SerializedName("total")        val total: Int,
    @SerializedName("page")         val page: Int,
    @SerializedName("limit")        val limit: Int,
    @SerializedName("pages")        val pages: Int
)

data class CreateTransactionRequest(
    val amount: Double,
    val merchant: String,
    @SerializedName("category_slug")    val categorySlug: String,
    @SerializedName("transaction_date") val transactionDate: String,
    val note: String = "",
    @SerializedName("is_waste")         val isWaste: Boolean = false,
    @SerializedName("is_pending")       val isPending: Boolean = false,
    @SerializedName("is_credit")        val isCredit: Boolean = false,
    @SerializedName("bank_account_id")  val bankAccountId: String? = null,
    @SerializedName("sms_raw")          val smsRaw: String? = null,
    @SerializedName("sms_id")           val smsId: String? = null
)

data class BatchTransactionRequest(val transactions: List<CreateTransactionRequest>)
data class BatchTransactionResponse(val inserted: Int, val skipped: Int)

data class TransactionSummary(
    @SerializedName("total_debit")  val totalDebit: Double,
    @SerializedName("total_credit") val totalCredit: Double,
    @SerializedName("pending_count") val pendingCount: Int,
    @SerializedName("by_category") val byCategory: List<CategoryTotalDto>,
    val month: Int, val year: Int
)

data class CategoryTotalDto(
    @SerializedName("category_slug") val categorySlug: String,
    val total: Double,
    val count: Int
)

// ── User / Profile ────────────────────────────────────────────
data class UpdateProfileRequest(
    @SerializedName("name")  val name: String?,
    @SerializedName("phone") val phone: String?
)

data class BankAccountDto(
    val id: String,
    val name: String,
    @SerializedName("last_four") val lastFour: String?,
    val balance: Double,
    val color: String = "#6C63FF"
)
data class CreateBankAccountRequest(val name: String, @SerializedName("last_four") val lastFour: String?, val balance: Double = 0.0, val color: String = "#6C63FF")

data class CreditCardDto(
    val id: String, val name: String,
    @SerializedName("credit_limit") val creditLimit: Double,
    val outstanding: Double,
    @SerializedName("due_day") val dueDay: Int,
    @SerializedName("min_due") val minDue: Double,
    val color: String = "#EC4899"
)
data class CreateCreditCardRequest(val name: String, @SerializedName("credit_limit") val creditLimit: Double, val outstanding: Double = 0.0, @SerializedName("due_day") val dueDay: Int = 1, @SerializedName("min_due") val minDue: Double = 0.0, val color: String = "#EC4899")

data class LoanDto(
    val id: String, val name: String,
    @SerializedName("emi_amount")       val emiAmount: Double,
    @SerializedName("interest_rate")    val interestRate: Double,
    val outstanding: Double,
    @SerializedName("months_remaining") val monthsRemaining: Int,
    val color: String = "#EF4444"
)
data class CreateLoanRequest(val name: String, @SerializedName("emi_amount") val emiAmount: Double, @SerializedName("interest_rate") val interestRate: Double, val outstanding: Double = 0.0, @SerializedName("months_remaining") val monthsRemaining: Int = 0, val color: String = "#EF4444")

data class SalaryDto(
    val amount: Double,
    @SerializedName("expected_day") val expectedDay: Int,
    val history: List<SalaryHistoryDto> = emptyList()
)
data class SalaryHistoryDto(val id: String, val amount: Double, @SerializedName("received_date") val receivedDate: String, val note: String = "")
data class UpdateSalaryRequest(val amount: Double, @SerializedName("expected_day") val expectedDay: Int)
data class SalaryReceivedRequest(val amount: Double, @SerializedName("received_date") val receivedDate: String, val note: String = "")

data class BillDto(val id: String, val name: String, val icon: String, val amount: Double, @SerializedName("due_day") val dueDay: Int, @SerializedName("paid_this_month") val paidThisMonth: Boolean)
data class CreateBillRequest(val name: String, val icon: String = "💡", val amount: Double, @SerializedName("due_day") val dueDay: Int)

data class InvestmentDto(val id: String, val name: String, @SerializedName("monthly_amount") val monthlyAmount: Double, @SerializedName("current_balance") val currentBalance: Double)
data class CreateInvestmentRequest(val name: String, @SerializedName("monthly_amount") val monthlyAmount: Double = 0.0, @SerializedName("current_balance") val currentBalance: Double = 0.0)

data class BudgetEntry(@SerializedName("category_slug") val categorySlug: String, val amount: Double)
data class UpdateBudgetsRequest(val budgets: List<BudgetEntry>, val month: Int? = null, val year: Int? = null)
data class BudgetsResponse(val month: Int, val year: Int, val budgets: List<BudgetDto>)
data class BudgetDto(@SerializedName("category_slug") val categorySlug: String, val amount: Double, val month: Int, val year: Int)

data class SmsScanTsRequest(@SerializedName("sms_scan_from_ms") val smsScanFromMs: Long)
data class SmsScanTsResponse(@SerializedName("sms_scan_from_ms") val smsScanFromMs: Long)

// ── Analytics ─────────────────────────────────────────────────
data class DashboardDto(
    val month: Int, val year: Int,
    val salary: SalaryInfoDto,
    @SerializedName("bank_balance")     val bankBalance: Double,
    @SerializedName("cc_outstanding")   val ccOutstanding: Double,
    @SerializedName("total_spent")      val totalSpent: Double,
    @SerializedName("total_credit")     val totalCredit: Double,
    @SerializedName("pending_count")    val pendingCount: Int,
    @SerializedName("emi_total")        val emiTotal: Double,
    @SerializedName("emi_burden_pct")   val emiBurdenPct: Int,
    val savings: Double,
    @SerializedName("savings_rate")     val savingsRate: Int,
    @SerializedName("burn_rate")        val burnRate: Double,
    @SerializedName("projected_spend")  val projectedSpend: Double,
    @SerializedName("budget_alerts")    val budgetAlerts: List<BudgetAlertDto>,
    @SerializedName("by_category")      val byCategory: List<CategoryTotalDto>
)

data class SalaryInfoDto(
    @SerializedName("amount")       val amount: Double,
    @SerializedName("expected_day") val expectedDay: Int
)
data class BudgetAlertDto(
    @SerializedName("category_slug") val categorySlug: String,
    @SerializedName("budget")        val budget: Double,
    @SerializedName("spent")         val spent: Double,
    @SerializedName("pct")           val pct: Int
)

data class TrendPoint(
    @SerializedName("month")  val month: String,
    @SerializedName("spent")  val spent: Double,
    @SerializedName("income") val income: Double
)
data class CategoryAnalysis(
    @SerializedName("category_slug") val categorySlug: String,
    @SerializedName("spent")         val spent: Double,
    @SerializedName("budget")        val budget: Double,
    @SerializedName("pct")           val pct: Int?
)
data class CategoriesAnalyticsResponse(
    @SerializedName("month")      val month: Int,
    @SerializedName("year")       val year: Int,
    @SerializedName("categories") val categories: List<CategoryAnalysis>
)

// ── Intelligence / AI ─────────────────────────────────────────
data class RecurringBillSuggestionDto(
    val merchant: String,
    @SerializedName("category_slug")    val categorySlug: String,
    @SerializedName("avg_amount")       val avgAmount: Double,
    val occurrences: Int,
    val cycle: String,                  // monthly | weekly | biweekly | quarterly | annual
    @SerializedName("avg_interval_days") val avgIntervalDays: Double,
    @SerializedName("last_seen")        val lastSeen: String,
    @SerializedName("due_day_estimate") val dueDayEstimate: Int?,
    val confidence: Int                 // 0-95
)

data class FinancialInsightDto(
    val type: String,                   // warning | success | alert | tip | info
    val message: String,
    val action: String? = null
)

data class CashFlowForecastDto(
    val salary: Double,
    @SerializedName("projected_spend")  val projectedSpend: Int,
    @SerializedName("remaining_bills")  val remainingBills: Int,
    @SerializedName("emi_total")        val emiTotal: Int,
    @SerializedName("month_end_balance") val monthEndBalance: Int,
    @SerializedName("is_overspending")  val isOverspending: Boolean,
    @SerializedName("burn_rate")        val burnRate: Int
)

data class SavingsOpportunityDto(
    @SerializedName("category_slug")    val categorySlug: String,
    @SerializedName("this_month")       val thisMonth: Double,
    @SerializedName("historical_avg")   val historicalAvg: Int,
    val overspend: Double
)

data class IntelligenceReportDto(
    @SerializedName("recurring_bills")       val recurringBills: List<RecurringBillSuggestionDto>,
    val insights: List<FinancialInsightDto>,
    val forecast: CashFlowForecastDto,
    @SerializedName("savings_opportunities") val savingsOpportunities: List<SavingsOpportunityDto>
)

data class AutoAddBillEntry(
    val name: String,
    val icon: String,
    val amount: Double,
    @SerializedName("due_day") val dueDay: Int
)

data class AutoAddBillsRequest(val bills: List<AutoAddBillEntry>)
data class AutoAddBillsResponse(val added: Int, val skipped: Int)

// ── Location-based merchant classification ────────────────────
data class ClassifyMerchantRequest(
    val merchant: String,
    @SerializedName("sms_body")  val smsBody: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double = 50.0
)

data class ClassifyMerchantResult(
    @SerializedName("category_slug") val categorySlug: String,
    @SerializedName("display_name")  val displayName: String? = null,
    @SerializedName("sub_category")  val subCategory: String? = null,
    val confidence: Double,
    val source: String = "places"   // "places" | "llm" | "keyword"
)

// ── Comprehensive monthly report ─────────────────────────────

data class MonthlyReportDto(
    val month: Int,
    val year: Int,
    // ── Core financials ──────────────────────────────────────
    val summary: MonthSummaryDto,
    // ── Category breakdown ───────────────────────────────────
    @SerializedName("category_breakdown") val categoryBreakdown: List<CategoryBreakdownDto>,
    // ── Daily spending heatmap ───────────────────────────────
    @SerializedName("daily_spending")     val dailySpending: List<DailySpendDto>,
    // ── Top merchants ────────────────────────────────────────
    @SerializedName("top_merchants")      val topMerchants: List<TopMerchantDto>,
    // ── Day-of-week pattern ──────────────────────────────────
    @SerializedName("day_of_week")        val dayOfWeek: List<DayOfWeekDto>,
    // ── Waste / impulse analysis ─────────────────────────────
    @SerializedName("waste_analysis")     val wasteAnalysis: WasteAnalysisDto,
    // ── Budget performance ───────────────────────────────────
    @SerializedName("budget_performance") val budgetPerformance: List<BudgetPerformanceDto>,
    // ── Upcoming & auto-detected bills ───────────────────────
    @SerializedName("upcoming_bills")     val upcomingBills: List<UpcomingBillDto>,
    // ── Financial health score ───────────────────────────────
    @SerializedName("health_score")       val healthScore: FinancialHealthScoreDto,
    // ── Anomalies ────────────────────────────────────────────
    val anomalies: List<AnomalyDto>,
    // ── Insights & tips ──────────────────────────────────────
    val insights: List<FinancialInsightDto>,
    // ── Trend vs last month ──────────────────────────────────
    val trends: MonthTrendsDto
)

data class MonthSummaryDto(
    val income: Double,
    @SerializedName("total_spent")   val totalSpent: Double,
    val savings: Double,
    @SerializedName("savings_rate")  val savingsRate: Int,
    @SerializedName("vs_last_month") val vsLastMonth: Int,     // % change in spending
    @SerializedName("burn_rate")     val burnRate: Double,     // daily average spend
    @SerializedName("peak_day")      val peakDay: String,      // date with highest spend
    @SerializedName("peak_amount")   val peakAmount: Double,
    @SerializedName("transaction_count") val transactionCount: Int,
    @SerializedName("avg_transaction")   val avgTransaction: Double
)

data class CategoryBreakdownDto(
    @SerializedName("category_slug")  val categorySlug: String,
    val amount: Double,
    val count: Int,
    @SerializedName("pct_of_total")   val pctOfTotal: Int,
    @SerializedName("vs_last_month")  val vsLastMonth: Int,    // % change
    val budget: Double?,
    @SerializedName("budget_pct")     val budgetPct: Int?,     // spent/budget * 100
    @SerializedName("budget_status")  val budgetStatus: String // ok | warning | over
)

data class DailySpendDto(
    val date: String,               // YYYY-MM-DD
    val amount: Double,
    val count: Int
)

data class TopMerchantDto(
    val merchant: String,
    @SerializedName("category_slug") val categorySlug: String,
    @SerializedName("total_spent")   val totalSpent: Double,
    @SerializedName("visit_count")   val visitCount: Int,
    @SerializedName("avg_amount")    val avgAmount: Double,
    @SerializedName("last_visit")    val lastVisit: String
)

data class DayOfWeekDto(
    val day: String,                // Mon | Tue | Wed ...
    @SerializedName("avg_spend")    val avgSpend: Double,
    @SerializedName("transaction_count") val transactionCount: Int
)

data class WasteAnalysisDto(
    @SerializedName("total_waste")         val totalWaste: Double,
    @SerializedName("waste_pct")           val wastePct: Int,
    @SerializedName("top_waste_category")  val topWasteCategory: String?,
    @SerializedName("waste_transactions")  val wasteTransactions: Int
)

data class BudgetPerformanceDto(
    @SerializedName("category_slug") val categorySlug: String,
    val budget: Double,
    val spent: Double,
    val variance: Double,           // spent - budget (positive = overspent)
    val status: String,             // ok | warning | over
    @SerializedName("days_remaining") val daysRemaining: Int,
    @SerializedName("projected_end") val projectedEnd: Double
)

data class UpcomingBillDto(
    val name: String,
    val amount: Double,
    @SerializedName("due_date") val dueDate: String,
    @SerializedName("days_until") val daysUntil: Int,
    val paid: Boolean,
    val icon: String
)

data class FinancialHealthScoreDto(
    val score: Int,                 // 0-100
    val grade: String,              // A+ | A | B | C | D
    val factors: List<HealthFactorDto>
)

data class HealthFactorDto(
    val name: String,
    val score: Int,
    val status: String,             // good | neutral | bad
    val detail: String
)

data class AnomalyDto(
    val merchant: String,
    @SerializedName("category_slug") val categorySlug: String,
    val amount: Double,
    val date: String,
    val reason: String,             // "3x above average" | "first time merchant" etc.
    @SerializedName("anomaly_type") val anomalyType: String  // high_amount | unusual_merchant | frequency
)

data class MonthTrendsDto(
    @SerializedName("spending_trend")         val spendingTrend: String, // up | down | stable
    @SerializedName("spending_trend_pct")     val spendingTrendPct: Int,
    @SerializedName("top_growing_category")   val topGrowingCategory: String?,
    @SerializedName("top_shrinking_category") val topShrinkingCategory: String?,
    @SerializedName("new_merchants")          val newMerchants: Int,
    @SerializedName("recurring_merchants")    val recurringMerchants: Int
)

// ── Financial Goals ───────────────────────────────────────────
data class FinancialGoalDto(
    val id: String,
    val title: String,
    val description: String = "",
    @SerializedName("target_amount")    val targetAmount: Double,
    @SerializedName("current_amount")   val currentAmount: Double = 0.0,
    val deadline: String? = null,
    @SerializedName("category_slug")    val categorySlug: String = "savings",
    val icon: String = "🎯",
    val color: String = "#6C63FF",
    @SerializedName("is_completed")     val isCompleted: Boolean = false,
    @SerializedName("auto_contribute")  val autoContribute: Boolean = false,
    @SerializedName("monthly_target")   val monthlyTarget: Double = 0.0
)

data class CreateGoalRequest(
    val title: String,
    val description: String = "",
    @SerializedName("target_amount")    val targetAmount: Double,
    @SerializedName("current_amount")   val currentAmount: Double = 0.0,
    val deadline: String? = null,
    @SerializedName("category_slug")    val categorySlug: String = "savings",
    val icon: String = "🎯",
    val color: String = "#6C63FF",
    @SerializedName("auto_contribute")  val autoContribute: Boolean = false,
    @SerializedName("monthly_target")   val monthlyTarget: Double = 0.0
)

data class ContributeGoalRequest(val amount: Double, val note: String = "")

// ── Password reset / change ───────────────────────────────────
data class ForgotPasswordRequest(@SerializedName("email") val email: String)
data class ForgotPasswordResponse(
    @SerializedName("message") val message: String,
    @SerializedName("otp")     val otp: String?   // returned for demo — no SMTP
)

data class ResetPasswordRequest(
    @SerializedName("email")       val email: String,
    @SerializedName("otp")         val otp: String,
    @SerializedName("newPassword") val newPassword: String
)

data class ChangePasswordRequest(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword")     val newPassword: String
)

// ── Support tickets ───────────────────────────────────────────
data class CreateSupportTicketRequest(
    @SerializedName("subject")     val subject: String,
    @SerializedName("description") val description: String,
    @SerializedName("category")    val category: String? = null
)

data class SupportTicketDto(
    @SerializedName("id")         val id: String,
    @SerializedName("subject")    val subject: String,
    @SerializedName("status")     val status: String,
    @SerializedName("priority")   val priority: String? = null,
    @SerializedName("category")   val category: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

// ── Gmail ─────────────────────────────────────────────────────
data class GmailAccountDto(
    @SerializedName("id")              val id: String,
    @SerializedName("gmail_email")     val gmailEmail: String,
    @SerializedName("last_synced_at")  val lastSyncedAt: String?,
    @SerializedName("is_active")       val isActive: Boolean = true
)

data class GmailAccountsData(
    @SerializedName("accounts") val accounts: List<GmailAccountDto>
)

data class GmailStatusDto(
    val connected: Boolean,
    @SerializedName("gmail_email")      val gmailEmail: String?,
    @SerializedName("last_synced_at")   val lastSyncedAt: String?,
    @SerializedName("accounts")         val accounts: List<GmailAccountDto>? = null
)

data class GmailConnectRequest(
    @SerializedName("gmail_email")      val gmailEmail: String,
    @SerializedName("access_token")     val accessToken: String,
    @SerializedName("refresh_token")    val refreshToken: String?,
    @SerializedName("token_expiry")     val tokenExpiry: Long?
)

data class EmailBillDetectionDto(
    @SerializedName("bill_name")        val billName: String,
    val amount: Double?,
    @SerializedName("due_date")         val dueDate: String?,
    @SerializedName("due_day_of_month") val dueDayOfMonth: Int?,
    @SerializedName("statement_date")   val statementDate: String?,
    @SerializedName("card_or_account")  val cardOrAccount: String?,
    @SerializedName("bank_name")        val bankName: String?,
    @SerializedName("email_subject")    val emailSubject: String,
    @SerializedName("email_date")       val emailDate: String,
    @SerializedName("is_minimum_due")   val isMinimumDue: Boolean = false
)

// ── AI Finance Coach ──────────────────────────────────────────
data class AiCoachMessage(
    @SerializedName("role")    val role: String,
    @SerializedName("content") val content: String
)
data class AiCoachRequest(
    @SerializedName("message") val message: String,
    @SerializedName("history") val history: List<AiCoachMessage> = emptyList()
)
data class AiCoachResponse(
    @SerializedName("reply")          val reply: String,
    @SerializedName("input_tokens")   val inputTokens: Int = 0,
    @SerializedName("output_tokens")  val outputTokens: Int = 0
)

// ── Financial Health Score ────────────────────────────────────
data class HealthFactorDto(
    @SerializedName("name")   val name: String,
    @SerializedName("score")  val score: Int,
    @SerializedName("max")    val max: Int,
    @SerializedName("pct")    val pct: Int,
    @SerializedName("status") val status: String,
    @SerializedName("detail") val detail: String,
    @SerializedName("tip")    val tip: String
)
data class HealthScoreDto(
    @SerializedName("score")   val score: Int,
    @SerializedName("grade")   val grade: String,
    @SerializedName("level")   val level: String,
    @SerializedName("factors") val factors: List<HealthFactorDto>
)

// ── Cash Flow Calendar ────────────────────────────────────────
data class CashFlowEventDto(
    @SerializedName("type")     val type: String,
    @SerializedName("label")    val label: String,
    @SerializedName("amount")   val amount: Double,
    @SerializedName("icon")     val icon: String,
    @SerializedName("category") val category: String
)
data class CashFlowDayDto(
    @SerializedName("date")               val date: String,
    @SerializedName("events")             val events: List<CashFlowEventDto>,
    @SerializedName("projected_balance")  val projectedBalance: Double,
    @SerializedName("is_low_balance")     val isLowBalance: Boolean,
    @SerializedName("is_negative")        val isNegative: Boolean
)
data class CashFlowDto(
    @SerializedName("starting_balance")      val startingBalance: Double,
    @SerializedName("salary_amount")         val salaryAmount: Double,
    @SerializedName("salary_day")            val salaryDay: Int,
    @SerializedName("monthly_bills_total")   val monthlyBillsTotal: Double,
    @SerializedName("monthly_emi_total")     val monthlyEmiTotal: Double,
    @SerializedName("daily_spend_estimate")  val dailySpendEstimate: Double,
    @SerializedName("events")               val events: List<CashFlowDayDto>
)

// ── Debt Payoff Planner ───────────────────────────────────────
data class DebtItemDto(
    @SerializedName("id")               val id: String,
    @SerializedName("name")             val name: String,
    @SerializedName("type")             val type: String,
    @SerializedName("outstanding")      val outstanding: Double,
    @SerializedName("monthly_payment")  val monthlyPayment: Double,
    @SerializedName("interest_rate")    val interestRate: Double,
    @SerializedName("color")            val color: String
)
data class PayoffStrategyDto(
    @SerializedName("months")        val months: Int,
    @SerializedName("total_interest") val totalInterest: Double,
    @SerializedName("payoff_date")   val payoffDate: String,
    @SerializedName("order_names")   val orderNames: List<String>,
    @SerializedName("description")   val description: String
)
data class DebtPayoffDto(
    @SerializedName("total_debt")             val totalDebt: Double,
    @SerializedName("total_monthly_payment")  val totalMonthlyPayment: Double,
    @SerializedName("debts")                  val debts: List<DebtItemDto>,
    @SerializedName("snowball")               val snowball: PayoffStrategyDto,
    @SerializedName("avalanche")              val avalanche: PayoffStrategyDto,
    @SerializedName("recommended")            val recommended: String,
    @SerializedName("interest_saved_by_avalanche") val interestSavedByAvalanche: Double
)

// ── Tax Planning ──────────────────────────────────────────────
data class TaxEstimateRequest(
    @SerializedName("annual_salary")        val annualSalary: Double,
    @SerializedName("other_income")         val otherIncome: Double = 0.0,
    @SerializedName("section_80c")          val section80c: Double = 0.0,
    @SerializedName("section_80d")          val section80d: Double = 0.0,
    @SerializedName("hra_exemption")        val hraExemption: Double = 0.0,
    @SerializedName("nps_80ccd")            val nps80ccd: Double = 0.0,
    @SerializedName("home_loan_interest")   val homeLoanInterest: Double = 0.0
)
data class TaxRegimeDto(
    @SerializedName("total_deductions")  val totalDeductions: Double,
    @SerializedName("taxable_income")    val taxableIncome: Double,
    @SerializedName("tax_before_cess")   val taxBeforeCess: Double,
    @SerializedName("total_tax")         val totalTax: Double,
    @SerializedName("effective_rate")    val effectiveRate: Double,
    @SerializedName("monthly_tds")       val monthlyTds: Double
)
data class TaxEstimateDto(
    @SerializedName("gross_income")              val grossIncome: Double,
    @SerializedName("old_regime")                val oldRegime: TaxRegimeDto,
    @SerializedName("new_regime")                val newRegime: TaxRegimeDto,
    @SerializedName("recommended")               val recommended: String,
    @SerializedName("tax_savings_by_switching")  val taxSavingsBySwitching: Double,
    @SerializedName("suggestions")               val suggestions: List<String>
)

// ── IOU Tracker ───────────────────────────────────────────────
data class IouEntryDto(
    @SerializedName("id")           val id: String,
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("amount")       val amount: Double,
    @SerializedName("direction")    val direction: String,  // "lent" | "borrowed"
    @SerializedName("description")  val description: String?,
    @SerializedName("date")         val date: String,
    @SerializedName("is_settled")   val isSettled: Boolean = false,
    @SerializedName("settled_at")   val settledAt: String? = null
)
data class CreateIouRequest(
    @SerializedName("contact_name") val contactName: String,
    @SerializedName("amount")       val amount: Double,
    @SerializedName("direction")    val direction: String,
    @SerializedName("description")  val description: String? = null,
    @SerializedName("date")         val date: String
)
data class IouSummaryDto(
    @SerializedName("contact_name")   val contactName: String,
    @SerializedName("total_lent")     val totalLent: Double,
    @SerializedName("total_borrowed") val totalBorrowed: Double,
    @SerializedName("net")            val net: Double,
    @SerializedName("count")          val count: Int
)
