package de.wartezeiten.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class CacheHeadersInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val path = request.url.encodedPath

        val policy = cachePolicyForPath(path) ?: return response

        return response.newBuilder()
            .header("Cache-Control", "public, max-age=${policy.maxAgeSeconds}")
            .addHeader("Vary", policy.varyHeader)
            .build()
    }
}

internal data class CachePolicy(
    val maxAgeSeconds: Long,
    val varyHeader: String,
)

internal fun cachePolicyForPath(path: String): CachePolicy? {
    return when {
        path.endsWith("/parks") -> CachePolicy(
            maxAgeSeconds = TimeUnit.HOURS.toSeconds(24),
            varyHeader = "language",
        )
        path.endsWith("/openingtimes") -> CachePolicy(
            maxAgeSeconds = TimeUnit.MINUTES.toSeconds(30),
            varyHeader = "park",
        )
        path.endsWith("/waitingtimes") -> CachePolicy(
            maxAgeSeconds = TimeUnit.MINUTES.toSeconds(5),
            varyHeader = "park, language",
        )
        path.endsWith("/crowdlevel") -> CachePolicy(
            maxAgeSeconds = TimeUnit.MINUTES.toSeconds(5),
            varyHeader = "park",
        )
        else -> null
    }
}
