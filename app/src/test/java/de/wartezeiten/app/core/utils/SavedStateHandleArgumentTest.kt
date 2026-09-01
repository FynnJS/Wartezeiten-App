package de.wartezeiten.app.core.utils

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedStateHandleArgumentTest {
    @Test
    fun nonStringValueDoesNotThrowAndIsReadAsString() {
        val handle = SavedStateHandle(mapOf("parkKey" to 42, "attractionId" to true))
        assertEquals("42", handle.readStringArgument("parkKey"))
        assertEquals("true", handle.readStringArgument("attractionId"))
    }

    @Test
    fun stringValueIsReturnedAsIs() {
        val handle = SavedStateHandle(mapOf("parkKey" to "europapark"))
        assertEquals("europapark", handle.readStringArgument("parkKey"))
    }

    @Test
    fun missingKeyReturnsNull() {
        val handle = SavedStateHandle()
        assertNull(handle.readStringArgument("missing"))
    }
}
