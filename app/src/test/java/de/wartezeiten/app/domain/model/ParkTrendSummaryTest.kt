package de.wartezeiten.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ParkTrendSummaryTest {
    @Test
    fun latestClosedSnapshotSuppressesCrowdSummary() {
        val summary = buildParkTrendSummary(
            snapshots = listOf(
                snapshot(capturedAtMillis = 1_000L, displayCrowdLevel = 45f, openedToday = true),
                snapshot(capturedAtMillis = 2_000L, displayCrowdLevel = 24f, openedToday = false),
            ),
            nowMillis = 2_500L,
        )

        assertEquals(ParkTrendSummary.Empty, summary)
    }

    @Test
    fun closedSnapshotsAreExcludedFromCrowdStatistics() {
        val summary = buildParkTrendSummary(
            snapshots = listOf(
                snapshot(capturedAtMillis = 1_000L, displayCrowdLevel = 80f, openedToday = false),
                snapshot(capturedAtMillis = 2_000L, displayCrowdLevel = 30f, openedToday = true),
                snapshot(capturedAtMillis = 3_000L, displayCrowdLevel = 50f, openedToday = true),
            ),
            nowMillis = 3_500L,
        )

        assertEquals(50f, summary.displayCrowdLevel)
        assertEquals(30f, summary.minCrowdLevel)
        assertEquals(50f, summary.maxCrowdLevel)
    }

    @Test
    fun nullLatestCrowdLevelSuppressesCrowdSummary() {
        val summary = buildParkTrendSummary(
            snapshots = listOf(
                snapshot(capturedAtMillis = 1_000L, displayCrowdLevel = 40f, openedToday = true),
                snapshot(capturedAtMillis = 2_000L, displayCrowdLevel = null, openedToday = true),
            ),
            nowMillis = 2_500L,
        )

        assertNull(summary.displayCrowdLevel)
        assertEquals(ParkTrendSummary.Empty, summary)
    }

    @Test
    fun trendSummaryOnlyUsesLatestCalendarDay() {
        val yesterday = Instant.parse("2026-06-03T12:00:00Z").toEpochMilli()
        val todayMorning = Instant.parse("2026-06-04T09:00:00Z").toEpochMilli()
        val todayNoon = Instant.parse("2026-06-04T12:00:00Z").toEpochMilli()

        val summary = buildParkTrendSummary(
            snapshots = listOf(
                snapshot(capturedAtMillis = yesterday, displayCrowdLevel = 90f, openedToday = true),
                snapshot(capturedAtMillis = todayMorning, displayCrowdLevel = 30f, openedToday = true),
                snapshot(capturedAtMillis = todayNoon, displayCrowdLevel = 50f, openedToday = true),
            ),
            nowMillis = todayNoon,
        )

        assertEquals(2, summary.sampleCount)
        assertEquals(30f, summary.minCrowdLevel)
        assertEquals(50f, summary.maxCrowdLevel)
    }

    @Test
    fun trendSummaryUsesParkOpeningWindowWhenAvailable() {
        val beforeOpening = Instant.parse("2026-06-04T07:30:00Z").toEpochMilli()
        val duringOpening = Instant.parse("2026-06-04T08:30:00Z").toEpochMilli()
        val afterClosing = Instant.parse("2026-06-04T19:30:00Z").toEpochMilli()

        val summary = buildParkTrendSummary(
            snapshots = listOf(
                snapshot(
                    capturedAtMillis = beforeOpening,
                    displayCrowdLevel = 10f,
                    openedToday = true,
                    openFrom = "2026-06-04T08:00:00Z",
                    closedFrom = "2026-06-04T19:00:00Z",
                ),
                snapshot(
                    capturedAtMillis = duringOpening,
                    displayCrowdLevel = 40f,
                    openedToday = true,
                    openFrom = "2026-06-04T08:00:00Z",
                    closedFrom = "2026-06-04T19:00:00Z",
                ),
                snapshot(
                    capturedAtMillis = afterClosing,
                    displayCrowdLevel = 80f,
                    openedToday = true,
                    openFrom = "2026-06-04T08:00:00Z",
                    closedFrom = "2026-06-04T19:00:00Z",
                ),
            ),
            nowMillis = duringOpening,
        )

        assertEquals(1, summary.sampleCount)
        assertEquals(40f, summary.displayCrowdLevel)
    }

    private fun snapshot(
        capturedAtMillis: Long,
        displayCrowdLevel: Float?,
        openedToday: Boolean?,
        openFrom: String? = null,
        closedFrom: String? = null,
    ): ParkCrowdSnapshot {
        return ParkCrowdSnapshot(
            capturedAtMillis = capturedAtMillis,
            apiCrowdLevel = displayCrowdLevel,
            calculatedCrowdLevel = null,
            displayCrowdLevel = displayCrowdLevel,
            openedToday = openedToday,
            openFrom = openFrom,
            closedFrom = closedFrom,
            openAttractions = 4,
            totalAttractions = 10,
        )
    }
}
