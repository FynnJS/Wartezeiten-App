# Wartezeiten App

Android-App zur Anzeige aktueller Wartezeiten, Öffnungszeiten und Besucheraufkommen in Freizeitparks weltweit. Die App nutzt die Wartezeiten.APP API, speichert operative Live-Daten lokal mit Room und zeigt die Oberfläche mit Jetpack Compose.

## Download

Die aktuelle APK wird über die Projekt-Website bereitgestellt:

https://wartezeiten-app.tutorialfynn.workers.dev/

Die Website liest `website/release.json` und verlinkt auf den neuesten GitHub Release im Repository `FynnJS/Wartezeiten-App`.

## Funktionen

- Parkliste mit Länder-, Favoriten- und Öffnungsstatus-Filter
- Gemeinsame Suche nach Parks und Attraktionen mit direktem Sprung zur passenden Attraktion
- Parkdetails mit Wartezeiten, Öffnungszeiten, Wetter, Feiertagen, Auslastung und Trend-Chart
- Zentrale Cloudflare App-Daten für Ranking-, Trend- und Attraktionsstatistik-Snapshots
- Statistikbereich mit Park-, Datum- und Attraktionsauswahl für zentrale Tagesverläufe
- Watchlist-Alarme für Wartezeiten, Attraktionsstatus, Parkstatus und Crowd-Level
- Datenbasierter Multi-Park-Vergleich mit Suche, Sortierung und direktem Sprung zur Parkübersicht
- In-App-Hinweis und Benachrichtigung bei neuer APK-Version
- Offline-First-Datenhaltung über Room, Cache-Hinweis und automatische Aktualisierung für Live-Daten

## Watchlist-Benachrichtigungen

Watchlist-Alarme werden lokal über WorkManager geprüft und als Android-Benachrichtigung angezeigt. Neue Alarme starten zusätzlich einen schnellen Einmal-Check; nach Neustart oder App-Update werden Hintergrundprüfungen wieder angemeldet. Der periodische lokale Check läuft alle 30 Minuten. Es werden keine Firebase-/FCM-Konfiguration und keine Worker-Secrets benötigt. Ab Android 13 muss die Benachrichtigungsberechtigung erlaubt sein; einzelne OEM-Systeme können zusätzlich Autostart- oder Akku-Einstellungen verlangen. Android kann periodische Arbeit im Standby/Doze trotzdem verzögern.

## Entwicklung

Voraussetzungen:

- Android Studio
- JDK 17
- Android SDK mit Compile SDK 35
- Node.js für Cloudflare Worker/Wrangler

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

Der GitHub Actions Workflow `.github/workflows/release-pipeline.yml` baut die Release-APK, lädt sie als GitHub-Release-Asset hoch und aktualisiert `website/release.json` automatisch.

Cloudflare App-Daten für Ranking, Trends und zentrale Attraktionsstatistiken werden vom Worker-Cron erzeugt. Setup-Hinweise stehen in `website/CLOUDFLARE-APP-DATA.md`.

Vor jedem Release prüfen:

- `CHANGELOG.md` ist aktualisiert.
- `website/release.json` zeigt auf Version, APK und Release-URL.
- Diese README enthält neue relevante Informationen zu Installation, Website oder Release-Automation.
- Die Attribution zu https://www.wartezeiten.app bleibt in der App sichtbar und anklickbar.
