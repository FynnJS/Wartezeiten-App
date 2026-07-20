package de.wartezeiten.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.abs

data class ParkCrowdSnapshot(
    val capturedAtMillis: Long,
    val apiCrowdLevel: Float?,
    val calculatedCrowdLevel: Float?,
    val displayCrowdLevel: Float?,
    val openedToday: Boolean?,
    val openFrom: String? = null,
    val closedFrom: String? = null,
    val openAttractions: Int,
    val totalAttractions: Int,
)

data class ParkTrendPoint(
    val capturedAtMillis: Long,
    val crowdLevel: Float,
    val source: ParkTrendSource,
)

enum class ParkTrendSource {
    Local,
    PublicHistory,
}

data class ParkTrendSummary(
    val latestSnapshotAtMillis: Long?,
    val isFresh: Boolean,
    val apiCrowdLevel: Float?,
    val calculatedCrowdLevel: Float?,
    val displayCrowdLevel: Float?,
    val sampleCount: Int,
    val minCrowdLevel: Float?,
    val medianCrowdLevel: Float?,
    val maxCrowdLevel: Float?,
    val percentile75CrowdLevel: Float?,
    val percentile90CrowdLevel: Float?,
    val volatility: Float?,
    val points: List<ParkTrendPoint>,
    val hasPublicHistory: Boolean,
) {
    companion object {
        val Empty = ParkTrendSummary(
            latestSnapshotAtMillis = null,
            isFresh = false,
            apiCrowdLevel = null,
            calculatedCrowdLevel = null,
            displayCrowdLevel = null,
            sampleCount = 0,
            minCrowdLevel = null,
            medianCrowdLevel = null,
            maxCrowdLevel = null,
            percentile75CrowdLevel = null,
            percentile90CrowdLevel = null,
            volatility = null,
            points = emptyList(),
            hasPublicHistory = false,
        )
    }
}

fun buildParkTrendSummary(
    snapshots: List<ParkCrowdSnapshot>,
    nowMillis: Long,
    freshAfterMillis: Long = 10 * 60 * 1000L,
): ParkTrendSummary {
    val sortedSnapshots = snapshots.sortedBy { it.capturedAtMillis }
    val latest = sortedSnapshots.lastOrNull() ?: return ParkTrendSummary.Empty
    
    val zoneId = latest.openFrom?.toOffsetZoneId() ?: latest.closedFrom?.toOffsetZoneId() ?: ZoneId.systemDefault()
    val latestDate = Instant.ofEpochMilli(latest.capturedAtMillis).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()

    if (latestDate < today && isParkCurrentlyOpen(latest.openedToday, latest.openFrom, latest.closedFrom, Instant.ofEpochMilli(nowMillis))) {
        return ParkTrendSummary.Empty
    }

    if (latest.openedToday == false || latest.displayCrowdLevel == null) {
        return ParkTrendSummary.Empty
    }
    val trendWindow = latest.trendWindow()
    val snapshotsInWindow = sortedSnapshots.filter { snapshot ->
        snapshot.capturedAtMillis in trendWindow.first until trendWindow.second
    }
    val latestInWindow = snapshotsInWindow.lastOrNull() ?: latest

    val points = snapshotsInWindow
        .filter { it.openedToday != false }
        .mapNotNull { snapshot ->
            snapshot.displayCrowdLevel?.let { level ->
                ParkTrendPoint(
                    capturedAtMillis = snapshot.capturedAtMillis,
                    crowdLevel = level.coerceIn(0f, 100f),
                    source = ParkTrendSource.Local,
                )
            }
        }
    val values = points.map { it.crowdLevel }.sorted()
    val latestAgeMillis = nowMillis - latestInWindow.capturedAtMillis

    return ParkTrendSummary(
        latestSnapshotAtMillis = latestInWindow.capturedAtMillis,
        isFresh = latestAgeMillis in 0..freshAfterMillis,
        apiCrowdLevel = latestInWindow.apiCrowdLevel,
        calculatedCrowdLevel = latestInWindow.calculatedCrowdLevel,
        displayCrowdLevel = latestInWindow.displayCrowdLevel,
        sampleCount = values.size,
        minCrowdLevel = values.firstOrNull(),
        medianCrowdLevel = values.percentile(0.5f),
        maxCrowdLevel = values.lastOrNull(),
        percentile75CrowdLevel = values.percentile(0.75f),
        percentile90CrowdLevel = values.percentile(0.9f),
        volatility = values.meanAbsoluteChange(),
        points = points,
        hasPublicHistory = false,
    )
}

private fun ParkCrowdSnapshot.trendWindow(): Pair<Long, Long> {
    val openedAt = openFrom?.toInstantOrNull()
    val closedAt = closedFrom?.toInstantOrNull()
    if (openedAt != null && closedAt != null && closedAt.isAfter(openedAt)) {
        val maxEnd = openedAt.plusMillis(MAX_TREND_WINDOW_MILLIS)
        return openedAt.toEpochMilli() to minOf(closedAt.toEpochMilli(), maxEnd.toEpochMilli())
    }

    val zoneId = openFrom?.toOffsetZoneId()
        ?: closedFrom?.toOffsetZoneId()
        ?: ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(capturedAtMillis).atZone(zoneId).toLocalDate()
    return day.dayWindowMillis(zoneId)
}

private fun LocalDate.dayWindowMillis(zoneId: ZoneId): Pair<Long, Long> {
    val start = atStartOfDay(zoneId).toInstant()
    return start.toEpochMilli() to start.plusMillis(MAX_TREND_WINDOW_MILLIS).toEpochMilli()
}

private fun String.toInstantOrNull(): Instant? {
    return runCatching { OffsetDateTime.parse(this).toInstant() }
        .getOrElse { runCatching { Instant.parse(this) }.getOrNull() }
}

private fun String.toOffsetZoneId(): ZoneId? {
    return runCatching { OffsetDateTime.parse(this).offset }.getOrNull()
}

private const val MAX_TREND_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

private fun List<Float>.percentile(percentile: Float): Float? {
    if (isEmpty()) return null
    val index = ((size - 1) * percentile).toInt().coerceIn(0, lastIndex)
    return this[index]
}

private fun List<Float>.meanAbsoluteChange(): Float? {
    if (size < 2) return null
    return zipWithNext { previous, current -> abs(current - previous) }.average().toFloat()
}
