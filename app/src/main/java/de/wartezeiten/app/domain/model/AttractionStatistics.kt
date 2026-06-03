package de.wartezeiten.app.domain.model

data class StatisticsIndex(
    val generatedAtMillis: Long,
    val parks: List<StatisticsParkIndex>,
)

data class StatisticsParkIndex(
    val parkKey: String,
    val dates: List<String>,
    val latestDate: String?,
    val attractionCount: Int,
    val sampleCount: Int,
    val updatedAtMillis: Long,
    val attractions: List<StatisticsAttractionIndex>,
)

data class StatisticsAttractionIndex(
    val id: String,
    val name: String,
    val latestDate: String?,
    val sampleCount: Int,
    val averageWaitMinutes: Float?,
    val lastValue: Int?,
    val lastStatusCode: Int?,
)

data class AttractionHistoryDay(
    val generatedAtMillis: Long,
    val parkKey: String,
    val date: String,
    val snapshots: List<AttractionHistorySnapshot>,
    val attractions: List<AttractionHistorySummary>,
)

data class AttractionHistorySnapshot(
    val capturedAtMillis: Long,
    val attractions: List<AttractionHistoryPoint>,
)

data class AttractionHistoryPoint(
    val id: String,
    val name: String,
    val value: Int,
    val statusCode: Int,
    val status: String,
)

data class AttractionHistorySummary(
    val id: String,
    val name: String,
    val sampleCount: Int,
    val openSampleCount: Int,
    val closedSampleCount: Int,
    val averageWaitMinutes: Float?,
    val minWaitMinutes: Int?,
    val maxWaitMinutes: Int?,
    val lastValue: Int?,
    val lastStatusCode: Int?,
)
