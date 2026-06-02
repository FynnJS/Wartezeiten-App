package de.wartezeiten.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val RECOMMENDATION_PERIODIC_WORK = "park_recommendation_scan_periodic"

object RecommendationScanScheduler {
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun ensureBackgroundScans(context: Context) {
        val periodicWork = PeriodicWorkRequestBuilder<RecommendationScanWorker>(
            repeatInterval = 6,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(networkConstraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECOMMENDATION_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork,
        )
    }
}
