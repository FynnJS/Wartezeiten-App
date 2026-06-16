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
    @SerializedName("attractions")
    val attractions: List<PublicLatestAttractionDto> = emptyList(),
)

data class PublicLatestAttractionDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
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
    val markers: List<PublicGlobalMarkerDto> = emptyList(),
)

data class PublicGlobalMarkerDto(
    @SerializedName("parkKey")
    val parkKey: String,
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
    val parks: List<PublicStatisticsParkIndexDto> = emptyList(),
)

data class PublicStatisticsParkIndexDto(
    @SerializedName("parkKey")
    val parkKey: String,
    @SerializedName("dates")
    val dates: List<String> = emptyList(),
    @SerializedName("latestDate")
    val latestDate: String?,
    @SerializedName("attractionCount")
    val attractionCount: Int?,
    @SerializedName("sampleCount")
    val sampleCount: Int?,
    @SerializedName("updatedAtMillis")
    val updatedAtMillis: Long?,
    @SerializedName("attractions")
    val attractions: List<PublicStatisticsAttractionIndexDto> = emptyList(),
)

data class PublicStatisticsAttractionIndexDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
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
    val parkKey: String,
    @SerializedName("date")
    val date: String,
    @SerializedName("openFrom")
    val openFrom: String?,
    @SerializedName("closedFrom")
    val closedFrom: String?,
    @SerializedName("snapshots")
    val snapshots: List<PublicAttractionHistorySnapshotDto> = emptyList(),
    @SerializedName("attractions")
    val attractions: List<PublicAttractionHistorySummaryDto> = emptyList(),
)

data class PublicAttractionHistorySnapshotDto(
    @SerializedName("capturedAtMillis")
    val capturedAtMillis: Long,
    @SerializedName("attractions")
    val attractions: List<PublicAttractionHistoryPointDto> = emptyList(),
)

data class PublicAttractionHistoryPointDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("value")
    val value: Int,
    @SerializedName("statusCode")
    val statusCode: Int,
    @SerializedName("status")
    val status: String?,
)

data class PublicAttractionHistorySummaryDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
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
        parks = parks.map { it.toDomain() },
    )
}

fun PublicStatisticsParkIndexDto.toDomain(): StatisticsParkIndex {
    return StatisticsParkIndex(
        parkKey = parkKey,
        dates = dates.sorted(),
        latestDate = latestDate,
        attractionCount = attractionCount ?: 0,
        sampleCount = sampleCount ?: 0,
        updatedAtMillis = updatedAtMillis ?: 0L,
        attractions = attractions.map { it.toDomain() },
    )
}

fun PublicStatisticsAttractionIndexDto.toDomain(): StatisticsAttractionIndex {
    return StatisticsAttractionIndex(
        id = id,
        name = name,
        latestDate = latestDate,
        sampleCount = sampleCount ?: 0,
        averageWaitMinutes = averageWaitMinutes,
        lastValue = lastValue,
        lastStatusCode = lastStatusCode,
    )
}

fun PublicAttractionHistoryDayDto.toDomain(): AttractionHistoryDay {
    return AttractionHistoryDay(
        generatedAtMillis = generatedAtMillis ?: 0L,
        parkKey = parkKey,
        date = date,
        openFrom = openFrom,
        closedFrom = closedFrom,
        snapshots = snapshots.map { it.toDomain() },
        attractions = attractions.map { it.toDomain() },
    )
}

fun PublicAttractionHistorySnapshotDto.toDomain(): AttractionHistorySnapshot {
    return AttractionHistorySnapshot(
        capturedAtMillis = capturedAtMillis,
        attractions = attractions.map { it.toDomain() },
    )
}

fun PublicAttractionHistoryPointDto.toDomain(): AttractionHistoryPoint {
    return AttractionHistoryPoint(
        id = id,
        name = name,
        value = value,
        statusCode = statusCode,
        status = status ?: "unknown",
    )
}

fun PublicAttractionHistorySummaryDto.toDomain(): AttractionHistorySummary {
    return AttractionHistorySummary(
        id = id,
        name = name,
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
