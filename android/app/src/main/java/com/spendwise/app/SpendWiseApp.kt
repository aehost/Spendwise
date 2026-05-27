package com.spendwise.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.spendwise.app.data.worker.GmailSyncWorker
import com.spendwise.app.data.worker.IntelligenceWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpendWiseApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleBackgroundWork()
    }

    private fun scheduleBackgroundWork() {
        // Weekly intelligence scan — auto-detects recurring bills from transaction history
        IntelligenceWorker.schedule(this)
        // Daily Gmail sync — parses bank/CC statement emails to auto-add bills
        GmailSyncWorker.schedule(this)
    }
}
