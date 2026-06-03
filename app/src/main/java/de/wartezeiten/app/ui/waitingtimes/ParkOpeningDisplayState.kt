package de.wartezeiten.app.ui.waitingtimes

import de.wartezeiten.app.domain.model.CrowdLevel
import de.wartezeiten.app.domain.model.AttractionStatus
import de.wartezeiten.app.domain.model.OpeningTimes
import de.wartezeiten.app.domain.model.WaitingTime
import de.wartezeiten.app.domain.model.estimateCrowdLevel
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

internal enum class ParkOpeningTone {
    Open,
    OpenOtherTimeToday,
    ClosedToday,
    Unknown,
}

internal data class ParkOpeningDisplayState(
    val tone: ParkOpeningTone,
    val statusText: String,
    val crowdText: String? = null,
)

internal fun parkOpeningDisplayState(
    openingTimes: OpeningTimes?,
    crowdLevel: CrowdLevel?,
    waitingTimes: List<WaitingTime> = emptyList(),
    currentTimeMillis: Long,
    localTimeOffsetSeconds: Int?,
): ParkOpeningDisplayState {
    if (openingTimes == null) {
        return ParkOpeningDisplayState(
            tone = ParkOpeningTone.Unknown,
            statusText = "\u00d6ffnungszeiten nicht verf\u00fcgbar",
        )
    }

    if (!openingTimes.opened) {
        return ParkOpeningDisplayState(
            tone = ParkOpeningTone.ClosedToday,
            statusText = "Heute geschlossen",
        )
    }

    val zoneId = localTimeOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(currentTimeMillis).atZone(zoneId)
    val openAt = openingTimes.from?.let { parseDateTime(it, zoneId) }
    val closeAt = openingTimes.to?.let { parseDateTime(it, zoneId) }
    val openTime = openAt?.toLocalTime() ?: openingTimes.from?.let(::parseTime)
    val closeTime = closeAt?.toLocalTime() ?: openingTimes.to?.let(::parseTime)

    val isCurrentlyOpen = when {
        openAt != null && closeAt != null -> !now.isBefore(openAt) && now.isBefore(closeAt)
        openTime != null && closeTime != null -> isWithinOpeningHours(
            currentTime = now.toLocalTime(),
            openTime = openTime,
            closeTime = closeTime,
        )
        else -> true
    }
    val hasOpenAttraction = waitingTimes.any { it.status == AttractionStatus.Opened }
    val canShowCrowdLevel = isCurrentlyOpen || hasOpenAttraction

    return if (canShowCrowdLevel) {
        val estimate = estimateCrowdLevel(
            waitingTimes = waitingTimes,
            apiCrowdLevel = crowdLevel?.level,
        )
        ParkOpeningDisplayState(
            tone = if (isCurrentlyOpen) ParkOpeningTone.Open else ParkOpeningTone.OpenOtherTimeToday,
            statusText = buildOpeningWindowStatusText(openTime, closeTime),
            crowdText = buildCrowdText(level = estimate.level),
        )
    } else {
        ParkOpeningDisplayState(
            tone = ParkOpeningTone.OpenOtherTimeToday,
            statusText = buildOpeningWindowStatusText(openTime, closeTime),
        )
    }
}

private fun parseDateTime(value: String, zoneId: ZoneId): ZonedDateTime? {
    runCatching { OffsetDateTime.parse(value).atZoneSameInstant(zoneId) }
        .onSuccess { return it }

    runCatching { LocalDateTime.parse(value).atZone(zoneId) }
        .onSuccess { return it }

    logger.log(Level.WARNING, "Could not parse opening date-time value: {0}", value)
    return null
}

private fun parseTime(value: String): LocalTime? {
    val rawTime = timePattern.find(value.substringAfter('T', value))?.value
    if (rawTime == null) {
        logger.log(Level.WARNING, "Could not find opening time value in: {0}", value)
        return null
    }

    return runCatching { LocalTime.parse(rawTime) }
        .onFailure {
            logger.log(Level.WARNING, "Could not parse opening time value: $rawTime", it)
        }
        .getOrNull()
}

private fun isWithinOpeningHours(
    currentTime: LocalTime,
    openTime: LocalTime,
    closeTime: LocalTime,
): Boolean {
    return if (closeTime.isAfter(openTime)) {
        !currentTime.isBefore(openTime) && currentTime.isBefore(closeTime)
    } else {
        !currentTime.isBefore(openTime) || currentTime.isBefore(closeTime)
    }
}

private fun buildOpeningWindowStatusText(openTime: LocalTime?, closeTime: LocalTime?): String {
    return when {
        openTime != null && closeTime != null ->
            "Heute ge\u00f6ffnet von ${openTime.format(displayTimeFormatter)} Uhr bis ${closeTime.format(displayTimeFormatter)} Uhr"
        openTime != null -> "Heute ge\u00f6ffnet ab: ${openTime.format(displayTimeFormatter)} Uhr"
        closeTime != null -> "Heute ge\u00f6ffnet bis: ${closeTime.format(displayTimeFormatter)} Uhr"
        else -> "Heute ge\u00f6ffnet"
    }
}

private fun buildCrowdText(level: Float?): String? {
    if (level == null) return null
    val formattedLevel = String.format(Locale.GERMAN, "%.0f%%", level)
    val description = when {
        level < 30 -> "Wenig los"
        level < 60 -> "Normal"
        level < 80 -> "Voll"
        else -> "Sehr voll"
    }
    return "Auslastung: ca. $formattedLevel ($description)"
}

private val displayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val timePattern = Regex("""\d{2}:\d{2}(:\d{2})?""")
private val logger: Logger = Logger.getLogger("ParkOpeningDisplayState")
