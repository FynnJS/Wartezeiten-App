package de.wartezeiten.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.wartezeiten.app.push.PushRegistrationManager
import de.wartezeiten.app.ui.widget.ParkWidgetUpdateScheduler
import de.wartezeiten.app.worker.NotificationScheduler
import de.wartezeiten.app.worker.RecommendationScanScheduler
import de.wartezeiten.app.worker.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class WartezeitenApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var pushRegistrationManager: PushRegistrationManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationScheduler.ensureBackgroundChecks(this)
        RecommendationScanScheduler.ensureBackgroundScans(this)
        UpdateScheduler.ensureBackgroundChecks(this)
        ParkWidgetUpdateScheduler.ensureBackgroundUpdates(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            pushRegistrationManager.syncCurrentWatchlist()
        }
    }
}
