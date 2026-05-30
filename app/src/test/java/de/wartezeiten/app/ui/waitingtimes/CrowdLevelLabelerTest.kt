package de.wartezeiten.app.ui.waitingtimes

import org.junit.Assert.assertEquals
import org.junit.Test

class CrowdLevelLabelerTest {
    @Test
    fun labelsCrowdLevelOnPercentScale() {
        assertEquals("Niedrig", CrowdLevelLabeler.getStatus(25f).label)
        assertEquals("Mittel", CrowdLevelLabeler.getStatus(45.6f).label)
        assertEquals("Hoch", CrowdLevelLabeler.getStatus(75f).label)
        assertEquals("Sehr hoch", CrowdLevelLabeler.getStatus(90f).label)
    }
}
