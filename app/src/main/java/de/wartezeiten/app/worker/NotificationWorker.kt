package de.wartezeiten.app.worker

import android.Manifest
import android.app.PendingIntent
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
import de.wartezeiten.app.data.local.dao.AlertHistoryDao
import de.wartezeiten.app.data.local.dao.ParkDao
import de.wartezeiten.app.data.local.dao.WatchlistDao
import de.wartezeiten.app.data.local.entity.AlertHistoryEntity
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import de.wartezeiten.app.data.local.entity.WatchlistType
import de.wartezeiten.app.data.remote.WartezeitenApiService
import de.wartezeiten.app.data.remote.dto.WaitingTimeDto
import de.wartezeiten.app.MainActivity
import kotlinx.coroutines.flow.first
import java.util.Locale

private const val CHANNEL_ID = "watchlist_alerts"
private const val CHANNEL_NAME = "Wartezeiten Erinnerungen"
private const val SUMMARY_NOTIFICATION_ID = 1234
private const val GROUP_KEY = "de.wartezeiten.app.WATCHLIST_ALERTS"

private data class WatchlistNotification(
    val title: String,
    val content: String,
    val parkKey: String,
    val parkName: String,
)

@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val watchlistDao: WatchlistDao,
    private val alertHistoryDao: AlertHistoryDao,
    private val parkDao: ParkDao,
    private val api: WartezeitenApiService,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        createNotificationChannel()

        val watchlistItems = watchlistDao.observeWatchlist().first()
        if (watchlistItems.isEmpty()) {
            NotificationScheduler.cancelBackgroundChecks(applicationContext)
            return Result.success()
        }

        val groupedByPark = watchlistItems.groupBy { it.parkKey }
        val parkNames = parkDao.observeParks(null).first()
            .flatMap { park -> listOf(park.id to park.name, park.uuid to park.name) }
            .toMap()
        val notifications = mutableListOf<WatchlistNotification>()

        groupedByPark.forEach { (parkKey, alerts) ->
            val parkName = parkNames[parkKey] ?: parkKey
            val openingTimes = safeApiCall { api.getOpeningTimes(parkKey) }
            val waitingTimes = safeApiCall { api.getWaitingTimes(parkKey, "de") }
            val crowdLevel = safeApiCall { api.getCrowdLevel(parkKey) }
            val opening = openingTimes?.firstOrNull()
            val isParkOpen = opening?.openedToday == true
            val canUseLiveAttractionAlerts = opening?.openedToday != false &&
                    waitingTimes?.any { it.status.normalizedStatus() == "opened" } == true

            collectParkNotifications(parkKey, parkName, isParkOpen, alerts, notifications)

            val crowdValue = crowdLevel?.crowdLevel?.replace(",", ".")?.toFloatOrNull()
            collectCrowdNotifications(parkKey, parkName, canUseLiveAttractionAlerts, crowdValue, alerts, notifications)

            if (canUseLiveAttractionAlerts && waitingTimes != null) {
                collectWaitBelowNotifications(parkKey, parkName, waitingTimes, alerts, notifications)
                collectWaitAboveNotifications(parkKey, parkName, waitingTimes, alerts, notifications)
                collectStatusNotifications(parkKey, parkName, waitingTimes, alerts, notifications)
            }
        }

        if (notifications.isNotEmpty()) {
            sendNotifications(notifications)
        }

        return Result.success()
    }

    private suspend fun collectParkNotifications(
        parkKey: String,
        parkName: String,
        isParkOpen: Boolean,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.NOW_OPENED }.forEach { alert ->
            if (shouldNotifyBoolean(alert.id, isParkOpen)) {
                notifications.add(
                    WatchlistNotification(
                        title = "$parkName ist heute geöffnet",
                        content = "Tippe, um die aktuellen Wartezeiten zu sehen.",
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }

        alerts.filter { it.type == WatchlistType.PARK_STATUS_CHANGED }.forEach { alert ->
            val status = if (isParkOpen) "open" else "closed"
            if (shouldNotifyValue(alert.id, status, notifyOnFirstMatch = false)) {
                notifications.add(
                    WatchlistNotification(
                        title = "$parkName: Status geändert",
                        content = if (isParkOpen) {
                            "Heute geöffnet."
                        } else {
                            "Heute geschlossen."
                        },
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }
    }

    private suspend fun collectCrowdNotifications(
        parkKey: String,
        parkName: String,
        isParkOpen: Boolean,
        crowdValue: Float?,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.CROWD_LEVEL_BELOW }.forEach { alert ->
            val triggered = isParkOpen && crowdValue != null && crowdValue <= alert.threshold
            if (shouldNotifyBoolean(alert.id, triggered)) {
                notifications.add(
                    WatchlistNotification(
                        title = "$parkName: Auslastung niedrig",
                        content = "Auslastung bei ${crowdValue?.formatPercent() ?: "?"}% (Limit: max. ${alert.threshold}%).",
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }

        alerts.filter { it.type == WatchlistType.CROWD_LEVEL_ABOVE }.forEach { alert ->
            val triggered = isParkOpen && crowdValue != null && crowdValue >= alert.threshold
            if (shouldNotifyBoolean(alert.id, triggered)) {
                notifications.add(
                    WatchlistNotification(
                        title = "$parkName: Auslastung hoch",
                        content = "Auslastung bei ${crowdValue?.formatPercent() ?: "?"}% (Limit: min. ${alert.threshold}%).",
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }
    }

    private suspend fun collectWaitBelowNotifications(
        parkKey: String,
        parkName: String,
        waitingTimes: List<WaitingTimeDto>,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.WAIT_TIME_BELOW }.forEach { alert ->
            val target = alert.attractionId?.let { id ->
                waitingTimes.firstOrNull { normalizeAttractionId(it) == id }
            }
            val best = target?.takeIf { it.isOpenWithWaitTime() }
                ?: waitingTimes
                    .filter { it.isOpenWithWaitTime() }
                    .minByOrNull { it.waitingTime ?: Int.MAX_VALUE }
            val triggered = best?.waitingTime != null && best.waitingTime <= alert.threshold

            if (shouldNotifyBoolean(alert.id, triggered)) {
                notifications.add(
                    WatchlistNotification(
                        title = if (target != null) "${target.name} ist schneller" else "Kurze Wartezeit in $parkName",
                        content = "${best?.name ?: "Eine Attraktion"}: ${best?.waitingTime ?: 0} Min. (Limit: max. ${alert.threshold} Min.).",
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }
    }

    private suspend fun collectWaitAboveNotifications(
        parkKey: String,
        parkName: String,
        waitingTimes: List<WaitingTimeDto>,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts.filter { it.type == WatchlistType.WAIT_TIME_ABOVE }.forEach { alert ->
            val target = alert.attractionId?.let { id ->
                waitingTimes.firstOrNull { normalizeAttractionId(it) == id }
            }
            val longest = target?.takeIf { it.isOpenWithWaitTime() }
                ?: waitingTimes
                    .filter { it.isOpenWithWaitTime() }
                    .maxByOrNull { it.waitingTime ?: Int.MIN_VALUE }
            val triggered = longest?.waitingTime != null && longest.waitingTime >= alert.threshold

            if (shouldNotifyBoolean(alert.id, triggered)) {
                notifications.add(
                    WatchlistNotification(
                        title = if (target != null) "${target.name} ist länger" else "Lange Wartezeit in $parkName",
                        content = "${longest?.name ?: "Eine Attraktion"}: ${longest?.waitingTime ?: 0} Min. (Limit: min. ${alert.threshold} Min.).",
                        parkKey = parkKey,
                        parkName = parkName,
                    )
                )
            }
        }
    }

    private suspend fun collectStatusNotifications(
        parkKey: String,
        parkName: String,
        waitingTimes: List<WaitingTimeDto>,
        alerts: List<WatchlistEntity>,
        notifications: MutableList<WatchlistNotification>,
    ) {
        alerts
            .filter {
                it.type == WatchlistType.ATTRACTION_OPEN ||
                        it.type == WatchlistType.ATTRACTION_CLOSED ||
                        it.type == WatchlistType.ATTRACTION_MAINTENANCE ||
                        it.type == WatchlistType.ATTRACTION_STATUS_CHANGE
            }
            .forEach { alert ->
                val current = alert.attractionId?.let { id ->
                    waitingTimes.firstOrNull { normalizeAttractionId(it) == id }
                } ?: return@forEach

                val shouldSend = when (alert.type) {
                    WatchlistType.ATTRACTION_OPEN -> shouldNotifyBoolean(alert.id, current.status.normalizedStatus() == "opened")
                    WatchlistType.ATTRACTION_CLOSED -> shouldNotifyBoolean(alert.id, current.status.normalizedStatus() == "closed")
                    WatchlistType.ATTRACTION_MAINTENANCE -> shouldNotifyBoolean(alert.id, current.status.normalizedStatus() == "maintenance")
                    WatchlistType.ATTRACTION_STATUS_CHANGE -> shouldNotifyValue(
                        alert.id,
                        current.status.normalizedStatus(),
                        notifyOnFirstMatch = false,
                    )
                    else -> false
                }

                if (shouldSend) {
                    notifications.add(
                        WatchlistNotification(
                            title = current.name,
                            content = current.status.toReadableStatus(),
                            parkKey = parkKey,
                            parkName = parkName,
                        )
                    )
                }
            }
    }

    private fun notificationIntent(parkKey: String): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.Builder()
                .scheme("wartezeiten")
                .authority("parks")
                .appendPath(parkKey)
                .build()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            applicationContext,
            parkKey.notificationId(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigungen für Park- und Attraktionsalarme"
        }
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun sendNotifications(notifications: List<WatchlistNotification>) {
        if (!canPostNotifications()) return

        val notificationManager = NotificationManagerCompat.from(applicationContext)
        val groupedByPark = notifications.groupBy { it.parkKey }

        groupedByPark.forEach { (parkKey, parkNotifications) ->
            val first = parkNotifications.first()
            val title = if (parkNotifications.size == 1) {
                first.title
            } else {
                "${parkNotifications.size} Hinweise für ${first.parkName}"
            }
            val content = if (parkNotifications.size == 1) {
                first.content
            } else {
                parkNotifications.joinToString(separator = "\n") { "${it.title}: ${it.content}" }
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(de.wartezeiten.app.R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(notificationIntent(parkKey))
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setGroup(GROUP_KEY)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(parkKey.notificationId(), notification)
        }

        val summaryText = if (notifications.size == 1) {
            notifications.first().content
        } else {
            notifications.joinToString(separator = "\n") { "${it.title}: ${it.content}" }
        }
        val summary = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(de.wartezeiten.app.R.mipmap.ic_launcher)
            .setContentTitle("${notifications.size} neue Wartezeiten-Hinweise")
            .setContentText(groupedByPark.size.toParkCountText())
            .setContentIntent(notificationIntent(notifications.first().parkKey))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    private fun normalizeAttractionId(dto: WaitingTimeDto): String {
        return dto.id ?: dto.name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-")
    }

    private fun String.notificationId(): Int {
        return 10_000 + hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) % 50_000 }
    }

    private suspend fun <T> safeApiCall(call: suspend () -> retrofit2.Response<T>): T? {
        return try {
            val response = call()
            if (response.isSuccessful) response.body() else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun shouldNotifyBoolean(alertId: Int, triggered: Boolean): Boolean {
        val lastValue = alertHistoryDao.getHistory(alertId)?.lastNotifiedValue
        val currentValue = triggered.toString()
        if (lastValue != currentValue) {
            alertHistoryDao.upsertHistory(AlertHistoryEntity(alertId, currentValue, System.currentTimeMillis()))
        }
        return triggered && lastValue != currentValue
    }

    private suspend fun shouldNotifyValue(
        alertId: Int,
        currentValue: String,
        notifyOnFirstMatch: Boolean,
    ): Boolean {
        val lastValue = alertHistoryDao.getHistory(alertId)?.lastNotifiedValue
        if (lastValue != currentValue) {
            alertHistoryDao.upsertHistory(AlertHistoryEntity(alertId, currentValue, System.currentTimeMillis()))
        }
        return (lastValue != null && lastValue != currentValue) || (lastValue == null && notifyOnFirstMatch)
    }

    private fun WaitingTimeDto.isOpenWithWaitTime(): Boolean {
        return status.normalizedStatus() == "opened" && waitingTime != null && waitingTime >= 0
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun Float.formatPercent(): String {
        return if (this % 1f == 0f) {
            toInt().toString()
        } else {
            String.format(Locale.GERMAN, "%.1f", this)
        }
    }

    private fun String.toReadableStatus(): String {
        return when (normalizedStatus()) {
            "opened" -> "Jetzt geöffnet."
            "closed" -> "Jetzt geschlossen."
            "maintenance" -> "Die Attraktion ist aktuell in Wartung."
            else -> "Status geändert: $this"
        }
    }

    private fun String.normalizedStatus(): String = trim().lowercase(Locale.ROOT)

    private fun Int.toParkCountText(): String {
        return if (this == 1) {
            "1 Park mit neuen Hinweisen"
        } else {
            "$this Parks mit neuen Hinweisen"
        }
    }
}
