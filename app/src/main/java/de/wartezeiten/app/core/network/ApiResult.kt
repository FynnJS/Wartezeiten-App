package de.wartezeiten.app.core.network

import com.google.gson.JsonParseException
import de.wartezeiten.app.core.i18n.localized
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
    return when (this) {
        NetworkError.RateLimited -> localized(
            language,
            de = "Zu viele Anfragen. Bitte kurz warten.",
            en = "Too many requests. Please wait a moment.",
            fr = "Trop de requ\u00eates. Merci d'attendre un instant.",
            nl = "Te veel verzoeken. Wacht even.",
        )
        NetworkError.NotFound -> localized(
            language,
            de = "Daten konnten nicht gefunden werden.",
            en = "Data could not be found.",
            fr = "Les donn\u00e9es n'ont pas pu \u00eatre trouv\u00e9es.",
            nl = "Gegevens konden niet worden gevonden.",
        )
        NetworkError.Network -> localized(
            language,
            de = "Keine Internetverbindung. Bitte pr\u00fcfe dein Netzwerk.",
            en = "No internet connection. Please check your network.",
            fr = "Aucune connexion internet. V\u00e9rifie ta connexion r\u00e9seau.",
            nl = "Geen internetverbinding. Controleer je netwerk.",
        )
        NetworkError.Server -> localized(
            language,
            de = "Serverfehler. Bitte versuche es sp\u00e4ter erneut.",
            en = "Server error. Please try again later.",
            fr = "Erreur du serveur. Merci de r\u00e9essayer plus tard.",
            nl = "Serverfout. Probeer het later opnieuw.",
        )
        NetworkError.EmptyBody -> localized(
            language,
            de = "Keine Daten vom Server erhalten.",
            en = "No data received from the server.",
            fr = "Aucune donn\u00e9e re\u00e7ue du serveur.",
            nl = "Geen gegevens van de server ontvangen.",
        )
        NetworkError.Unknown -> localized(
            language,
            de = "Ein unbekannter Fehler ist aufgetreten.",
            en = "An unknown error occurred.",
            fr = "Une erreur inconnue s'est produite.",
            nl = "Er is een onbekende fout opgetreden.",
        )
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
