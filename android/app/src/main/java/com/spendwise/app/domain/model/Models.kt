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
