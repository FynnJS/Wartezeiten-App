package de.wartezeiten.app.data.mapper

import de.wartezeiten.app.data.remote.dto.ParkDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParkMapperTest {
    @Test
    fun missingUuidFallsBackToParkId() {
        val entity = ParkDto(
            id = "phantasialand",
            uuid = null,
            name = "Phantasialand",
            country = "Deutschland",
        ).toEntity(updatedAtMillis = 123L)!!

        assertEquals("phantasialand", entity.id)
        assertEquals("phantasialand", entity.uuid)
    }

    @Test
    fun blankUuidFallsBackToParkId() {
        val entity = ParkDto(
            id = "europapark",
            uuid = "   ",
            name = "Europa-Park",
            country = "Deutschland",
        ).toEntity(updatedAtMillis = 123L)!!

        assertEquals("europapark", entity.uuid)
    }

    @Test
    fun missingIdOrNameSkipsEntry() {
        assertNull(ParkDto(id = null, uuid = null, name = "Ohne ID", country = "DE").toEntity(updatedAtMillis = 1L))
        assertNull(ParkDto(id = "ohne-name", uuid = null, name = null, country = "DE").toEntity(updatedAtMillis = 1L))
    }
}
