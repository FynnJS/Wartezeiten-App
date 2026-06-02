package de.wartezeiten.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

    private fun snapshot(
        capturedAtMillis: Long,
        displayCrowdLevel: Float?,
        openedToday: Boolean?,
    ): ParkCrowdSnapshot {
        return ParkCrowdSnapshot(
            capturedAtMillis = capturedAtMillis,
            apiCrowdLevel = displayCrowdLevel,
            calculatedCrowdLevel = null,
            displayCrowdLevel = displayCrowdLevel,
            openedToday = openedToday,
            openAttractions = 4,
            totalAttractions = 10,
        )
    }
}
