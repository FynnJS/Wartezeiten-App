# Wartezeiten App Website

Diese Webseite ist für Cloudflare Pages vorbereitet. Sie enthält:

- APK-Download-Link
- Release-Metadaten in `release.json`
- SHA-256-Verifizierung
- Update-Info für die App

---

## 1. Cloudflare Pages Setup

### Voraussetzungen

- GitHub Account mit Repo: `https://github.com/FynnJS/Wartezeiten-App`
- Cloudflare Account (kostenlos möglich)
- Der Repo muss in GitHub öffentlich sein

### Schritt-für-Schritt Setup

#### 1.1 Cloudflare Pages mit GitHub verbinden

1. Melde dich bei Cloudflare an: https://dash.cloudflare.com
2. Gehe zu **Pages**
3. Klicke auf **Create a project** → **Connect to Git**
4. Wähle **GitHub**
5. Autorisiere Cloudflare für den Zugriff auf dein GitHub-Konto
6. Wähle das Repo: `Wartezeiten-App`

#### 1.2 Build-Konfiguration

Nachdem du das Repo ausgewählt hast:

- **Production branch**: `main`
- **Build command**: (leer lassen)
- **Build output directory**: `website`
- **Environment variables**: (keine nötig)

Dann klicke **Save and Deploy**.

#### 1.3 Domain konfigurieren

Nach dem Deploy bekommst du eine URL wie:

`https://wartezeiten-app-xxx.pages.dev`

Du kannst eine Custom-Domain hinzufügen oder diese URL verwenden.

**Wichtig**: Notiere diese URL, sie wird später in der App konfiguriert! Setze in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "UPDATE_BASE_URL", "\"https://wartezeiten-app-xxx.pages.dev/\"")
```

---

## 2. GitHub Release Workflow Schritt für Schritt

### 2.1 Vorbereitung vor jedem Release

1. Öffne `app/build.gradle.kts` lokal
2. Erhöhe `versionCode` um 1
3. Setze `versionName` auf die neue Version, z. B. `1.1.0`
4. Committe und pushe:

```bash
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.1.0"
git push origin main
```

### 2.2 GitHub Release erstellen

1. Gehe zu GitHub: https://github.com/FynnJS/Wartezeiten-App/releases
2. Klicke **Create a new release**
3. Setze die Felder:

| Feld | Wert | Beispiel |
|------|------|---------|
| **Tag name** | `v<version>` | `v1.1.0` |
| **Release title** | Beschreibung | `Version 1.1.0: Neue Features` |
| **Description** | Release Notes (Markdown, eine pro Zeile) | `- Feature X\n- Bug Fix Y\n- Performance` |
| **Pre-release** | Unchecked | ☐ |

4. Klicke **Publish release**

### 2.3 Was passiert danach automatisch

GitHub Actions startet sofort:

1. **Build** (2-5 Min.): `./gradlew :app:assembleRelease`
2. **Metadaten**: SHA-256, Dateigröße, Release-Datum berechnet
3. **Upload**: APK als GitHub Release Asset hochgeladen
4. **Website Update**: `website/release.json` wird geschrieben
5. **Git Commit**: `website/release.json` nach `main` gepusht
6. **Cloudflare Deploy** (30 Sek.): Pages aktualisiert Website

Status überprüfen:
- GitHub: `Actions` Tab → Workflow `Build Release APK and Update Website`
- Cloudflare: `Pages` → `Deployments`

---

## 3. Was sich in `website/release.json` ändert

### Beispiel vor und nach

**Vorher** (`website/release.json` initial):
```json
{
  "versionName": "1.0",
  "versionCode": 1,
  "releaseDate": "2026-05-30",
  "apkUrl": "./releases/wartezeiten-app-1.0.apk",
  "sha256": "REPLACE_WITH_REAL_SHA256_HASH",
  "apkSize": "REPLACE_WITH_FILE_SIZE"
}
```

**Nachdem du GitHub Release `v1.1.0` veröffentlicht hast**:
```json
{
  "versionName": "1.1.0",
  "versionCode": 10100,
  "releaseDate": "2026-05-30",
  "apkUrl": "https://github.com/FynnJS/Wartezeiten-App/releases/download/v1.1.0/wartezeiten-app-1.1.0.apk",
  "sha256": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z",
  "apkSize": "15728640 bytes",
  "releaseNotes": [
    "Neue Features hinzugefügt",
    "Performance verbessert",
    "Bug Fixes"
  ],
  "showBanner": true,
  "virusTotalUrl": ""
}
```

### Was wird automatisch gefüllt?

| Feld | Quelle | Beispiel |
|------|--------|---------|
| `versionName` | GitHub Tag | `1.1.0` (ohne `v`) |
| `versionCode` | Berechnet: `major*10000 + minor*100 + patch` | `10100` für `v1.1.0` |
| `releaseDate` | System-Datum (UTC) | `2026-05-30` |
| `apkUrl` | GitHub Release Asset | `https://github.com/.../releases/download/v1.1.0/...` |
| `sha256` | APK-Hashwert | `a1b2c3d4e5f...` |
| `apkSize` | APK-Dateigröße | `15728640 bytes` |
| `releaseNotes` | Release Description (Zeilen) | Array aus Description |
| `showBanner` | Immer aktiv | `true` |

### VirusTotal-Scan optional

Nach dem GitHub Release:

1. Gehe zu https://www.virustotal.com
2. Lade die APK vom GitHub Release hoch
3. Warte auf Scan (5-10 Min.)
4. Kopiere Scan-URL
5. Bearbeite `website/release.json` manuell:
   ```json
   "virusTotalUrl": "https://www.virustotal.com/gui/file/.../detection"
   ```
6. Committe und pushe:
   ```bash
   git add website/release.json
   git commit -m "chore: add virustotal link for v1.1.0"
   git push origin main
   ```

---

## Release-Prozess Checkliste

### ✅ Vor dem Release

- [ ] Neue Features und Bugfixes im Code fertig
- [ ] `app/build.gradle.kts`: `versionCode` erhöht (um 1)
- [ ] `app/build.gradle.kts`: `versionName` aktualisiert (z. B. `1.1.0`)
- [ ] Alle Änderungen committed: `git add . && git commit -m "..."`
- [ ] `main` gepusht: `git push origin main`

### 🚀 Release durchführen

1. GitHub Release erstellen mit Tag `v1.1.0`
2. Release Notes schreiben (eine Pro Zeile)
3. **Publish release** klicken
4. GitHub Actions wartet ab (~3-5 Min. für Build)
5. Actions Tab aufrufen und Status überprüfen

### ✨ Nach dem Release

- [ ] `website/release.json` aktualisiert (automatisch)
- [ ] APK von GitHub Release herunterladbar
- [ ] Website-Deploy erfolgreich auf Cloudflare Pages
- [ ] `https://<domain>.pages.dev` zeigt neue Version
- [ ] App-Update-Banner zeigt in nächster Minute an
- [ ] Optional: VirusTotal Scan durchführen und Link hinzufügen

---

## Beispiel: Kompletter Release-Ablauf

### Szenario: Release Version 1.1.0

**Schritt 1: Vorbereitung lokal**

```bash
# Änderungen in App machen, z. B. Feature X hinzufügen
# Dann:
git add app/src/main/...
git commit -m "feat: add Feature X"

# Version aktualisieren
# Bearbeite app/build.gradle.kts:
#   versionCode = 2
#   versionName = "1.1.0"

git add app/build.gradle.kts
git commit -m "chore: bump version to 1.1.0"
git push origin main
```

**Schritt 2: GitHub Release erstellen**

1. GitHub → Releases
2. **Create a new release**
3. Tag: `v1.1.0`
4. Title: `Version 1.1.0`
5. Description:
   ```
   - Feature X hinzugefügt
   - Performance in Park-List verbessert
   - Bug bei Favoriten-Speicherung behoben
   ```
6. **Publish release** ← Actions startet jetzt!

**Schritt 3: Actions läuft**

GitHub Actions zeigt im `Actions` Tab:

```
Build Release APK and Update Website
📊 running...
├─ Build release APK
├─ Prepare release metadata
├─ Upload APK to GitHub release
├─ Commit website/release.json
└─ Upload artifact
```

**Schritt 4: Nach ~5 Minuten**

- APK im Release sichtbar
- `website/release.json` auf GitHub aktualisiert
- Cloudflare Pages deployed Seite neu
- Website zeigt v1.1.0 zum Download

**Schritt 5: In der App**

- `release.json` wird geladen
- App vergleicht: `remote.versionCode (10100) > local.versionCode (1)`
- Update-Banner erscheint
- User kann APK herunterladen und installieren

---

## Troubleshooting

### Actions fehlgeschlagen?

**Problem**: "Build release APK failed"
- Lösung: Prüfe ob Java 17 installiert ist
- Oder: Lokaler Build funktioniert? `./gradlew :app:assembleRelease`

**Problem**: "Permission denied" beim Git Commit
- Lösung: GitHub Token überprüfen (sollte automatisch in Actions vorhanden sein)

**Problem**: "website/release.json nicht gefunden"
- Lösung: Sicherstellen, dass `website/` Ordner im Repo ist

### Website zeigt alte Version?

- Cloudflare Cache leeren: `Caching` → `Purge Everything`
- Oder: Warten 5 Min., Cache sollte ablaufen

### App zeigt Update nicht?

- Prüfe ob `UPDATE_BASE_URL` in `app/build.gradle.kts` korrekt gesetzt ist
- App muss neuen Build haben (alte Version kennt neue URL nicht)
- Prüfe ob `release.json` von `https://<domain>.pages.dev` erreichbar ist

---

## Sicherheit und Best Practices

- **Signiere immer die APK**: Die Action baut `release` APK, die muss signiert sein
- **HTTPS nur**: Website muss über HTTPS laufen (Cloudflare macht das automatisch)
- **GitHub Token**: Wird automatisch verwendet, braucht du nicht manuell setzen
- **VirusTotal**: Nach jedem Release für Vertrauen durchführen
- **Release Notes**: Schreib aussagekräftige Notes, User sehen die im Update-Banner



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
- Ergänze nach dem Upload den VirusTotal-Scan-Link in `release.json` und auf der Seite.

## GitHub Actions Automatisierung

Diese App kann jetzt automatisch die Release-APK bauen und die Website-Metadaten aktualisieren. Die dafür erstellte Action liegt in:

- `.github/workflows/release-pipeline.yml`

### Ablauf

1. Erstelle einen GitHub Release mit einem Tag im Format `v1.0.0`.
2. Cloudflare Pages stellt die Website aus `website/` bereit.
3. Sobald der Release veröffentlicht ist, führt GitHub Actions aus:
   - Build der Release-APK (`./gradlew :app:assembleRelease`)
   - Upload der APK als GitHub Release Asset
   - Aktualisierung von `website/release.json`
   - Push von `website/release.json` nach `main`
4. Cloudflare Pages deployed anschließend die aktualisierte Webseite automatisch.

### Tag-Format

Für korrekte Versionsnummern im Auto-Update nutze Tags wie:

- `v1.0.0`
- `v1.0.1`
- `v1.1.0`

Die Action wandelt das Tag in `versionCode` um nach der Regel:

`major * 10000 + minor * 100 + patch`

Beispiel: `v1.2.3` → `10203`

### Release-APK URL

Der `release.json`-Eintrag `apkUrl` verweist auf den GitHub Release Asset-Link:

`https://github.com/<user>/<repo>/releases/download/<tag>/<apk-name>.apk`

Das bedeutet:

- Die APK muss nicht im Git-Repo selbst gespeichert werden
- Die Website zeigt den Download-Link direkt an
- Die App kann den Link aus `release.json` laden und anzeigen

### Manuelle Ausführung

Falls du die Automation manuell testen möchtest, kannst du in GitHub im Reiter `Actions` die Action `Build Release APK and Update Website` manuell starten.
