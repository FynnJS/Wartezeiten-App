package de.wartezeiten.app.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Ausweichquelle fuer Live-Wartezeiten, wenn die wartezeiten.app-API fuer /v1/waitingtimes
 * ausfaellt. Siehe [de.wartezeiten.app.data.remote.fallback.QueueTimesParkMapping] fuer die
 * Zuordnung von wartezeiten-Park-Slugs zu queue-times.com-Park-IDs.
 */
interface QueueTimesApiService {
    @GET("parks/{parkId}/queue_times.json")
    suspend fun getQueueTimes(@Path("parkId") parkId: Int): Response<QueueTimesResponseDto>
}

data class QueueTimesResponseDto(
    val lands: List<QueueTimesLandDto> = emptyList(),
    val rides: List<QueueTimesRideDto> = emptyList(),
)

data class QueueTimesLandDto(
    val rides: List<QueueTimesRideDto> = emptyList(),
)

data class QueueTimesRideDto(
    val id: Long,
    val name: String,
    val is_open: Boolean,
    val wait_time: Int?,
)
