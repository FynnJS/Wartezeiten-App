package de.wartezeiten.app.domain.model

import kotlin.math.abs

data class ParkCrowdSnapshot(
    val capturedAtMillis: Long,
    val apiCrowdLevel: Float?,
    val calculatedCrowdLevel: Float?,
    val displayCrowdLevel: Float?,
    val openedToday: Boolean?,
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
    if (latest.openedToday == false || latest.displayCrowdLevel == null) {
        return ParkTrendSummary.Empty
    }

    val points = sortedSnapshots
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
    val latestAgeMillis = nowMillis - latest.capturedAtMillis

    return ParkTrendSummary(
        latestSnapshotAtMillis = latest.capturedAtMillis,
        isFresh = latestAgeMillis in 0..freshAfterMillis,
        apiCrowdLevel = latest.apiCrowdLevel,
        calculatedCrowdLevel = latest.calculatedCrowdLevel,
        displayCrowdLevel = latest.displayCrowdLevel,
        sampleCount = values.size,
        minCrowdLevel = values.firstOrNull(),
        medianCrowdLevel = values.percentile(0.5f),
        maxCrowdLevel = values.lastOrNull(),
        percentile75CrowdLevel = values.percentile(0.75f),
        percentile90CrowdLevel = values.percentile(0.9f),
        volatility = values.meanAbsoluteChange(),
        points = points.takeLast(48),
        hasPublicHistory = false,
    )
}

private fun List<Float>.percentile(percentile: Float): Float? {
    if (isEmpty()) return null
    val index = ((size - 1) * percentile).toInt().coerceIn(0, lastIndex)
    return this[index]
}

private fun List<Float>.meanAbsoluteChange(): Float? {
    if (size < 2) return null
    return zipWithNext { previous, current -> abs(current - previous) }.average().toFloat()
}
