package de.wartezeiten.app.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface HolidayApiService {
    @GET("api/v3/NextPublicHolidays/{countryCode}")
    suspend fun getNextHolidays(@Path("countryCode") countryCode: String): Response<List<HolidayDto>>
}

data class HolidayDto(
    val date: String?,
    val name: String?,
    val localName: String?
)
