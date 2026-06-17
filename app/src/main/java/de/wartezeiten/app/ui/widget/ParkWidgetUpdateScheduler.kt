package de.wartezeiten.app.ui.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ParkWidgetUpdateScheduler {
    private const val PERIODIC_WORK_NAME = "park_widget_periodic_refresh"
    private const val MANUAL_WORK_NAME = "park_widget_manual_refresh"

    fun ensureBackgroundUpdates(context: Context) {
        val work = PeriodicWorkRequestBuilder<ParkWidgetRefreshWorker>(30, TimeUnit.MINUTES)
            .setConstraints(refreshConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            work,
        )
    }

    fun refreshSoon(context: Context) {
        val work = OneTimeWorkRequestBuilder<ParkWidgetRefreshWorker>()
            .setConstraints(refreshConstraints())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            work,
        )
    }

    fun cancelBackgroundUpdates(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun refreshConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
