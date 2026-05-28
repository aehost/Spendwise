package com.spendwise.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.spendwise.app.data.worker.GmailSyncWorker
import com.spendwise.app.data.worker.IntelligenceWorker
import com.spendwise.app.data.worker.WeeklyDigestWorker
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
        GmailSyncWorker.schedule(this)
        WeeklyDigestWorker.schedule(this)
    }
}
