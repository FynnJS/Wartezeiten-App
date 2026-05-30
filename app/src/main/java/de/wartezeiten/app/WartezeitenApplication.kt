package de.wartezeiten.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.wartezeiten.app.worker.NotificationScheduler
import de.wartezeiten.app.worker.UpdateScheduler
import javax.inject.Inject

@HiltAndroidApp
class WartezeitenApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationScheduler.ensureBackgroundChecks(this)
        UpdateScheduler.ensureBackgroundChecks(this)
    }
}
