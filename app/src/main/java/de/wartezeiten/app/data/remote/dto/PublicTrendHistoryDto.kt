package de.wartezeiten.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import de.wartezeiten.app.domain.model.AttractionHistoryDay
import de.wartezeiten.app.domain.model.AttractionHistoryPoint
import de.wartezeiten.app.domain.model.AttractionHistorySnapshot
import de.wartezeiten.app.domain.model.AttractionHistorySummary
import de.wartezeiten.app.domain.model.StatisticsIndex
import de.wartezeiten.app.domain.model.StatisticsAttractionIndex
import de.wartezeiten.app.domain.model.StatisticsParkIndex

data class PublicLatestAppDataDto(
    @SerializedName("generatedAtMillis")
    val generatedAtMillis: Long?,
    @SerializedName("parks")
    val parks: List<PublicLatestParkSnapshotDto>? = null,
    @SerializedName("recommendations")
    val recommendations: List<PublicParkRecommendationDto>? = null,
)

data class PublicLatestParkSnapshotDto(
    @SerializedName("parkKey")
    val parkKey: String?,
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
    @SerializedName("attractions")
    val attractions: List<PublicLatestAttractionDto>? = null,
)

data class PublicLatestAttractionDto(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("value")
    val value: Int?,
    @SerializedName("statusCode")
    val statusCode: Int?,
    @SerializedName("status")
    val status: String?,
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

data class PublicGlobalMarkersDto(
    @SerializedName("generatedAtMillis")
    val generatedAtMillis: Long?,
    @SerializedName("date")
    val date: String?,
    @SerializedName("markers")
    val markers: List<PublicGlobalMarkerDto>? = null,
)

data class PublicGlobalMarkerDto(
    @SerializedName("parkKey")
    val parkKey: String?,
    @SerializedName("capturedAtMillis")
    val capturedAtMillis: Long,
    @SerializedName("openedToday")
    val openedToday: Boolean?,
    @SerializedName("openFrom")
    val openFrom: String?,
    @SerializedName("closedFrom")
    val closedFrom: String?,
    @SerializedName("openAttractions")
    val openAttractions: Int?,
    @SerializedName("totalAttractions")
    val totalAttractions: Int?,
    @SerializedName("attractionCount")
    val attractionCount: Int?,
)

data class PublicTrendHistoryDto(
    @SerializedName("generatedAtMillis")
    val generatedAtMillis: Long?,
    @SerializedName("parks")
    val parks: List<PublicParkTrendHistoryDto>? = null,
)

data class PublicParkTrendHistoryDto(
    @SerializedName("parkKey")
    val parkKey: String?,
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
    @SerializedName("openFrom")
    val openFrom: String?,
    @SerializedName("closedFrom")
    val closedFrom: String?,
    @SerializedName("openAttractions")
    val openAttractions: Int,
    @SerializedName("totalAttractions")
    val totalAttractions: Int,
)

data class PublicStatisticsIndexDto(
    @SerializedName("generatedAtMillis")
    val generatedAtMillis: Long?,
    @SerializedName("parks")
    val parks: List<PublicStatisticsParkIndexDto>? = null,
)

data class PublicStatisticsParkIndexDto(
    @SerializedName("parkKey")
    val parkKey: String?,
    @SerializedName("dates")
    val dates: List<String>? = null,
    @SerializedName("latestDate")
    val latestDate: String?,
    @SerializedName("attractionCount")
    val attractionCount: Int?,
    @SerializedName("sampleCount")
    val sampleCount: Int?,
    @SerializedName("updatedAtMillis")
    val updatedAtMillis: Long?,
    @SerializedName("attractions")
    val attractions: List<PublicStatisticsAttractionIndexDto>? = null,
)

data class PublicStatisticsAttractionIndexDto(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("latestDate")
    val latestDate: String?,
    @SerializedName("sampleCount")
    val sampleCount: Int?,
    @SerializedName("averageWaitMinutes")
    val averageWaitMinutes: Float?,
    @SerializedName("lastValue")
    val lastValue: Int?,
    @SerializedName("lastStatusCode")
    val lastStatusCode: Int?,
)

data class PublicAttractionHistoryDayDto(
    @SerializedName("generatedAtMillis")
    val generatedAtMillis: Long?,
    @SerializedName("parkKey")
    val parkKey: String?,
    @SerializedName("date")
    val date: String?,
    @SerializedName("openFrom")
    val openFrom: String?,
    @SerializedName("closedFrom")
    val closedFrom: String?,
    @SerializedName("snapshots")
    val snapshots: List<PublicAttractionHistorySnapshotDto>? = null,
    @SerializedName("attractions")
    val attractions: List<PublicAttractionHistorySummaryDto>? = null,
)

data class PublicAttractionHistorySnapshotDto(
    @SerializedName("capturedAtMillis")
    val capturedAtMillis: Long,
    @SerializedName("attractions")
    val attractions: List<PublicAttractionHistoryPointDto>? = null,
)

data class PublicAttractionHistoryPointDto(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("value")
    val value: Int,
    @SerializedName("statusCode")
    val statusCode: Int,
    @SerializedName("status")
    val status: String?,
)

data class PublicAttractionHistorySummaryDto(
    @SerializedName("id")
    val id: String?,
    @SerializedName("name")
    val name: String?,
    @SerializedName("sampleCount")
    val sampleCount: Int?,
    @SerializedName("openSampleCount")
    val openSampleCount: Int?,
    @SerializedName("closedSampleCount")
    val closedSampleCount: Int?,
    @SerializedName("averageWaitMinutes")
    val averageWaitMinutes: Float?,
    @SerializedName("minWaitMinutes")
    val minWaitMinutes: Int?,
    @SerializedName("maxWaitMinutes")
    val maxWaitMinutes: Int?,
    @SerializedName("lastValue")
    val lastValue: Int?,
    @SerializedName("lastStatusCode")
    val lastStatusCode: Int?,
)

fun PublicStatisticsIndexDto.toDomain(): StatisticsIndex {
    return StatisticsIndex(
        generatedAtMillis = generatedAtMillis ?: 0L,
        parks = parks.orEmpty().mapNotNull { it.toDomain() },
    )
}

fun PublicStatisticsParkIndexDto.toDomain(): StatisticsParkIndex? {
    val parkKey = parkKey ?: return null
    return StatisticsParkIndex(
        parkKey = parkKey,
        dates = dates.orEmpty().sorted(),
        latestDate = latestDate,
        attractionCount = attractionCount ?: 0,
        sampleCount = sampleCount ?: 0,
        updatedAtMillis = updatedAtMillis ?: 0L,
        attractions = attractions.orEmpty().mapNotNull { it.toDomain() },
    )
}

fun PublicStatisticsAttractionIndexDto.toDomain(): StatisticsAttractionIndex? {
    val id = id ?: return null
    return StatisticsAttractionIndex(
        id = id,
        name = name ?: id,
        latestDate = latestDate,
        sampleCount = sampleCount ?: 0,
        averageWaitMinutes = averageWaitMinutes,
        lastValue = lastValue,
        lastStatusCode = lastStatusCode,
    )
}

fun PublicAttractionHistoryDayDto.toDomain(): AttractionHistoryDay? {
    val parkKey = parkKey ?: return null
    val date = date ?: return null
    return AttractionHistoryDay(
        generatedAtMillis = generatedAtMillis ?: 0L,
        parkKey = parkKey,
        date = date,
        openFrom = openFrom,
        closedFrom = closedFrom,
        snapshots = snapshots.orEmpty().mapNotNull { it.toDomain() },
        attractions = attractions.orEmpty().mapNotNull { it.toDomain() },
    )
}

fun PublicAttractionHistorySnapshotDto.toDomain(): AttractionHistorySnapshot? {
    val points = attractions.orEmpty().mapNotNull { it.toDomain() }
    if (points.isEmpty()) return null
    return AttractionHistorySnapshot(
        capturedAtMillis = capturedAtMillis,
        attractions = points,
    )
}

fun PublicAttractionHistoryPointDto.toDomain(): AttractionHistoryPoint? {
    val id = id ?: return null
    return AttractionHistoryPoint(
        id = id,
        name = name ?: id,
        value = value,
        statusCode = statusCode,
        status = status ?: "unknown",
    )
}

fun PublicAttractionHistorySummaryDto.toDomain(): AttractionHistorySummary? {
    val id = id ?: return null
    return AttractionHistorySummary(
        id = id,
        name = name ?: id,
        sampleCount = sampleCount ?: 0,
        openSampleCount = openSampleCount ?: 0,
        closedSampleCount = closedSampleCount ?: 0,
        averageWaitMinutes = averageWaitMinutes,
        minWaitMinutes = minWaitMinutes,
        maxWaitMinutes = maxWaitMinutes,
        lastValue = lastValue,
        lastStatusCode = lastStatusCode,
    )
}
