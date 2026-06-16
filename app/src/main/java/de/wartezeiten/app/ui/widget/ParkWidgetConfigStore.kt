package de.wartezeiten.app.ui.widget

import android.content.Context

data class ParkWidgetStoredConfig(
    val parkKey: String,
    val parkName: String,
    val attractionIds: List<String>,
)

internal object ParkWidgetConfigStore {
    fun save(
        context: Context,
        appWidgetId: Int,
        parkKey: String,
        parkName: String,
        attractionIds: List<String>,
    ) {
        preferences(context).edit()
            .putString(key(appWidgetId, "park_key"), parkKey)
            .putString(key(appWidgetId, "park_name"), parkName)
            .putString(key(appWidgetId, "attraction_ids"), ParkWidgetState.encodeAttractionIds(attractionIds))
            .putString(key(LAST_WIDGET_ID, "park_key"), parkKey)
            .putString(key(LAST_WIDGET_ID, "park_name"), parkName)
            .putString(key(LAST_WIDGET_ID, "attraction_ids"), ParkWidgetState.encodeAttractionIds(attractionIds))
            .apply()
    }

    fun read(context: Context, appWidgetId: Int): ParkWidgetStoredConfig? {
        val preferences = preferences(context)
        val parkKey = preferences.getString(key(appWidgetId, "park_key"), null)?.takeIf { it.isNotBlank() }
            ?: return null
        return ParkWidgetStoredConfig(
            parkKey = parkKey,
            parkName = preferences.getString(key(appWidgetId, "park_name"), null).orEmpty(),
            attractionIds = ParkWidgetState.decodeAttractionIds(
                preferences.getString(key(appWidgetId, "attraction_ids"), null),
            ),
        )
    }

    fun readLast(context: Context): ParkWidgetStoredConfig? = read(context, LAST_WIDGET_ID)

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("park_widget_config", Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int, suffix: String) = "widget_${appWidgetId}_$suffix"

    private const val LAST_WIDGET_ID = -1
}
