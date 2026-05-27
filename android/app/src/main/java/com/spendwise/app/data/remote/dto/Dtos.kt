package com.spendwise.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Generic wrapper ────────────────────────────────────────────
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String?,
    val code: String?
)

// ── Auth ───────────────────────────────────────────────────────
data class LoginRequest(val email: String, val password: String)
data class RegisterRequest(val email: String, val password: String, val name: String = "")
data class RefreshRequest(@SerializedName("refreshToken") val refreshToken: String)
data class LogoutRequest(@SerializedName("refreshToken") val refreshToken: String)

data class AuthResponse(
    @SerializedName("accessToken")  val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val email: String,
    val name: String,
    val role: String = "user",
    @SerializedName("currency_code") val currencyCode: String = "INR"
)

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
    val transactions: List<TransactionDto>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val pages: Int
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
data class UpdateProfileRequest(val name: String?, val phone: String?)

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

data class SalaryInfoDto(val amount: Double, @SerializedName("expected_day") val expectedDay: Int)
data class BudgetAlertDto(@SerializedName("category_slug") val categorySlug: String, val budget: Double, val spent: Double, val pct: Int)

data class TrendPoint(val month: String, val spent: Double, val income: Double)
data class CategoryAnalysis(@SerializedName("category_slug") val categorySlug: String, val spent: Double, val budget: Double, val pct: Int?)
data class CategoriesAnalyticsResponse(val month: Int, val year: Int, val categories: List<CategoryAnalysis>)
