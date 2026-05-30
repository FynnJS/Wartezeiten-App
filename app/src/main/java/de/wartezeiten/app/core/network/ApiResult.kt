package de.wartezeiten.app.core.network

import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    data class Error(
        val type: NetworkError,
        val message: String? = null,
        val retryAfterSeconds: Long? = null,
        val cause: Throwable? = null
    ) : ApiResult<Nothing>
}

enum class NetworkError {
    RateLimited,
    NotFound,
    Network,
    Server,
    EmptyBody,
    Unknown
}

fun NetworkError.toUserMessage(): String {
    return when (this) {
        NetworkError.RateLimited -> "Zu viele Anfragen. Bitte kurz warten."
        NetworkError.NotFound -> "Daten konnten nicht gefunden werden."
        NetworkError.Network -> "Keine Internetverbindung. Bitte prüfe dein Netzwerk."
        NetworkError.Server -> "Serverfehler. Bitte versuche es später erneut."
        NetworkError.EmptyBody -> "Keine Daten vom Server erhalten."
        NetworkError.Unknown -> "Ein unbekannter Fehler ist aufgetreten."
    }
}

suspend fun <T> safeApiCall(call: suspend () -> Response<T>): ApiResult<T> {
    return try {
        val response = call()
        when {
            response.isSuccessful -> {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(NetworkError.EmptyBody, "Die API hat keine Daten geliefert.")
                }
            }
            response.code() == 429 -> {
                ApiResult.Error(
                    type = NetworkError.RateLimited,
                    message = "Zu viele Anfragen. Bitte kurz warten und erneut versuchen.",
                    retryAfterSeconds = response.headers()["Retry-After"]?.toLongOrNull()
                )
            }
            response.code() == 404 -> {
                ApiResult.Error(
                    type = NetworkError.NotFound,
                    message = "Fuer diesen Park liegen aktuell keine Daten vor."
                )
            }
            response.code() in 500..599 -> {
                ApiResult.Error(NetworkError.Server, "Der Server ist aktuell nicht erreichbar.")
            }
            else -> {
                ApiResult.Error(NetworkError.Unknown, "Unerwarteter Fehler: HTTP ${response.code()}.")
            }
        }
    } catch (exception: IOException) {
        ApiResult.Error(NetworkError.Network, "Keine Netzwerkverbindung.", cause = exception)
    } catch (exception: HttpException) {
        ApiResult.Error(NetworkError.Unknown, exception.message(), cause = exception)
    }
}


