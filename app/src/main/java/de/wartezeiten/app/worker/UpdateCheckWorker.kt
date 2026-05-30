package de.wartezeiten.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.wartezeiten.app.BuildConfig
import de.wartezeiten.app.data.remote.UpdateApiService
import de.wartezeiten.app.data.remote.dto.AppUpdateInfo
import retrofit2.Response
import java.io.IOException

private const val CHANNEL_ID = "app_update_channel"
private const val CHANNEL_NAME = "App-Update"
private const val NOTIFICATION_ID = 2
private const val SHARED_PREFS_NAME = "update_check_prefs"
private const val PREF_LAST_NOTIFIED_VERSION = "last_notified_version_code"

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updateApiService: UpdateApiService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val response = safeApiCall { updateApiService.fetchRelease() }
        if (response == null || !response.isSuccessful) {
            return Result.success()
        }

        val releaseInfo = response.body() ?: return Result.success()
        val currentVersionCode = BuildConfig.VERSION_CODE
        if (releaseInfo.versionCode <= currentVersionCode) {
            return Result.success()
        }

        if (shouldNotifyForVersion(releaseInfo.versionCode)) {
            showUpdateNotification(releaseInfo)
        }
        return Result.success()
    }

    private fun shouldNotifyForVersion(versionCode: Int): Boolean {
        val preferences = applicationContext.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotifiedVersion = preferences.getInt(PREF_LAST_NOTIFIED_VERSION, -1)
        return if (versionCode != lastNotifiedVersion) {
            preferences.edit().putInt(PREF_LAST_NOTIFIED_VERSION, versionCode).apply()
            true
        } else {
            false
        }
    }

    private fun showUpdateNotification(releaseInfo: AppUpdateInfo) {
        createNotificationChannel()

        val intent = Intent(applicationContext, Class.forName("de.wartezeiten.app.MainActivity")).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(de.wartezeiten.app.R.mipmap.ic_launcher)
            .setContentTitle("Neues App-Update verfügbar")
            .setContentText("Version ${releaseInfo.versionName} steht zum Download bereit.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(releaseInfo.releaseDate))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (canPostNotifications()) {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigt über verfügbare APK-Updates."
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun <T> safeApiCall(call: suspend () -> Response<T>): Response<T>? {
        return try {
            call()
        } catch (exception: IOException) {
            null
        } catch (exception: Exception) {
            null
        }
    }
}
