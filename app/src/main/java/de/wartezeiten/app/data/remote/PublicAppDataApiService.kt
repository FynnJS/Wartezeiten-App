package de.wartezeiten.app.data.remote

import de.wartezeiten.app.data.remote.dto.PublicLatestAppDataDto
import de.wartezeiten.app.data.remote.dto.PublicTrendHistoryDto
import retrofit2.Response
import retrofit2.http.GET

interface PublicAppDataApiService {
    @GET("app-data/latest.json")
    suspend fun getLatestAppData(): Response<PublicLatestAppDataDto>

    @GET("app-data/trend-history.json")
    suspend fun getTrendHistory(): Response<PublicTrendHistoryDto>
}
