package de.wartezeiten.app.data.remote

import de.wartezeiten.app.data.remote.dto.CrowdLevelDto
import de.wartezeiten.app.data.remote.dto.OpeningTimesDto
import de.wartezeiten.app.data.remote.dto.ParkDto
import de.wartezeiten.app.data.remote.dto.WaitingTimeDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface WartezeitenApiService {
    @GET("/v1/parks")
    suspend fun getParks(
        @Header("language") language: String,
    ): Response<List<ParkDto>>

    /**
     * FIX: API returns a JSON array ([{...}]), not a single object.
     * Changed return type from Response<OpeningTimesDto> to Response<List<OpeningTimesDto>>.
     * This was causing: JsonSyntaxException: Expected BEGIN_OBJECT but was BEGIN_ARRAY
     */
    @GET("/v1/openingtimes")
    suspend fun getOpeningTimes(
        @Header("park") park: String,
    ): Response<List<OpeningTimesDto>>

    @GET("/v1/waitingtimes")
    suspend fun getWaitingTimes(
        @Header("park") park: String,
        @Header("language") language: String,
    ): Response<List<WaitingTimeDto>>

    @GET("/v1/crowdlevel")
    suspend fun getCrowdLevel(
        @Header("park") park: String,
    ): Response<CrowdLevelDto>
}
