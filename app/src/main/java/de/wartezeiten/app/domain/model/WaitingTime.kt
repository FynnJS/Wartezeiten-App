package de.wartezeiten.app.domain.model

data class WaitingTime(
    val attractionId: String,
    val name: String,
    val waitingTime: Int?,
    val status: AttractionStatus
)

data class CurrentAttractionSearchEntry(
    val parkKey: String,
    val attractionId: String,
    val name: String,
    val waitingTime: Int?,
    val status: AttractionStatus,
    val updatedAtMillis: Long,
)

enum class AttractionStatus {
    Opened,
    Maintenance,
    Closed,
    ClosedWeather,
    Unknown;

    companion object {
        fun fromApi(value: String): AttractionStatus {
            return when (value.lowercase()) {
                "opened", "open" -> Opened
                "maintenance" -> Maintenance
                "closed" -> Closed
                "closedweather", "closed_weather", "weather" -> ClosedWeather
                else -> Unknown
            }
        }
    }
}
