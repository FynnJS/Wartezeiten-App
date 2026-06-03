# Wartezeiten App

Android-App zur Anzeige aktueller Wartezeiten, Öffnungszeiten und Besucheraufkommen in Freizeitparks weltweit. Die App nutzt die Wartezeiten.APP API, speichert Daten lokal mit Room und zeigt die Oberfläche mit Jetpack Compose.

## Download

Die aktuelle APK wird über die Projekt-Website bereitgestellt:

https://wartezeiten-app.tutorialfynn.workers.dev/

Die Website liest `website/release.json` und verlinkt auf den neuesten GitHub Release im Repository `FynnJS/Wartezeiten-App`.

## Funktionen

- Parkliste mit Länder-, Favoriten- und Öffnungsstatus-Filter
- Parkdetails mit Wartezeiten, Öffnungszeiten, Wetter, Feiertagen und Auslastung
- Watchlist-Alarme für Wartezeiten, Attraktionsstatus, Parkstatus und Crowd-Level
- In-App-Hinweis und Benachrichtigung bei neuer APK-Version
- Offline-First-Datenhaltung über Room und automatische Aktualisierung

## Entwicklung

Voraussetzungen:

- Android Studio
- JDK 17
- Android SDK mit Compile SDK 35

Build:

```powershell
.\gradlew.bat :app:assembleDebug
```

Release-APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

## Website und Releases

Die Website liegt im Ordner `website/` und wird als Cloudflare Worker deployt. Bei einem neuen Release muss `website/release.json` auf die aktuelle APK zeigen, damit Website, In-App-Update-Banner und Update-Benachrichtigung dieselbe Version sehen.

Der GitHub Actions Workflow `.github/workflows/release-pipeline.yml` baut die Release-APK, lädt sie als GitHub-Release-Asset hoch und aktualisiert `website/release.json` automatisch.

Vor jedem Release prüfen:

- `CHANGELOG.md` ist aktualisiert.
- `website/release.json` zeigt auf Version, APK, SHA-256 und Release-URL.
- Diese README enthält neue relevante Informationen zu Installation, Website oder Release-Automation.
- Die Attribution zu https://www.wartezeiten.app bleibt in der App sichtbar und anklickbar.
