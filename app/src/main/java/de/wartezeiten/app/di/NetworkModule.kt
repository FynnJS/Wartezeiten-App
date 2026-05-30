package de.wartezeiten.app.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.wartezeiten.app.BuildConfig
import de.wartezeiten.app.core.network.CacheHeadersInterceptor
import de.wartezeiten.app.data.remote.HolidayApiService
import de.wartezeiten.app.data.remote.UpdateApiService
import de.wartezeiten.app.data.remote.WartezeitenApiService
import de.wartezeiten.app.data.remote.WeatherApiService
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        cacheHeadersInterceptor: CacheHeadersInterceptor
    ): OkHttpClient {
        val cache = Cache(
            directory = File(context.cacheDir, "wartezeiten-http-cache-v2"),
            maxSize = 10L * 1024L * 1024L
        )
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .addNetworkInterceptor(cacheHeadersInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.wartezeiten.app/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideWeatherRetrofit(
        client: OkHttpClient,
        gson: Gson
    ): WeatherApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WeatherApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideHolidayRetrofit(
        client: OkHttpClient,
        gson: Gson
    ): HolidayApiService {
        return Retrofit.Builder()
            .baseUrl("https://date.nager.at/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(HolidayApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWartezeitenApiService(
        retrofit: Retrofit
    ): WartezeitenApiService {
        return retrofit.create(WartezeitenApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUpdateApiService(
        client: OkHttpClient,
        gson: Gson
    ): UpdateApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.UPDATE_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(UpdateApiService::class.java)
    }
}
