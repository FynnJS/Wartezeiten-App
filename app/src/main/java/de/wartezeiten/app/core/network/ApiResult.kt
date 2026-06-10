package de.wartezeiten.app.core.network

import com.google.gson.JsonParseException
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

fun NetworkError.toUserMessage(language: String = "de"): String {
    val useEnglish = language == "en"
    return when (this) {
        NetworkError.RateLimited -> if (useEnglish) {
            "Too many requests. Please wait a moment."
        } else {
            "Zu viele Anfragen. Bitte kurz warten."
        }
        NetworkError.NotFound -> if (useEnglish) {
            "Data could not be found."
        } else {
            "Daten konnten nicht gefunden werden."
        }
        NetworkError.Network -> if (useEnglish) {
            "No internet connection. Please check your network."
        } else {
            "Keine Internetverbindung. Bitte pr\u00fcfe dein Netzwerk."
        }
        NetworkError.Server -> if (useEnglish) {
            "Server error. Please try again later."
        } else {
            "Serverfehler. Bitte versuche es sp\u00e4ter erneut."
        }
        NetworkError.EmptyBody -> if (useEnglish) {
            "No data received from the server."
        } else {
            "Keine Daten vom Server erhalten."
        }
        NetworkError.Unknown -> if (useEnglish) {
            "An unknown error occurred."
        } else {
            "Ein unbekannter Fehler ist aufgetreten."
        }
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
                    message = "F\u00fcr diesen Park liegen aktuell keine Daten vor."
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
    } catch (exception: JsonParseException) {
        ApiResult.Error(NetworkError.Unknown, "Die Serverantwort konnte nicht gelesen werden.", cause = exception)
    } catch (exception: HttpException) {
        ApiResult.Error(NetworkError.Unknown, exception.message(), cause = exception)
    }
}
