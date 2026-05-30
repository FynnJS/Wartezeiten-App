# Wartezeiten App Website

Diese Webseite ist für Cloudflare Pages vorbereitet. Sie enthält:

- APK-Download-Link
- Release-Metadaten in `release.json`
- SHA-256-Verifizierung
- Update-Info für die App

## Deployment auf Cloudflare Pages

1. Verbinde dein Git-Repository mit Cloudflare Pages.
2. Wähle als Build-Ordner den Ordner `website`.
3. Setze `Build command` auf leer.
4. Setze `Build output directory` auf `.`.
5. Deploy.

## Release-Prozess

1. Erstelle einen signed Release-Build der App:
   - In Android Studio: Build > Generate Signed Bundle / APK.
   - Oder per Gradle: `./gradlew :app:assembleRelease`.
2. Kopiere die fertige Release-APK nach `website/releases/`.
3. Berechne den SHA-256 Hash:
   - Windows: `CertUtil -hashfile "path\to\wartezeiten-app-1.0.apk" SHA256`
   - Linux/macOS: `sha256sum wartezeiten-app-1.0.apk`
4. Aktualisiere `website/release.json`:
   - `apkUrl`
   - `versionName`
   - `versionCode`
   - `releaseDate`
   - `sha256`
   - `apkSize`
   - `releaseNotes`
   - optional `virusTotalUrl`
5. Committe und pushe die Änderungen.
6. Cloudflare Pages aktualisiert die Seite automatisch.

## So sieht die App den Update-Check

Die App sollte die Datei `release.json` abrufen und den Wert `versionCode` mit der installierten Version vergleichen.

### Beispiel-Kotlin (Simplified)

```kotlin
private val releaseUrl = "https://deine-domain.pages.dev/release.json"

suspend fun checkForUpdate(): ReleaseInfo? {
    val request = Request.Builder().url(releaseUrl).build()
    return OkHttpClient().newCall(request).execute().use { response ->
        if (!response.isSuccessful) return null
        val json = response.body?.string() ?: return null
        return Gson().fromJson(json, ReleaseInfo::class.java)
    }
}

fun showUpdateBanner(release: ReleaseInfo, installedVersionCode: Int) {
    if (release.versionCode > installedVersionCode) {
        // Zeige Banner mit release.apkUrl
    }
}
```

### ReleaseInfo-Datenklasse

```kotlin
data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val apkUrl: String,
    val sha256: String,
    val apkSize: String,
    val releaseNotes: List<String>,
    val showBanner: Boolean
)
```

## Installation außerhalb von Google Play

1. Lade die APK von der Seite herunter.
2. Prüfe den SHA-256 Hash.
3. Öffne die APK und ermögliche ggf. die Installation aus unbekannten Quellen.
4. Installiere die APK manuell.

## Hinweis zur Sicherheit

- Nutze HTTPS für die Seite.
- Veröffentliche die APK nie ungesigned.
- ergänze nach dem Upload den VirusTotal-Scan-Link in `release.json` und auf der Seite.
