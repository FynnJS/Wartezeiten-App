package de.wartezeiten.app.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import de.wartezeiten.app.MainActivity
import de.wartezeiten.app.R
import de.wartezeiten.app.data.local.dao.WatchlistDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CHANNEL_ID = "watchlist_alerts"
private const val CHANNEL_NAME = "Park-Alarme"

@AndroidEntryPoint
class WartezeitenFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushRegistrationManager: PushRegistrationManager
    @Inject lateinit var watchlistDao: WatchlistDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        serviceScope.launch {
            pushRegistrationManager.registerToken(token)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMessageReceived(message: RemoteMessage) {
        createNotificationChannel()
        val data = message.data
        val parkKey = data["parkKey"].orEmpty()
        if (parkKey.isBlank() || !canPostNotifications()) return

        val title = data["title"]
            ?: message.notification?.title
            ?: getString(R.string.app_name)
        val body = data["body"]
            ?: message.notification?.body
            ?: ""
        val attractionId = data["attractionId"]?.takeIf { it.isNotBlank() }
        val localAlertId = data["localAlertId"]?.toIntOrNull()
        val notifyOnce = data["notifyOnce"].toBoolean()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(notificationIntent(parkKey, attractionId))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify((parkKey + (attractionId ?: "") + title).notificationId(), notification)
        if (notifyOnce && localAlertId != null) {
            serviceScope.launch {
                watchlistDao.setEnabled(localAlertId, false)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun notificationIntent(parkKey: String, attractionId: String?): PendingIntent {
        val uriBuilder = Uri.Builder()
            .scheme("wartezeiten")
            .authority("parks")
            .appendPath(parkKey)
        if (!attractionId.isNullOrBlank()) {
            uriBuilder.appendQueryParameter("attractionId", attractionId)
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uriBuilder.build()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            (parkKey + (attractionId ?: "")).notificationId(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Vor-Ort-Hinweise fuer Wartezeiten, Auslastung und Attraktionsstatus"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun String.notificationId(): Int {
        return 60_000 + hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) % 30_000 }
    }
}
