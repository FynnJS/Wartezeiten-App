package de.wartezeiten.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AppUpdateInfo(
    @SerializedName("versionName") val versionName: String,
    @SerializedName("versionCode") val versionCode: Int,
    @SerializedName("releaseDate") val releaseDate: String,
    @SerializedName("apkUrl") val apkUrl: String,
    @SerializedName("releasePageUrl") val releasePageUrl: String? = null,
    @SerializedName("sha256") val sha256: String? = null,
    @SerializedName("apkSize") val apkSize: String? = null,
    @SerializedName("releaseNotes") val releaseNotes: List<String> = emptyList(),
    @SerializedName("releaseNotesLocalized") val releaseNotesLocalized: Map<String, List<String>> = emptyMap(),
    @SerializedName("showBanner") val showBanner: Boolean = false,
    @SerializedName("virusTotalUrl") val virusTotalUrl: String? = null
) {
    fun releaseNotesFor(language: String): List<String> {
        return releaseNotesLocalized[language]
            ?: releaseNotesLocalized[language.lowercase()]
            ?: releaseNotesLocalized["de"]
            ?: releaseNotesLocalized["en"]
            ?: releaseNotes
    }
}
