package de.wartezeiten.app.ui.parks

import de.wartezeiten.app.core.utils.countryToFlag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CountryFlagTest {
    @Test
    fun usaMapsToUnitedStatesFlag() {
        val flag = countryToFlag("USA")

        assertEquals(2, flag.codePointCount(0, flag.length))
        assertArrayEquals(intArrayOf(0x1F1FA, 0x1F1F8), flag.codePoints().toArray())
    }

    @Test
    fun germanUnitedStatesNameMapsToUnitedStatesFlag() {
        assertArrayEquals(
            intArrayOf(0x1F1FA, 0x1F1F8),
            countryToFlag("Vereinigte Staaten").codePoints().toArray(),
        )
    }
}
