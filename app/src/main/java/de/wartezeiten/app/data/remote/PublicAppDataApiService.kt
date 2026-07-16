package de.wartezeiten.app.data.remote

import de.wartezeiten.app.data.remote.dto.PublicLatestAppDataDto
import de.wartezeiten.app.data.remote.dto.PublicAttractionHistoryDayDto
import de.wartezeiten.app.data.remote.dto.PublicGlobalMarkersDto
import de.wartezeiten.app.data.remote.dto.PublicStatisticsIndexDto
import de.wartezeiten.app.data.remote.dto.PublicTrendHistoryDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface PublicAppDataApiService {
    @GET("app-data/latest.json")
    suspend fun getLatestAppData(
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<PublicLatestAppDataDto>

    @GET("app-data/global-markers/latest.json")
    suspend fun getGlobalMarkers(
        @Header("Cache-Control") cacheControl: String? = null,
    ): Response<PublicGlobalMarkersDto>

    @GET("app-data/trend-history.json")
    suspend fun getTrendHistory(
        @Query("parkKey") parkKey: String? = null,
    ): Response<PublicTrendHistoryDto>

    @GET("app-data/statistics/index.json")
    suspend fun getStatisticsIndex(): Response<PublicStatisticsIndexDto>

    @GET("app-data/statistics/parks/{parkKey}/days/{date}.json")
    suspend fun getAttractionHistoryDay(
        @Path("parkKey") parkKey: String,
        @Path("date") date: String,
    ): Response<PublicAttractionHistoryDayDto>
}
