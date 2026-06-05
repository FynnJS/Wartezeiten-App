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

class StatisticsUiStateTest {
    @Test
    fun availableDatesUsesIndexedDatesWhenTodayHasNoMeasurements() {
        val state = StatisticsUiState(
            index = StatisticsIndex(
                generatedAtMillis = 1L,
                parks = listOf(
                    parkIndex(
                        dates = listOf("2026-06-04"),
                        latestDate = "2026-06-04",
                    ),
                ),
            ),
            selectedParkKey = "europapark",
            selectedDate = "2026-06-04",
        )

        assertEquals(listOf("2026-06-04"), state.availableDates)
        assertTrue("2026-06-04" in state.availableDates)
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
}
