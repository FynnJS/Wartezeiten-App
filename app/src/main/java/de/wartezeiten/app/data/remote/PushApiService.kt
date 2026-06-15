package de.wartezeiten.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PushApiService {
    @GET("push/status")
    suspend fun getStatus(): Response<PushStatusResponse>

    @POST("push/register")
    suspend fun registerInstallation(@Body request: PushRegisterRequest): Response<Unit>

    @POST("push/watchlist")
    suspend fun syncWatchlist(@Body request: PushWatchlistSyncRequest): Response<Unit>

    @POST("push/unregister")
    suspend fun unregisterInstallation(@Body request: PushUnregisterRequest): Response<Unit>
}

data class PushStatusResponse(
    val ok: Boolean,
    val d1Configured: Boolean,
    val fcmConfigured: Boolean,
    val pushReady: Boolean,
)

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
    val notifyOnce: Boolean,
    val onlyWhenParkOpen: Boolean,
    val quietHoursEnabled: Boolean,
    val quietStartMinutes: Int,
    val quietEndMinutes: Int,
    val cooldownMinutes: Int,
)

data class PushUnregisterRequest(
    val installationId: String,
)
