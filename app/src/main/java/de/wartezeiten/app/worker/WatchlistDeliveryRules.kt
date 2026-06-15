package de.wartezeiten.app.worker

import de.wartezeiten.app.data.local.entity.AlertHistoryEntity
import de.wartezeiten.app.data.local.entity.WatchlistEntity
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

internal fun WatchlistEntity.cooldownElapsed(
    history: AlertHistoryEntity?,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    val lastTriggered = history?.lastTriggeredAtMillis ?: 0L
    return lastTriggered <= 0L || nowMillis - lastTriggered >= cooldownMinutes * 60_000L
}

internal fun dailySummaryDayKey(
    nowMillis: Long,
    openingOffsetSource: String?,
): String? {
    val offset = openingOffsetSource
        ?.let { runCatching { OffsetDateTime.parse(it).offset }.getOrNull() }
        ?: ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(nowMillis))
    val localDateTime = Instant.ofEpochMilli(nowMillis).atOffset(offset)
    return localDateTime.toLocalDate().toString().takeIf { localDateTime.hour == 18 }
}

internal fun WatchlistEntity.isInQuietHours(
    nowMillis: Long,
    openingOffsetSource: String?,
): Boolean {
    if (!quietHoursEnabled) return false
    val offset = openingOffsetSource
        ?.let { runCatching { OffsetDateTime.parse(it).offset }.getOrNull() }
        ?: ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(nowMillis))
    val minutes = Instant.ofEpochMilli(nowMillis).atOffset(offset).toLocalTime().toSecondOfDay() / 60
    return if (quietStartMinutes <= quietEndMinutes) {
        minutes in quietStartMinutes until quietEndMinutes
    } else {
        minutes >= quietStartMinutes || minutes < quietEndMinutes
    }
}
