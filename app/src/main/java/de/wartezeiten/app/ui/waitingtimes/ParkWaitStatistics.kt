package de.wartezeiten.app.ui.waitingtimes

import de.wartezeiten.app.domain.model.AttractionHistoryDay

data class ParkWaitStatisticsPoint(
    val capturedAtMillis: Long,
    val averageWaitMinutes: Float,
    val openAttractionCount: Int,
)

data class ParkWaitStatistics(
    val date: String,
    val averageWaitMinutes: Float,
    val minAverageWaitMinutes: Float,
    val maxAverageWaitMinutes: Float,
    val latestAverageWaitMinutes: Float,
    val latestOpenAttractionCount: Int,
    val points: List<ParkWaitStatisticsPoint>,
)

internal fun AttractionHistoryDay.toParkWaitStatistics(): ParkWaitStatistics? {
    val points = snapshots.mapNotNull { snapshot ->
        val waits = snapshot.attractions
            .filter { point ->
                point.value >= 0 && (
                    point.statusCode == 0 ||
                        point.status.equals("opened", ignoreCase = true) ||
                        point.status.equals("open", ignoreCase = true)
                    )
            }
            .map { it.value }
        if (waits.isEmpty()) return@mapNotNull null
        ParkWaitStatisticsPoint(
            capturedAtMillis = snapshot.capturedAtMillis,
            averageWaitMinutes = waits.average().toFloat(),
            openAttractionCount = waits.size,
        )
    }.sortedBy { it.capturedAtMillis }
    if (points.isEmpty()) return null

    val averages = points.map { it.averageWaitMinutes }
    return ParkWaitStatistics(
        date = date,
        averageWaitMinutes = averages.average().toFloat(),
        minAverageWaitMinutes = averages.min(),
        maxAverageWaitMinutes = averages.max(),
        latestAverageWaitMinutes = points.last().averageWaitMinutes,
        latestOpenAttractionCount = points.last().openAttractionCount,
        points = points,
    )
}
