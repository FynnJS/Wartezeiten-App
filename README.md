# Wartezeiten App

Android-App zur Anzeige aktueller Wartezeiten, Oeffnungszeiten und Besucheraufkommen in Freizeitparks weltweit. Die App nutzt die Wartezeiten.APP API, speichert Daten lokal mit Room und zeigt die Oberflaeche mit Jetpack Compose.

## Download

Die aktuelle APK wird ueber die Projekt-Website bereitgestellt:

https://wartezeiten-app.tutorialfynn.workers.dev/

Die Website liest `website/release.json` und verlinkt auf den neuesten GitHub Release im Repository `FynnJS/Wartezeiten-App`.

## Funktionen

- Parkliste mit Laender-, Favoriten- und Oeffnungsstatus-Filter
- Parkdetails mit Wartezeiten, Oeffnungszeiten, Wetter, Feiertagen, Auslastung und Trend-Chart
- Zentrale Cloudflare App-Daten fuer Ranking- und Trend-Snapshots
- Watchlist-Alarme fuer Wartezeiten, Attraktionsstatus, Parkstatus und Crowd-Level
- Schnellzugriff auf Favoriten und Ranking "Bester Wert heute"
- Teilen von Parkstatus und einzelnen Attraktionen
- In-App-Hinweis und Benachrichtigung bei neuer APK-Version
- Offline-First-Datenhaltung ueber Room und automatische Aktualisierung

## Entwicklung

Voraussetzungen:

- Android Studio
- JDK 17
- Android SDK mit Compile SDK 35
- Node.js fuer Cloudflare Worker/Wrangler

Build:

```powershell
.\gradlew.bat :app:assembleDebug
```

Release-APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Cloudflare Worker lokal:

```powershell
npm install
npm run worker:dev
```

## Website und Releases

Die Website liegt im Ordner `website/` und wird als Cloudflare Worker deployt. Bei einem neuen Release muss `website/release.json` auf die aktuelle APK zeigen, damit Website, In-App-Update-Banner und Update-Benachrichtigung dieselbe Version sehen.

Der GitHub Actions Workflow `.github/workflows/release-pipeline.yml` baut die Release-APK, laedt sie als GitHub-Release-Asset hoch und aktualisiert `website/release.json` automatisch.

Cloudflare App-Daten fuer Ranking und Trends werden vom Worker-Cron erzeugt. Setup-Hinweise stehen in `website/CLOUDFLARE-APP-DATA.md`.

Vor jedem Release pruefen:

- `CHANGELOG.md` ist aktualisiert.
- `website/release.json` zeigt auf Version, APK und Release-URL.
- Diese README enthaelt neue relevante Informationen zu Installation, Website oder Release-Automation.
- Die Attribution zu https://www.wartezeiten.app bleibt in der App sichtbar und anklickbar.
