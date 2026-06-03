package de.wartezeiten.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val UPDATE_ONE_TIME_WORK = "update_check_once"
private const val UPDATE_PERIODIC_WORK = "update_check_periodic"

object UpdateScheduler {
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensureBackgroundChecks(context: Context) {
        val immediateWork = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UPDATE_ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            immediateWork,
        )

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
