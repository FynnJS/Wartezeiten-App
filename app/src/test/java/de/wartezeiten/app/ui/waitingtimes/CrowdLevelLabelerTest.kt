package de.wartezeiten.app.ui.waitingtimes

import org.junit.Assert.assertEquals
import org.junit.Test

class CrowdLevelLabelerTest {
    @Test
    fun labelsCrowdLevelOnPercentScale() {
        assertEquals("Niedrig", CrowdLevelLabeler.getStatus(25f, "de").label)
        assertEquals("Mittel", CrowdLevelLabeler.getStatus(45.6f, "de").label)
        assertEquals("Hoch", CrowdLevelLabeler.getStatus(75f, "de").label)
        assertEquals("Sehr hoch", CrowdLevelLabeler.getStatus(90f, "de").label)
    }

    @Test
    fun labelsAreLocalized() {
        assertEquals("Unknown", CrowdLevelLabeler.getStatus(null, "en").label)
        assertEquals("Low", CrowdLevelLabeler.getStatus(25f, "en").label)
        assertEquals("Medium", CrowdLevelLabeler.getStatus(45.6f, "en").label)
        assertEquals("High", CrowdLevelLabeler.getStatus(75f, "en").label)
        assertEquals("Very high", CrowdLevelLabeler.getStatus(90f, "en").label)

        assertEquals("Faible", CrowdLevelLabeler.getStatus(25f, "fr").label)
        assertEquals("Moyen", CrowdLevelLabeler.getStatus(45.6f, "fr").label)
        assertEquals("Élevé", CrowdLevelLabeler.getStatus(75f, "fr").label)
        assertEquals("Très élevé", CrowdLevelLabeler.getStatus(90f, "fr").label)

        assertEquals("Laag", CrowdLevelLabeler.getStatus(25f, "nl").label)
        assertEquals("Gemiddeld", CrowdLevelLabeler.getStatus(45.6f, "nl").label)
        assertEquals("Hoog", CrowdLevelLabeler.getStatus(75f, "nl").label)
        assertEquals("Zeer hoog", CrowdLevelLabeler.getStatus(90f, "nl").label)
    }
}
