package de.wartezeiten.app.data.mapper

import de.wartezeiten.app.data.remote.dto.WaitingTimeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WaitingTimeMapperTest {
    @Test
    fun namelessAttractionsGetStablePerRowIds() {
        val first = WaitingTimeDto(id = null, name = null, code = null, waitingTime = 10, status = "opened")
            .toEntity("park", 1L, index = 0)
        val second = WaitingTimeDto(id = null, name = null, code = null, waitingTime = 20, status = "opened")
            .toEntity("park", 1L, index = 1)

        assertNotEquals(first.attractionId, second.attractionId)
        assertNotEquals(first.attractionId, fallbackAttractionId("Unbekannte Attraktion", 2))
    }

    @Test
    fun uuidFirstThenCodeThenIndexFallback() {
        val withId = WaitingTimeDto(id = "uuid-123", name = "A", code = "code-1", waitingTime = null, status = null)
        assertEquals("uuid-123", withId.toEntity("park", 1L).attractionId)

        val withCode = WaitingTimeDto(id = null, name = "A", code = "code-1", waitingTime = null, status = null)
        assertEquals("code-1", withCode.toEntity("park", 1L).attractionId)
    }

    @Test
    fun nullNameFallsBackToPlaceholderName() {
        val entity = WaitingTimeDto(id = "x", name = null, code = null, waitingTime = 5, status = "opened")
            .toEntity("park", 1L)
        assertEquals("Unbekannte Attraktion", entity.name)
        assertEquals("x", entity.attractionId)
    }

    @Test
    fun duplicateIdsAreDeduplicatedInMapperOutput() {
        val entities = listOf(
            WaitingTimeDto(id = "a", name = "A", code = null, waitingTime = 5, status = "opened"),
            WaitingTimeDto(id = "a", name = "A Duplicate", code = null, waitingTime = 9, status = "opened"),
        ).mapIndexed { index, dto -> dto.toEntity("park", 1L, index) }
            .distinctBy { it.attractionId }

        assertEquals(1, entities.size)
    }
}
