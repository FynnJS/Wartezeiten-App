package de.wartezeiten.app.push

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import de.wartezeiten.app.BuildConfig
import de.wartezeiten.app.data.local.PreferencesDataSource
import de.wartezeiten.app.data.local.dao.WatchlistDao
import de.wartezeiten.app.data.remote.PushApiService
import de.wartezeiten.app.data.remote.PushRegisterRequest
import de.wartezeiten.app.data.remote.PushWatchlistAlertRequest
import de.wartezeiten.app.data.remote.PushWatchlistSyncRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRegistrationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: PreferencesDataSource,
    private val watchlistDao: WatchlistDao,
    private val pushApi: PushApiService,
) {
    private val logger = Logger.getLogger("PushRegistration")

    val isPushConfigured: Boolean
        get() = BuildConfig.FIREBASE_APPLICATION_ID.isNotBlank() &&
                BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
                BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
                BuildConfig.FIREBASE_GCM_SENDER_ID.isNotBlank()

    suspend fun syncCurrentWatchlist() {
        if (!ensureFirebaseInitialized()) return
        runCatching {
            val token = withContext(Dispatchers.IO) {
                Tasks.await(FirebaseMessaging.getInstance().token)
            }
            registerToken(token)
            syncWatchlistOnly()
        }.onFailure { error ->
            logger.log(Level.WARNING, "Push watchlist sync failed", error)
        }
    }

    suspend fun registerToken(token: String) {
        if (!ensureFirebaseInitialized()) return
        runCatching {
            val installationId = preferences.getOrCreatePushInstallationId()
            val language = preferences.language.first()
            val response = pushApi.registerInstallation(
                PushRegisterRequest(
                    installationId = installationId,
                    token = token,
                    language = language,
                )
            )
            if (!response.isSuccessful) {
                logger.warning("Push registration failed with HTTP ${response.code()}")
            }
        }.onFailure { error ->
            logger.log(Level.WARNING, "Push token registration failed", error)
        }
    }

    private suspend fun syncWatchlistOnly() {
        val installationId = preferences.getOrCreatePushInstallationId()
        val alerts = watchlistDao.observeWatchlist().first().map { alert ->
            PushWatchlistAlertRequest(
                localAlertId = alert.id.toString(),
                parkKey = alert.parkKey,
                attractionId = alert.attractionId,
                type = alert.type.name,
                threshold = alert.threshold,
            )
        }
        val response = pushApi.syncWatchlist(
            PushWatchlistSyncRequest(
                installationId = installationId,
                alerts = alerts,
            )
        )
        if (!response.isSuccessful) {
            logger.warning("Push watchlist sync failed with HTTP ${response.code()}")
        }
    }

    private fun ensureFirebaseInitialized(): Boolean {
        if (!isPushConfigured) return false
        if (FirebaseApp.getApps(context).isNotEmpty()) return true

        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_GCM_SENDER_ID)
            .build()
        FirebaseApp.initializeApp(context, options)
        return true
    }
}
