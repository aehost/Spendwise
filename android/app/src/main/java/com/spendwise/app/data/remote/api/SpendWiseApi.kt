package com.spendwise.app.data.remote.api

import com.spendwise.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthResponse>>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<ApiResponse<Map<String, String>>>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<ApiResponse<Map<String, String>>>

    @GET("auth/me")
    suspend fun me(): Response<ApiResponse<UserDto>>

    @DELETE("auth/account")
    suspend fun deleteAccount(): Response<ApiResponse<Map<String, String>>>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiResponse<ForgotPasswordResponse>>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<Map<String, String>>>

    @PUT("auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<Map<String, String>>>
}

interface TransactionApi {
    @GET("transactions")
    suspend fun getTransactions(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("category") category: String? = null,
        @Query("is_pending") isPending: Boolean? = null,
        @Query("is_credit") isCredit: Boolean? = null,
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("bank_account_id") bankAccountId: String? = null,
        @Query("search") search: String? = null
    ): Response<ApiResponse<TransactionListResponse>>

    @POST("transactions")
    suspend fun createTransaction(@Body req: CreateTransactionRequest): Response<ApiResponse<TransactionDto>>

    @PUT("transactions/{id}")
    suspend fun updateTransaction(@Path("id") id: String, @Body req: Map<String, @JvmSuppressWildcards Any?>): Response<ApiResponse<TransactionDto>>

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    @POST("transactions/batch")
    suspend fun batchCreate(@Body req: BatchTransactionRequest): Response<ApiResponse<BatchTransactionResponse>>

    @GET("transactions/summary")
    suspend fun getSummary(@Query("month") month: Int? = null, @Query("year") year: Int? = null): Response<ApiResponse<TransactionSummary>>
}

interface UserApi {
    @GET("user/profile")
    suspend fun getProfile(): Response<ApiResponse<UserDto>>

    @PUT("user/profile")
    suspend fun updateProfile(@Body req: UpdateProfileRequest): Response<ApiResponse<UserDto>>

    @GET("user/sms-scan-ts")
    suspend fun getSmsScanTs(): Response<ApiResponse<SmsScanTsResponse>>

    @PUT("user/sms-scan-ts")
    suspend fun updateSmsScanTs(@Body req: SmsScanTsRequest): Response<ApiResponse<SmsScanTsResponse>>

    @GET("user/bank-accounts")
    suspend fun getBankAccounts(): Response<ApiResponse<List<BankAccountDto>>>

    @POST("user/bank-accounts")
    suspend fun createBankAccount(@Body req: CreateBankAccountRequest): Response<ApiResponse<BankAccountDto>>

    @PUT("user/bank-accounts/{id}")
    suspend fun updateBankAccount(@Path("id") id: String, @Body req: Map<String, @JvmSuppressWildcards Any?>): Response<ApiResponse<BankAccountDto>>

    @DELETE("user/bank-accounts/{id}")
    suspend fun deleteBankAccount(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    @GET("user/credit-cards")
    suspend fun getCreditCards(): Response<ApiResponse<List<CreditCardDto>>>

    @POST("user/credit-cards")
    suspend fun createCreditCard(@Body req: CreateCreditCardRequest): Response<ApiResponse<CreditCardDto>>

    @PUT("user/credit-cards/{id}")
    suspend fun updateCreditCard(@Path("id") id: String, @Body req: Map<String, @JvmSuppressWildcards Any?>): Response<ApiResponse<CreditCardDto>>

    @DELETE("user/credit-cards/{id}")
    suspend fun deleteCreditCard(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    @GET("user/loans")
    suspend fun getLoans(): Response<ApiResponse<List<LoanDto>>>

    @POST("user/loans")
    suspend fun createLoan(@Body req: CreateLoanRequest): Response<ApiResponse<LoanDto>>

    @PUT("user/loans/{id}")
    suspend fun updateLoan(@Path("id") id: String, @Body req: Map<String, @JvmSuppressWildcards Any?>): Response<ApiResponse<LoanDto>>

    @DELETE("user/loans/{id}")
    suspend fun deleteLoan(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    @GET("user/salary")
    suspend fun getSalary(): Response<ApiResponse<SalaryDto>>

    @PUT("user/salary")
    suspend fun updateSalary(@Body req: UpdateSalaryRequest): Response<ApiResponse<Map<String, Any>>>

    @POST("user/salary/received")
    suspend fun markSalaryReceived(@Body req: SalaryReceivedRequest): Response<ApiResponse<SalaryHistoryDto>>

    @GET("user/investments")
    suspend fun getInvestments(): Response<ApiResponse<List<InvestmentDto>>>

    @POST("user/investments")
    suspend fun createInvestment(@Body req: CreateInvestmentRequest): Response<ApiResponse<InvestmentDto>>

    @PUT("user/investments/{id}")
    suspend fun updateInvestment(@Path("id") id: String, @Body req: Map<String, @JvmSuppressWildcards Any?>): Response<ApiResponse<InvestmentDto>>

    @DELETE("user/investments/{id}")
    suspend fun deleteInvestment(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    @GET("user/bills")
    suspend fun getBills(): Response<ApiResponse<List<BillDto>>>

    @POST("user/bills")
    suspend fun createBill(@Body req: CreateBillRequest): Response<ApiResponse<BillDto>>

    @PUT("user/bills/{id}")
    suspend fun updateBill(@Path("id") id: String, @Body req: Map<String, @JvmSuppressWildcards Any?>): Response<ApiResponse<BillDto>>

    @DELETE("user/bills/{id}")
    suspend fun deleteBill(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    @POST("user/bills/{id}/pay")
    suspend fun payBill(@Path("id") id: String): Response<ApiResponse<BillDto>>

    @GET("user/budgets")
    suspend fun getBudgets(@Query("month") month: Int? = null, @Query("year") year: Int? = null): Response<ApiResponse<BudgetsResponse>>

    @PUT("user/budgets")
    suspend fun updateBudgets(@Body req: UpdateBudgetsRequest): Response<ApiResponse<Map<String, Any>>>

    @POST("user/support-ticket")
    suspend fun createSupportTicket(@Body req: CreateSupportTicketRequest): Response<ApiResponse<SupportTicketDto>>

    @GET("user/support-tickets")
    suspend fun getSupportTickets(): Response<ApiResponse<Map<String, List<SupportTicketDto>>>>
}

interface AnalyticsApi {
    @GET("analytics/dashboard")
    suspend fun getDashboard(): Response<ApiResponse<DashboardDto>>

    @GET("analytics/monthly")
    suspend fun getMonthly(@Query("month") month: Int? = null, @Query("year") year: Int? = null): Response<ApiResponse<Map<String, Any>>>

    @GET("analytics/trend")
    suspend fun getTrend(): Response<ApiResponse<List<TrendPoint>>>

    @GET("analytics/categories")
    suspend fun getCategories(@Query("month") month: Int? = null, @Query("year") year: Int? = null): Response<ApiResponse<CategoriesAnalyticsResponse>>
}

interface IntelligenceApi {
    @GET("analytics/intelligence")
    suspend fun getIntelligenceReport(): Response<ApiResponse<IntelligenceReportDto>>

    @POST("analytics/auto-add-bills")
    suspend fun autoAddBills(@Body req: AutoAddBillsRequest): Response<ApiResponse<AutoAddBillsResponse>>

    /** Location + Places API fallback classifier (Tier 5) */
    @POST("analytics/classify-merchant")
    suspend fun classifyMerchant(@Body req: ClassifyMerchantRequest): Response<ApiResponse<ClassifyMerchantResult>>

    /** Comprehensive monthly analytics report */
    @GET("analytics/monthly-report")
    suspend fun getMonthlyReport(
        @Query("month") month: Int? = null,
        @Query("year") year: Int? = null
    ): Response<ApiResponse<MonthlyReportDto>>
}

interface GoalsApi {
    @GET("user/goals")
    suspend fun getGoals(): Response<ApiResponse<List<FinancialGoalDto>>>

    @POST("user/goals")
    suspend fun createGoal(@Body req: CreateGoalRequest): Response<ApiResponse<FinancialGoalDto>>

    @PUT("user/goals/{id}")
    suspend fun updateGoal(
        @Path("id") id: String,
        @Body req: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiResponse<FinancialGoalDto>>

    @POST("user/goals/{id}/contribute")
    suspend fun contributeToGoal(
        @Path("id") id: String,
        @Body req: ContributeGoalRequest
    ): Response<ApiResponse<FinancialGoalDto>>

    @DELETE("user/goals/{id}")
    suspend fun deleteGoal(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>
}

interface FinancialAdvisorApi {
    @GET("analytics/financial-advisor")
    suspend fun getInsights(): Response<ApiResponse<FinancialAdvisorDto>>
}

interface HealthScoreApi {
    @GET("analytics/health-score")
    suspend fun getHealthScore(): Response<ApiResponse<HealthScoreDto>>
}

interface CashFlowApi {
    @GET("analytics/cash-flow")
    suspend fun getCashFlow(): Response<ApiResponse<CashFlowDto>>
}

interface DebtPayoffApi {
    @GET("analytics/debt-payoff")
    suspend fun getDebtPayoff(): Response<ApiResponse<DebtPayoffDto>>
}

interface TaxApi {
    @POST("analytics/tax-estimate")
    suspend fun estimateTax(@Body req: TaxEstimateRequest): Response<ApiResponse<TaxEstimateDto>>
}

interface IouApi {
    @GET("user/iou")
    suspend fun getEntries(@Query("settled") settled: Boolean = false): Response<ApiResponse<List<IouEntryDto>>>

    @GET("user/iou/summary")
    suspend fun getSummary(): Response<ApiResponse<List<IouSummaryDto>>>

    @POST("user/iou")
    suspend fun create(@Body req: CreateIouRequest): Response<ApiResponse<IouEntryDto>>

    @PUT("user/iou/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body req: Map<String, @JvmSuppressWildcards Any?>
    ): Response<ApiResponse<IouEntryDto>>

    @DELETE("user/iou/{id}")
    suspend fun delete(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>
}

interface GmailApi {
    @GET("user/gmail/status")
    suspend fun getStatus(): Response<ApiResponse<GmailStatusDto>>

    @POST("user/gmail/connect")
    suspend fun connect(@Body req: GmailConnectRequest): Response<ApiResponse<GmailStatusDto>>

    @POST("user/gmail/disconnect")
    suspend fun disconnect(): Response<ApiResponse<Map<String, Boolean>>>

    @PUT("user/gmail/sync-timestamp")
    suspend fun updateSyncTimestamp(): Response<ApiResponse<Map<String, String>>>

    // ── Multi-account endpoints ────────────────────────────────
    @GET("user/gmail/accounts")
    suspend fun getAccounts(): Response<ApiResponse<GmailAccountsData>>

    @POST("user/gmail/accounts")
    suspend fun addAccount(@Body req: GmailConnectRequest): Response<ApiResponse<GmailAccountDto>>

    @DELETE("user/gmail/accounts/{id}")
    suspend fun removeAccount(@Path("id") id: String): Response<ApiResponse<Map<String, Boolean>>>

    @PUT("user/gmail/accounts/{id}/sync-timestamp")
    suspend fun updateAccountSyncTimestamp(@Path("id") accountId: String): Response<ApiResponse<Map<String, String>>>
}
