package de.wartezeiten.app.update

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import de.wartezeiten.app.core.i18n.localized
import de.wartezeiten.app.data.remote.dto.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ApkDownloadResult {
    data class Success(val apkUri: Uri) : ApkDownloadResult
    data class Error(val message: String) : ApkDownloadResult
}

@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    suspend fun download(
        releaseInfo: AppUpdateInfo,
        language: String,
        onProgress: (Float) -> Unit,
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(directory, "wartezeiten-update.apk")
        file.delete()

        try {
            val request = Request.Builder().url(releaseInfo.apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ApkDownloadResult.Error(
                        localized(
                            language,
                            de = "Download fehlgeschlagen (HTTP ${response.code}).",
                            en = "Download failed (HTTP ${response.code}).",
                            fr = "Téléchargement échoué (HTTP ${response.code}).",
                            nl = "Download mislukt (HTTP ${response.code}).",
                        ),
                    )
                }
                val body = response.body ?: return@withContext ApkDownloadResult.Error(
                    localized(
                        language,
                        de = "Download lieferte keine Daten.",
                        en = "Download returned no data.",
                        fr = "Le téléchargement n'a renvoyé aucune donnée.",
                        nl = "Download leverde geen gegevens op.",
                    ),
                )
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var totalRead = 0L
                        var bytesRead = input.read(buffer)
                        while (bytesRead != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                onProgress((totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                            bytesRead = input.read(buffer)
                        }
                    }
                }
            }

            releaseInfo.sha256?.takeIf { it.isNotBlank() }?.let { expectedHash ->
                if (!file.sha256().equals(expectedHash, ignoreCase = true)) {
                    file.delete()
                    return@withContext ApkDownloadResult.Error(
                        localized(
                            language,
                            de = "Prüfsumme stimmt nicht überein. Download wurde verworfen.",
                            en = "Checksum mismatch. The download was discarded.",
                            fr = "La somme de contrôle ne correspond pas. Le téléchargement a été annulé.",
                            nl = "Controlesom komt niet overeen. De download is verwijderd.",
                        ),
                    )
                }
            }

            ApkDownloadResult.Success(
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
            )
        } catch (exception: IOException) {
            file.delete()
            ApkDownloadResult.Error(
                exception.message ?: localized(
                    language,
                    de = "Netzwerkfehler beim Download.",
                    en = "Network error during download.",
                    fr = "Erreur réseau pendant le téléchargement.",
                    nl = "Netwerkfout tijdens het downloaden.",
                ),
            )
        }
    }
}

internal fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        var bytesRead = input.read(buffer)
        while (bytesRead != -1) {
            digest.update(buffer, 0, bytesRead)
            bytesRead = input.read(buffer)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
