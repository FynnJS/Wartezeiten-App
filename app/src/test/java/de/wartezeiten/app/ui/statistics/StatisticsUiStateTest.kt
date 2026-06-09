package de.wartezeiten.app.ui.statistics

import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.AttractionHistoryPoint
import de.wartezeiten.app.domain.model.AttractionHistorySnapshot
import de.wartezeiten.app.domain.model.StatisticsIndex
import de.wartezeiten.app.domain.model.StatisticsParkIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class StatisticsUiStateTest {
    @Test
    fun availableDatesUsesIndexedDatesWhenTodayHasNoMeasurements() {
        val indexedDate = LocalDate.now().minusDays(1).toString()
        val today = LocalDate.now().toString()
        val state = StatisticsUiState(
            index = StatisticsIndex(
                generatedAtMillis = 1L,
                parks = listOf(
                    parkIndex(
                        dates = listOf(indexedDate),
                        latestDate = indexedDate,
                    ),
                ),
            ),
            selectedParkKey = "europapark",
            selectedDate = indexedDate,
        )

        assertEquals(listOf(indexedDate, today), state.availableDates)
        assertTrue(indexedDate in state.availableDates)
        assertTrue(today in state.availableDates)
    }

    @Test
    fun parkSeriesUsesOpenStatusAliasesForAverageWaits() {
        val state = StatisticsUiState(
            selectedParkKey = "europapark",
            day = AttractionHistoryDay(
                generatedAtMillis = 1L,
                parkKey = "europapark",
                date = "2026-06-05",
                snapshots = listOf(
                    snapshot(
                        capturedAtMillis = 1_000L,
                        point(id = "a", value = 10, statusCode = -4, status = "open"),
                        point(id = "b", value = -1, statusCode = -1, status = "closed"),
                    ),
                    snapshot(
                        capturedAtMillis = 2_000L,
                        point(id = "a", value = 20, statusCode = 0, status = "opened"),
                    ),
                ),
                attractions = emptyList(),
            ),
        )

        assertEquals(2, state.parkSeries.size)
        assertEquals(10f, state.parkSeries.first().averageWaitMinutes, 0.001f)
        assertEquals(20f, state.parkStatistics?.latestAverageWaitMinutes ?: -1f, 0.001f)
    }

    @Test
    fun offsetOpeningTimesRemoveSnapshotsAfterClosing() {
        val state = StatisticsUiState(
            selectedParkKey = "phantasialand",
            selectedAttractionId = "black-mamba",
            day = AttractionHistoryDay(
                generatedAtMillis = 1L,
                parkKey = "phantasialand",
                date = "2026-06-06",
                openFrom = "2026-06-06T09:00:00+02:00",
                closedFrom = "2026-06-06T18:00:00+02:00",
                snapshots = listOf(
                    snapshot(
                        capturedAtMillis = millis("2026-06-06T10:00:00+02:00"),
                        point(id = "black-mamba", value = 20, statusCode = 0, status = "opened"),
                    ),
                    snapshot(
                        capturedAtMillis = millis("2026-06-06T23:30:00+02:00"),
                        point(id = "black-mamba", value = -1, statusCode = -1, status = "closed"),
                    ),
                ),
                attractions = emptyList(),
            ),
        )

        assertEquals(1, state.selectedSeries.size)
        assertEquals(20, state.selectedSeries.single().value)
        assertEquals(1, state.parkSeries.size)
        assertEquals(20f, state.parkStatistics?.averageWaitMinutes ?: -1f, 0.001f)
    }

    private fun parkIndex(
        dates: List<String>,
        latestDate: String?,
    ): StatisticsParkIndex {
        return StatisticsParkIndex(
            parkKey = "europapark",
            dates = dates,
            latestDate = latestDate,
            attractionCount = 0,
            sampleCount = 0,
            updatedAtMillis = 0L,
            attractions = emptyList(),
        )
    }

    private fun snapshot(
        capturedAtMillis: Long,
        vararg points: AttractionHistoryPoint,
    ): AttractionHistorySnapshot {
        return AttractionHistorySnapshot(
            capturedAtMillis = capturedAtMillis,
            attractions = points.toList(),
        )
    }

    private fun point(
        id: String,
        value: Int,
        statusCode: Int,
        status: String,
    ): AttractionHistoryPoint {
        return AttractionHistoryPoint(
            id = id,
            name = id,
            value = value,
            statusCode = statusCode,
            status = status,
        )
    }

    private fun millis(value: String): Long {
        return OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }
}
