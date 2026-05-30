package de.wartezeiten.app.data.remote

import de.wartezeiten.app.data.remote.dto.AppUpdateInfo
import retrofit2.Response
import retrofit2.http.GET

interface UpdateApiService {
    @GET("release.json")
    suspend fun fetchRelease(): Response<AppUpdateInfo>
}
