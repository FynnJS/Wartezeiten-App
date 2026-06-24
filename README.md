# Wartezeiten App

Android-App zur Anzeige aktueller Wartezeiten, Öffnungszeiten und Besucheraufkommen in Freizeitparks weltweit. Die App nutzt die Wartezeiten.APP API, speichert operative Live-Daten lokal mit Room und zeigt die Oberfläche mit Jetpack Compose.

## Download

Die aktuelle APK wird über die Projekt-Website bereitgestellt:

https://wartezeiten-app.tutorialfynn.workers.dev/

Die Website liest `website/release.json` und verlinkt auf den neuesten GitHub Release im Repository `FynnJS/Wartezeiten-App`.

## Funktionen

- Parkliste mit Länder-, Favoriten- und Öffnungsstatus-Filter
- Gemeinsame Suche nach Parks und Attraktionen mit direktem Sprung zur passenden Attraktion
- Parkdetails mit Wartezeiten, Öffnungszeiten, Wetter, Feiertagen, aktueller Auslastung und zentraler Parkstatistik
- Attraktions-Detailkarten mit Verlauf, Prognose, persönlicher Notiz, Watchlist-Shortcut und Deep-Link-Share
- Historisch gestützte "Jetzt oder später?"-Einordnung für geöffnete Attraktionen
- Zentrale Cloudflare App-Daten für Ranking-, Trend- und Attraktionsstatistik-Snapshots
- Statistikbereich mit Park-, Datum- und Attraktionsauswahl für zentrale Tagesverläufe
- Watchlist-Alarme mit Ruhezeiten, Einmal-Zustellung, Parköffnungsregel, Mindestabstand und täglicher Parkzusammenfassung
- Datenbasierter Multi-Park-Vergleich mit Suche, Sortierung und direktem Sprung zur Parkübersicht
- Startbildschirm-Widget fuer einen Lieblingspark mit Oeffnungsstatus, Wartezeit-Kennzahlen, drei Attraktionen, Datenalter sowie manuellem und periodischem Refresh
- Vier Sprachen (Deutsch, Englisch, Französisch, Niederländisch) mit zentraler Sprachauswahl in den Einstellungen
- Pflicht-Update-Sperre mit In-App-APK-Download samt Fortschrittsanzeige, SHA-256-Prüfung und direktem Installationsdialog; "Was ist neu"-Dialog nach erfolgreichem Update
- Offline-First-Datenhaltung über Room, Cache-Hinweis und automatische Aktualisierung für Live-Daten

## Watchlist-Benachrichtigungen

Watchlist-Alarme werden weiterhin lokal ueber WorkManager geprueft und als Android-Benachrichtigung angezeigt. Neue Alarme starten zusaetzlich einen schnellen Einmal-Check; nach Neustart oder App-Update werden Hintergrundpruefungen wieder angemeldet. Der periodische lokale Fallback-Check laeuft alle 30 Minuten. Ab Android 13 muss die Benachrichtigungsberechtigung erlaubt sein; einzelne OEM-Systeme koennen zusaetzlich Autostart- oder Akku-Einstellungen verlangen.

Optional kann die App Watchlist-Alarme serverseitig per Firebase Cloud Messaging empfangen. Dafuer synchronisiert die App ihre lokale Watchlist an den Cloudflare Worker (`/push/register`, `/push/watchlist`), der die Alerts in D1 speichert und ueber einen separaten `* * * * *`-Cron minuetlich prueft, soweit Upstream-API, Firebase und Android-Zustellung mitspielen. Die Statistik-Shards bleiben auf den versetzten 5-Minuten-Regeln (`0-59/5`, `1-59/5`, `2-59/5`).

Android-Build-Properties fuer FCM:

- `FIREBASE_APPLICATION_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_GCM_SENDER_ID`

Cloudflare Worker-Secrets fuer FCM HTTP v1:

- `FCM_PROJECT_ID`
- `FCM_CLIENT_EMAIL`
- `FCM_PRIVATE_KEY`

Ohne diese Werte bleibt Push deaktiviert und die lokale WorkManager-Loesung uebernimmt.
Der Endpunkt `/push/status` zeigt ohne Geheimnisse an, ob D1 und FCM im Worker einsatzbereit sind. Die App meldet Standby-Push erst dann als aktiv, wenn sowohl ihre Firebase-Konfiguration als auch dieser Serverstatus erfolgreich sind.
Lokale Builds können die Android-Werte alternativ aus einer unveränderten `app/google-services.json` lesen; die Datei bleibt durch `.gitignore` lokal.

Das vorbereitete Setup kann mit den beiden unveränderten Firebase-JSON-Dateien ausgeführt werden:

```powershell
.\scripts\configure-push.ps1 -GoogleServicesJson C:\Pfad\google-services.json -ServiceAccountJson C:\Pfad\firebase-service-account.json
```

Das Skript setzt die GitHub-Variablen, hinterlegt die Worker-Secrets, deployed den Worker und prüft den öffentlichen Bereitschaftsstatus. Details stehen in `docs/PUSH-SETUP.md`.

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

Die Website liegt im Ordner `website/` und wird als Cloudflare Worker deployt. Bei einem neuen Release muss `website/release.json` auf die aktuelle APK zeigen, damit Website, Pflicht-Update-Screen und Update-Benachrichtigung dieselbe Version sehen.

Der GitHub Actions Workflow `.github/workflows/release-pipeline.yml` baut die Release-APK, lädt sie als GitHub-Release-Asset hoch und aktualisiert `website/release.json` automatisch.

Für veröffentlichte GitHub-Releases muss die Pipeline mit einem stabilen Release-Keystore signieren. Dafür müssen diese Repository-Secrets gepflegt sein:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`
- `RELEASE_CERT_SHA256`

Fehlen diese Secrets bei einem Release-Event, bricht die Pipeline ab. Das verhindert APKs, die wegen abweichender Signatur nicht als Update über eine bestehende Installation installiert werden können.

Der dauerhafte Schlüssel wird einmalig eingerichtet und anschließend lokal sowie in GitHub hinterlegt:

```powershell
.\scripts\configure-release-signing.ps1
```

Der dabei ausgegebene Sicherungsordner muss zusätzlich außerhalb des Rechners gesichert werden. Der private Keystore lässt sich aus einer veröffentlichten APK nicht wiederherstellen.

Cloudflare App-Daten für Ranking, Trends und zentrale Attraktionsstatistiken werden vom Worker-Cron erzeugt. Der Cron schreibt D1-Statistik-Snapshots parkweise und nutzt bei aktivem D1 nur die drei realen 5-Minuten-Cron-Shards; `APP_DATA_HISTORY_SHARDS` ist nur noch für Legacy-KV-Fallbacks relevant. Setup-Hinweise stehen in `website/CLOUDFLARE-APP-DATA.md`.

Vor jedem Release prüfen:

- `CHANGELOG.md` ist aktualisiert.
- `/push/status` meldet `pushReady: true`, bevor ein Release mit Standby-Push veröffentlicht wird.
- `website/release.json` zeigt erst dann auf Version, APK und Release-URL, wenn die APK als GitHub-Release-Asset verfügbar ist.
- Diese README enthält neue relevante Informationen zu Installation, Website oder Release-Automation.
- Die Attribution zu https://www.wartezeiten.app bleibt in der App sichtbar und anklickbar.
