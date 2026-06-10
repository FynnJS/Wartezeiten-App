package de.wartezeiten.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface PushApiService {
    @POST("push/register")
    suspend fun registerInstallation(@Body request: PushRegisterRequest): Response<Unit>

    @POST("push/watchlist")
    suspend fun syncWatchlist(@Body request: PushWatchlistSyncRequest): Response<Unit>

    @POST("push/unregister")
    suspend fun unregisterInstallation(@Body request: PushUnregisterRequest): Response<Unit>
}

data class PushRegisterRequest(
    val installationId: String,
    val token: String,
    val language: String,
)

data class PushWatchlistSyncRequest(
    val installationId: String,
    val alerts: List<PushWatchlistAlertRequest>,
)

data class PushWatchlistAlertRequest(
    val localAlertId: String,
    val parkKey: String,
    val attractionId: String?,
    val type: String,
    val threshold: Int,
)

data class PushUnregisterRequest(
    val installationId: String,
)
