package de.wartezeiten.app.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.wartezeiten.app.R

object NotificationDiagnostics {
    private const val channelId = "watchlist_alerts"
    private const val testNotificationId = 59_001

    fun showTestNotification(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Park-Alarme",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Wartezeiten, Auslastung und Attraktionsstatus"
                },
            )
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Testbenachrichtigung")
            .setContentText("Android-Benachrichtigungen funktionieren auf diesem Gerät.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Android-Benachrichtigungen funktionieren auf diesem Gerät. Standby-Push benötigt zusätzlich eine aktive Firebase-Verbindung."),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(testNotificationId, notification)
        return true
    }
}
