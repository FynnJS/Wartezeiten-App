package de.wartezeiten.app.domain.model

data class ParkRecommendation(
    val park: Park,
    val score: Int,
    val crowdLevel: Float?,
    val openAttractions: Int,
    val totalAttractions: Int,
    val reason: String,
)
