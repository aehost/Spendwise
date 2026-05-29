package com.spendwise.app.di

import android.content.Context
import com.spendwise.app.data.local.preferences.CategoryCorrectionStore
import com.spendwise.app.data.local.preferences.TokenManager
import com.spendwise.app.domain.usecase.ParseSmsUseCase
import com.spendwise.app.sms.SmsScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun provideTokenManager(@ApplicationContext ctx: Context) = TokenManager(ctx)
    @Provides @Singleton fun provideParseSmsUseCase() = ParseSmsUseCase()
    @Provides @Singleton fun provideSmsScanner(@ApplicationContext ctx: Context) = SmsScanner(ctx)
    @Provides @Singleton fun provideCategoryCorrectionStore(@ApplicationContext ctx: Context) = CategoryCorrectionStore(ctx)
}
