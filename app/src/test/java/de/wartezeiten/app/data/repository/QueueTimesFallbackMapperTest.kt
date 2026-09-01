package de.wartezeiten.app.data.repository

import de.wartezeiten.app.data.remote.QueueTimesLandDto
import de.wartezeiten.app.data.remote.QueueTimesResponseDto
import de.wartezeiten.app.data.remote.QueueTimesRideDto
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueTimesFallbackMapperTest {
    @Test
    fun nullRidesAndLandsDegradeToEmptyList() {
        val response = QueueTimesResponseDto(lands = null, rides = null)
        assertEquals(0, response.toWaitingTimeDtos().size)
    }

    @Test
    fun ridesAndLandsAreMappedWithStatus() {
        val response = QueueTimesResponseDto(
            rides = listOf(QueueTimesRideDto(id = 1, name = "Achterbahn", is_open = true, wait_time = 20)),
            lands = listOf(
                QueueTimesLandDto(
                    rides = listOf(QueueTimesRideDto(id = 2, name = "Wasserbahn", is_open = false, wait_time = null)),
                ),
            ),
        )
        val dtos = response.toWaitingTimeDtos()
        assertEquals(2, dtos.size)
        assertEquals("1", dtos[0].id)
        assertEquals("opened", dtos[0].status)
        assertEquals(20, dtos[0].waitingTime)
        assertEquals("2", dtos[1].id)
        assertEquals("closed", dtos[1].status)
    }

    @Test
    fun entriesWithoutIdOrNameAreSkipped() {
        val response = QueueTimesResponseDto(
            rides = listOf(
                QueueTimesRideDto(id = null, name = "Ohne ID", is_open = true, wait_time = 5),
                QueueTimesRideDto(id = 3, name = null, is_open = true, wait_time = 5),
            ),
            lands = null,
        )
        assertEquals(0, response.toWaitingTimeDtos().size)
    }
}
