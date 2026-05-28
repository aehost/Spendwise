package com.spendwise.app.di

import com.spendwise.app.BuildConfig
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.data.remote.api.*
import com.spendwise.app.data.remote.interceptor.AuthInterceptor
import com.spendwise.app.data.remote.interceptor.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideAuthInterceptor(tokenManager: TokenManager) = AuthInterceptor(tokenManager)

    @Provides @Singleton
    fun provideTokenAuthenticator(tokenManager: TokenManager) = TokenAuthenticator(tokenManager)

    @Provides @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)   // auto-refresh JWT on 401
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides @Singleton
    fun provideRetrofit(okHttp: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton fun provideAuthApi(r: Retrofit):          AuthApi          = r.create(AuthApi::class.java)
    @Provides @Singleton fun provideTransactionApi(r: Retrofit):   TransactionApi   = r.create(TransactionApi::class.java)
    @Provides @Singleton fun provideUserApi(r: Retrofit):          UserApi          = r.create(UserApi::class.java)
    @Provides @Singleton fun provideAnalyticsApi(r: Retrofit):     AnalyticsApi     = r.create(AnalyticsApi::class.java)
    @Provides @Singleton fun provideIntelligenceApi(r: Retrofit):  IntelligenceApi  = r.create(IntelligenceApi::class.java)
    @Provides @Singleton fun provideGoalsApi(r: Retrofit):         GoalsApi         = r.create(GoalsApi::class.java)
    @Provides @Singleton fun provideGmailApi(r: Retrofit):         GmailApi         = r.create(GmailApi::class.java)
    @Provides @Singleton fun provideFinancialAdvisorApi(r: Retrofit): FinancialAdvisorApi = r.create(FinancialAdvisorApi::class.java)
    @Provides @Singleton fun provideHealthScoreApi(r: Retrofit):   HealthScoreApi   = r.create(HealthScoreApi::class.java)
    @Provides @Singleton fun provideCashFlowApi(r: Retrofit):      CashFlowApi      = r.create(CashFlowApi::class.java)
    @Provides @Singleton fun provideDebtPayoffApi(r: Retrofit):    DebtPayoffApi    = r.create(DebtPayoffApi::class.java)
    @Provides @Singleton fun provideTaxApi(r: Retrofit):           TaxApi           = r.create(TaxApi::class.java)
    @Provides @Singleton fun provideIouApi(r: Retrofit):           IouApi           = r.create(IouApi::class.java)
}
