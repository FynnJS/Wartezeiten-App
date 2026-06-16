package de.wartezeiten.app.ui.waitingtimes

import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.AttractionHistoryPoint
import de.wartezeiten.app.domain.model.AttractionHistorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ParkWaitStatisticsTest {
    @Test
    fun calculatesAverageWaitSeriesFromOpenAttractions() {
        val statistics = AttractionHistoryDay(
            generatedAtMillis = 2_000L,
            parkKey = "europapark",
            date = "2026-06-14",
            openFrom = "2026-06-14T09:00:00+02:00",
            closedFrom = "2026-06-14T18:00:00+02:00",
            snapshots = listOf(
                snapshot(1_000L, point("a", 10, 0, "opened"), point("b", 20, 0, "opened")),
                snapshot(2_000L, point("a", 30, -4, "open"), point("b", -1, -1, "closed")),
            ),
            attractions = emptyList(),
        ).toParkWaitStatistics()

        assertNotNull(statistics)
        assertEquals("2026-06-14T09:00:00+02:00", statistics?.openFrom)
        assertEquals("2026-06-14T18:00:00+02:00", statistics?.closedFrom)
        assertEquals(2, statistics?.points?.size)
        assertEquals(22.5f, statistics?.averageWaitMinutes ?: -1f, 0.001f)
        assertEquals(15f, statistics?.minAverageWaitMinutes ?: -1f, 0.001f)
        assertEquals(30f, statistics?.latestAverageWaitMinutes ?: -1f, 0.001f)
        assertEquals(1, statistics?.latestOpenAttractionCount)
    }

    @Test
    fun filtersFutureSnapshotsForCurrentParkDay() {
        val nowMillis = java.time.OffsetDateTime.parse("2026-06-16T09:30:00+02:00")
            .toInstant()
            .toEpochMilli()
        val statistics = AttractionHistoryDay(
            generatedAtMillis = nowMillis,
            parkKey = "europapark",
            date = "2026-06-16",
            openFrom = "2026-06-16T09:00:00+02:00",
            closedFrom = "2026-06-16T18:00:00+02:00",
            snapshots = listOf(
                snapshot(
                    java.time.OffsetDateTime.parse("2026-06-16T09:15:00+02:00").toInstant().toEpochMilli(),
                    point("a", 10, 0, "opened"),
                ),
                snapshot(
                    java.time.OffsetDateTime.parse("2026-06-16T13:00:00+02:00").toInstant().toEpochMilli(),
                    point("a", 60, 0, "opened"),
                ),
            ),
            attractions = emptyList(),
        ).toParkWaitStatistics(nowMillis = nowMillis)

        assertNotNull(statistics)
        assertEquals(1, statistics?.points?.size)
        assertEquals(10f, statistics?.latestAverageWaitMinutes ?: -1f, 0.001f)
    }

    private fun snapshot(
        capturedAtMillis: Long,
        vararg points: AttractionHistoryPoint,
    ) = AttractionHistorySnapshot(capturedAtMillis, points.toList())

    private fun point(id: String, value: Int, statusCode: Int, status: String) =
        AttractionHistoryPoint(id, id, value, statusCode, status)
}
