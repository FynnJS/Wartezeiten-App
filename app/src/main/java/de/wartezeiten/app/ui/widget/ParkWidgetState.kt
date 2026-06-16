package de.wartezeiten.app.ui.widget

import androidx.datastore.preferences.core.stringPreferencesKey

internal object ParkWidgetState {
    val PARK_KEY = stringPreferencesKey("park_key")
    val PARK_NAME = stringPreferencesKey("park_name")
    val ATTRACTION_IDS = stringPreferencesKey("attraction_ids")

    fun encodeAttractionIds(ids: Collection<String>): String {
        return ids.filter { it.isNotBlank() }.distinct().take(3).joinToString(SEPARATOR)
    }

    fun decodeAttractionIds(value: String?): List<String> {
        return value
            ?.split(SEPARATOR)
            .orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
    }

    private const val SEPARATOR = "\u001F"
}
