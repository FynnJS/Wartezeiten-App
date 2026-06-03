package de.wartezeiten.app.domain.repository

import de.wartezeiten.app.core.network.ApiResult
import de.wartezeiten.app.domain.model.Park
import de.wartezeiten.app.domain.model.ParkDetail
import de.wartezeiten.app.domain.model.ParkRecommendation
import de.wartezeiten.app.domain.model.ParkTrendSummary
import kotlinx.coroutines.flow.Flow

data class ParkRecommendationScanProgress(
    val completedParks: Int,
    val totalParks: Int,
    val estimatedRemainingMillis: Long,
)

interface WartezeitenRepository {
    fun observeParks(query: String?): Flow<List<Park>>
    fun observeLatestOpenParkKeys(): Flow<Set<String>>
    fun observeBestParkRecommendation(): Flow<ParkRecommendation?>
    fun observeParkRecommendations(limit: Int = 5): Flow<List<ParkRecommendation>>
    suspend fun refreshParks(language: String): ApiResult<Unit>
    suspend fun refreshParkRecommendationSnapshots(
        language: String,
        onProgress: (ParkRecommendationScanProgress) -> Unit = {},
    ): ApiResult<Unit>
    suspend fun refreshPublicAppData(): ApiResult<Unit>

    fun observeParkDetail(parkKey: String): Flow<ParkDetail>
    suspend fun refreshParkDetail(parkKey: String, language: String): ApiResult<Unit>
    
    fun getParkTrendSummary(parkKey: String): Flow<ParkTrendSummary>
    suspend fun refreshPublicTrendHistory(parkKey: String): ApiResult<Unit>

    suspend fun toggleFavorite(parkId: String, isFavorite: Boolean)
}
