package de.wartezeiten.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PublicLatestAppDataDto(
    @SerializedName("generatedAtMillis")
    val generatedAtMillis: Long?,
    @SerializedName("parks")
    val parks: List<PublicLatestParkSnapshotDto> = emptyList(),
    @SerializedName("recommendations")
    val recommendations: List<PublicParkRecommendationDto> = emptyList(),
)

data class PublicLatestParkSnapshotDto(
    @SerializedName("parkKey")
    val parkKey: String,
    @SerializedName("capturedAtMillis")
    val capturedAtMillis: Long,
    @SerializedName("apiCrowdLevel")
    val apiCrowdLevel: Float?,
    @SerializedName("calculatedCrowdLevel")
    val calculatedCrowdLevel: Float?,
    @SerializedName("displayCrowdLevel")
    val displayCrowdLevel: Float?,
    @SerializedName("openedToday")
    val openedToday: Boolean?,
    @SerializedName("openFrom")
    val openFrom: String?,
    @SerializedName("closedFrom")
    val closedFrom: String?,
    @SerializedName("openAttractions")
    val openAttractions: Int,
    @SerializedName("totalAttractions")
    val totalAttractions: Int,
)

data class PublicParkRecommendationDto(
    @SerializedName("parkKey")
    val parkKey: String,
    @SerializedName("score")
    val score: Int,
    @SerializedName("crowdLevel")
    val crowdLevel: Float?,
    @SerializedName("openAttractions")
    val openAttractions: Int,
    @SerializedName("totalAttractions")
    val totalAttractions: Int,
    @SerializedName("reason")
    val reason: String?,
)

data class PublicTrendHistoryDto(
    @SerializedName("generatedAtMillis")
    val generatedAtMillis: Long?,
    @SerializedName("parks")
    val parks: List<PublicParkTrendHistoryDto> = emptyList(),
)

data class PublicParkTrendHistoryDto(
    @SerializedName("parkKey")
    val parkKey: String,
    @SerializedName("snapshots")
    val snapshots: List<PublicParkTrendSnapshotDto> = emptyList(),
)

data class PublicParkTrendSnapshotDto(
    @SerializedName("capturedAtMillis")
    val capturedAtMillis: Long,
    @SerializedName("apiCrowdLevel")
    val apiCrowdLevel: Float?,
    @SerializedName("calculatedCrowdLevel")
    val calculatedCrowdLevel: Float?,
    @SerializedName("displayCrowdLevel")
    val displayCrowdLevel: Float?,
    @SerializedName("openedToday")
    val openedToday: Boolean?,
    @SerializedName("openAttractions")
    val openAttractions: Int,
    @SerializedName("totalAttractions")
    val totalAttractions: Int,
)
