package de.wartezeiten.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val UPDATE_PERIODIC_WORK = "update_check_periodic"

object UpdateScheduler {
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensureBackgroundChecks(context: Context) {
        val periodicWork = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UPDATE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }
}
