package de.wartezeiten.app.domain.model

data class DataQuality(
    val lastUpdated: Long,
    val freshness: DataFreshness,
    val confidenceScore: Float // 0.0f to 1.0f
)

enum class DataFreshness {
    Fresh,
    Stale,
    Expired
}
