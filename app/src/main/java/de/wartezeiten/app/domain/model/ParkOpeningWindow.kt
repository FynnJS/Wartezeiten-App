package de.wartezeiten.app.domain.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ParkOpeningWindow(
    val opensAt: ZonedDateTime?,
    val closesAt: ZonedDateTime?,
    val openTime: LocalTime?,
    val closeTime: LocalTime?,
)

fun parkOpeningWindow(
    openFrom: String?,
    closedFrom: String?,
    zoneId: ZoneId,
): ParkOpeningWindow {
    val opensAt = openFrom?.toZonedDateTimeOrNull(zoneId)
    val rawClosesAt = closedFrom?.toZonedDateTimeOrNull(zoneId)
    val normalizedClosesAt = normalizeClosingDate(opensAt, rawClosesAt)
    return ParkOpeningWindow(
        opensAt = opensAt,
        closesAt = normalizedClosesAt,
        openTime = opensAt?.toLocalTime() ?: openFrom?.parseTimeOrNull(),
        closeTime = normalizedClosesAt?.toLocalTime() ?: closedFrom?.parseTimeOrNull(),
    )
}

fun isParkCurrentlyOpen(
    openedToday: Boolean?,
    openFrom: String?,
    closedFrom: String?,
    now: Instant = Instant.now(),
): Boolean {
    if (openedToday != true) return false
    val window = parkOpeningWindow(openFrom, closedFrom, ZoneId.systemDefault())
    val opensAt = window.opensAt?.toInstant()
    val closesAt = window.closesAt?.toInstant()
    if (opensAt != null && now.isBefore(opensAt)) return false
    if (closesAt != null && !now.isBefore(closesAt)) return false
    return true
}

/**
 * Erkennt einen wahrscheinlichen Datenausfall der Wartezeiten-Quelle: Der Park gilt laut
 * Öffnungszeiten als heute geöffnet und die Öffnung liegt mindestens [graceMinutes] zurück
 * (und der Park hat noch nicht geschlossen), aber keine Attraktion meldet aktuell "geöffnet".
 */
fun isParkOpenWithoutWaitingTimeData(
    openedToday: Boolean?,
    openFrom: String?,
    closedFrom: String?,
    hasOpenAttraction: Boolean,
    now: Instant = Instant.now(),
    graceMinutes: Long = 15,
): Boolean {
    if (openedToday != true || hasOpenAttraction) return false
    val window = parkOpeningWindow(openFrom, closedFrom, ZoneId.systemDefault())
    val opensAt = window.opensAt?.toInstant() ?: return false
    if (now.isBefore(opensAt.plusSeconds(graceMinutes * 60))) return false
    val closesAt = window.closesAt?.toInstant()
    if (closesAt != null && !now.isBefore(closesAt)) return false
    return true
}

private fun normalizeClosingDate(
    opensAt: ZonedDateTime?,
    closesAt: ZonedDateTime?,
): ZonedDateTime? {
    if (opensAt == null || closesAt == null || !closesAt.isBefore(opensAt)) return closesAt

    val openTime = opensAt.toLocalTime()
    val closeTime = closesAt.toLocalTime()
    val closeDate = if (closeTime.isAfter(openTime)) {
        opensAt.toLocalDate()
    } else {
        opensAt.toLocalDate().plusDays(1)
    }
    return ZonedDateTime.of(closeDate, closeTime, opensAt.zone)
}

private fun String.toZonedDateTimeOrNull(zoneId: ZoneId): ZonedDateTime? {
    return runCatching { OffsetDateTime.parse(this).atZoneSameInstant(zoneId) }
        .getOrElse {
            runCatching { LocalDateTime.parse(this).atZone(zoneId) }
                .getOrNull()
        }
}

private fun String.parseTimeOrNull(): LocalTime? {
    val rawTime = timePattern.find(substringAfter('T', this))?.value ?: return null
    return runCatching { LocalTime.parse(rawTime) }.getOrNull()
}

private val timePattern = Regex("""\d{2}:\d{2}(:\d{2})?""")
