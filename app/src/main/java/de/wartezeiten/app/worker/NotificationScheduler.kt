package de.wartezeiten.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val WATCHLIST_ONE_TIME_WORK = "watchlist_notifications_check"
private const val WATCHLIST_PERIODIC_WORK = "watchlist_notifications_periodic"

object NotificationScheduler {
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensureBackgroundChecks(context: Context) {
        val periodicWork = PeriodicWorkRequestBuilder<NotificationWorker>(
            repeatInterval = 30,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        )
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WATCHLIST_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork,
        )
    }

    fun runSoonAndKeepChecking(context: Context) {
        val immediateWork = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WATCHLIST_ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            immediateWork,
        )
        ensureBackgroundChecks(context)
    }

    fun cancelBackgroundChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WATCHLIST_ONE_TIME_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(WATCHLIST_PERIODIC_WORK)
    }
}
