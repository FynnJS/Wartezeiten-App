# Wartezeiten App – Projekt-Dokumentation für KI-Agents

## 1. Projektübersicht
**Wartezeiten App** ist eine Android-Anwendung zur Anzeige von Wartezeiten in Freizeitparks. Basiert auf [Wartezeiten.APP](https://wartezeiten.app) API.

### Tech-Stack
| Komponente | Technologie |
|-----------|-------------|
| Sprache | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Architektur | Clean Architecture + MVVM |
| Dependency Injection | Hilt |
| Datenspeicherung | Room (lokal) + Retrofit (API) |
| Concurrency | Coroutines & Flow |

### Architektur-Layer
- **`core`** – Netzwerk, Dispatcher, i18n, Utils
- **`data`** – Repositories, lokale/remote Datenquellen, DTOs, Mapper
- **`domain`** – Geschäftslogik, Use-Cases, Modelle
- **`di`** – Hilt Dependency Injection Module
- **`ui`** – Jetpack Compose Screens, Komponenten, Theming

## 2. API Dokumentation (Wartezeiten.APP v1.6.0)
**Basis:** `https://api.wartezeiten.app`, Header-basierte Parameter ([vollständige Doku](https://api.wartezeiten.app))

| Endpunkt | Header | Response | Cache |
|----------|--------|----------|-------|
| `/v1/parks` | `language` (de/en) | Parks: `id`, `uuid`, `name`, `land` | 24h |
| `/v1/openingtimes` | `park` | `opened_today`, `open_from`, `closed_from` | 30min* |
| `/v1/waitingtimes` | `park`, `language` | Attraktionen: `name`, `waitingtime`, `status`, `uuid`, `datetime` | 5min |
| `/v1/crowdlevel` | `park` | `crowd_level`, `timestamp` | 5-10min |

*clientseitig max. 30min trotz API-Hinweis (verhindert Tageswechsel-Probleme)

## 3. Kernarchitektur & Qualitätsrichtlinien

### Offline-First & Error Handling
- Lokale Speicherung via Room, Daten über Flow an UI
- Zentrales Error Handling via `ApiResult` Sealed Interface
- Bei Netzwerkfehlern: Offline-Banner mit Alter der Cached-Daten

### Qualitätsanforderungen
✅ **Verifikation nach jeder Änderung** (UI visuell, Logik getestet)  
✅ **Commit-Message:** Nach Änderungen einen aussagekräftigen Commit-Message Vorschlag basierend auf den vorgenommenen Änderungen geben
✅ **Technisch robust, responsiv und visuell modern** (Material 3 konsistent)  
✅ **Projektwissen aktualisieren:** Architekturentscheidungen, Fallstricke in dieser Datei dokumentieren  

## 4. Kritische Implementierungsdetails

### API & Caching
| ⚠️ Detail | Impact |
|-----------|--------|
| **Header-Parameter** | `park`/`language` als Header (nicht Query), sonst falsche Daten pro Park |
| **OkHttp Vary-Header** | `/v1/openingtimes`, `/v1/crowdlevel` → `Vary: park`; `/v1/waitingtimes` → `Vary: park, language` |
| **Cache-Strategie** | `/v1/openingtimes`: Max. 30min (nicht 24h), verhindert Tageswechsel-Bugs |

### Öffnungszeiten & Parkstatus
| ⚠️ Detail | Regel |
|-----------|-------|
| **Quelle der Wahrheit** | `opened_today` Boolean ist maßgeblich, nicht `status`-Strings |
| **Crowd Level bei geschlossenen Parks** | Wenn `opened_today=false` → "Heute geschlossen" anzeigen, kein altes `crowd_level` |
| **Geschlossene Parks & Attraktionen** | Attraktionslisten sichtbar (Katalog), aber Live-Aussagen (Auslastung, Empfehlungen) nur bei geöffnet |
| **Fehlende Wartezeitdaten erkennen** | `isParkOpenWithoutWaitingTimeData` in `domain/model/ParkOpeningWindow.kt` detektiert Ausfälle; zeige `WaitingTimeDataGapBanner` |

### UI-Verhalten
- **Auto-Update:** Jede Minute (via `viewModelScope` + `delay`)
- **Refresh-Feedback:** Nur manuelle Updates → Snackbar. Initiales Laden / Auto-Refresh → keine Snackbar
- **Zeit-Anzeige:** Aktuelle Parkzeit + letzter API-Update-Zeitpunkt (aus API-Timestamp oder Gerätezeit als Fallback)
- **Filter & Sortierung:** Parks (Land, Status), Attraktionen (Wartezeit auf/ab, Name, Status); Standard: höchste Wartezeit zuerst
- **Offen-Filter:** Snapshots max. 30min alt; älter → nicht als geöffnet darstellen
- **Keine Logos:** Nur Text + dezente Flaggen (ISO-Code → Regional Indicators, z.B. `US` → 🇺🇸)

### Push-Notifications & Watchlist
**Zwei Modi:**
1. **WorkManager (Fallback):** `NotificationWorker` lädt bei neuen Alarmen einmalig, prüft dann alle 30min
2. **Firebase Cloud Messaging (Premium):** `PushRegistrationManager` sync Token/Watchlist → Cloudflare Worker → D1 → minütlicher Cron

**Konfiguration erforderlich:**
- Android: `FIREBASE_APPLICATION_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_GCM_SENDER_ID` (Gradle Properties)
- Worker: `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY` (Secrets)
- Ohne Konfiguration: WorkManager übernimmt, kein Push-Fehler

**Alarm-Typen:**
- "Alle Änderungen": Parkstatus/Attraktionen-Vergleich → nur bei echten Änderungen
- `WAIT_TIME_BELOW`/`WAIT_TIME_ABOVE`: Bei `attraction_id` exklusiv diese Attraktion (kein Fallback auf andere!)
- **Erweiterte Regeln:** Einmalig, während Parköffnung, Ruhezeit 22:00-08:00, Mindestabstand 15-120min
- `DAILY_SUMMARY`: Täglich ~18:00 Parkzeit mit Status/Auslastung/offenen Attraktionen

**Push-Health:**
- `/push/status` liefert `d1Configured`, `fcmConfigured`, `pushReady` (Booleans)

### Statistik & D1 (Cloudflare)
**Zentrale Snapshot-Verwaltung:**
- Worker sammelt per Cron in **versetzten 5-Minuten-Shards** (4 Park-Shards parallel)
- D1: `attraction_history_snapshots` (Park/Zeit/Attraktionen) + `attraction_history_days` (Metadaten)
- Legacy-KV-Fallback für alte Tagesstatistiken

**⚠️ D1-Fallstricke:**
| Fallstrick | Lösung |
|-----------|--------|
| **Write-Reihenfolge** | Snapshots *parkweise* früh schreiben, nicht am Ende → Timeout → leere Statistiken |
| **Schema-Guard** | `ensureAttractionHistoryD1` idempotent vor Reads/Writes |
| **Cron-Subrequests** | Zu große Shards → "Too many subrequests" → leere Marker; `DEFAULT_CRON_SHARDS` anpassen |
| **Retention** | `APP_DATA_D1_RETENTION_DAYS` (Default 14), alte Snapshots vor Writes löschen |
| **Zeitstempel** | `captured_at_millis` = Worker-Zeit (`Date.now()`), nicht API-`datetime` |
| **Parallele Queries** | GROUP-BY + JOIN beide parallel, nicht N sequenzielle Reads pro Park |

**Live-Quelle für Park-Filter:**
- `/app-data/global-markers/latest.json` für aktuellen Park-Status
- Fallback: lokale Öffnungs-/Wartezeiten-Scans

### Statistik-UI
- **Heutige Daten:** Initial nur "heute" wenn zentrale Daten vorhanden, sonst `latestDate`
- **Heutige Snapshots:** Keine zukünftigen Messpunkte im Graph anzeigen
- **Ohne heutige Messpunkte:** Explizit anzeigen "Für heute noch keine zentralen Daten"
- **Attraktions-Prognose:** 1-3h Vorhersage + bis zu 7 historische Vergleich-Tage (mind. 3 für konservative Aussagen)
- **Trendabruf:** `/app-data/trend-history.json?parkKey={parkKey}` (Filter in D1-Query)

### Multi-Park-Vergleich
- Datenbasierter Screen in `ui/compare` (keine Park-Ratings mehr)
- Read-only, max. 4 Parks, suchbar nach Name/Land
- Kennzahlen aus aktuellen Wartezeiten berechnet

### Offline-/Such-/Share-UX
- **Offline-Banner:** Netzwerkfehler + Cache → Banner mit Alter
- **Suchhistorie:** Letzte 5 bestätigte Suchen in `PreferencesDataSource`
- **Alias-Suche:** `app/src/main/assets/park_aliases.csv` (Code-Heuristik nur Fallback)
- **Share:** Statistik-Screens → PNG via Android Sharesheet
- **Zuletzt angesehen:** 8 Einträge in `PreferencesDataSource`, bei Deep-Link/Notification aktualisieren

### Start-/Detail-UX
- **Dashboard:** Zuletzt angesehene + Favoriten mit Öffnungs-/Wartezeit-Kennzahlen
- **Parkdetails:** Offline-Banner, Datenqualitätskarte; als Text teilbar
- **Attraktions-Details:** Route `parks/{parkKey}?attractionId={attractionId}` → Status, Verlauf, Prognose, Watchlist, Notiz
- **Notizen:** Lokal in Room, bei Cache-Leerung erhalten bleiben

### Homescreen-Widget (Jetpack Glance)
- Speichert `park_key` + bis zu 3 Attraktions-IDs
- Update via `refreshParkDetail`, öffnet Park per `wartezeiten://parks/{parkKey}`
- Wartezeiten nur wenn Park geöffnet (`isParkCurrentlyOpen`)
- **Update:** WorkManager alle 30min; manueller Refresh sofort + andere Instanzen

### Cache-Verwaltung
- **Löschen in Einstellungen:** API-/Statistik-Cache → Favoriten/Watchlist/Settings bleiben

### Mehrsprachigkeit (DE/EN/FR/NL)
- **UI-Strings:** `localized(language, de = ..., en = ..., fr = ..., nl = ...)` (nicht if-ternaries)
- **API-Calls:** `language.toApiLanguage()` (API nur de/en)
- **Push-Texte:** Worker nutzt Installations-Sprache → `localizedPushText()` (4 Sprachen)
- **Attraktions-ID:** Nutzt fest "de" (nicht UI-Sprache abhängig)
- **Sprachauswahl-UI:** Mit Flagge + nativer Sprachname

### "Was ist neu"-Dialog & Versioning
- Einmalig nach Update, gesteuert via `lastSeenVersionCode` vs. `VERSION_CODE`
- **Bei Release:** `WhatsNewRelease`-Eintrag in `WhatsNewContent.kt` mit derselben `versionCode`

### ⚠️ Fallstricke
| Fallstrick | Lösung |
|-----------|--------|
| **Encoding-Bug (v1.1.6)** | Mojibake statt UTF-8 (…→â€¦). Vor jedem Commit: `grep -n "â€"` prüfen |
| **Heutiger Verlauf** | `buildAttractionHistorySeries` nicht `firstOrNull()` nutzen; `it.date == today` filtern |
| **Wartezeit-Alarm-Kandidaten** | `candidates = alert.attraction_id ? (target ? [target] : []) : openAttractions` |
| **Push-Prozent** | `formatPercent()` ganzzahlig runden |
| **Watchlist-API-Fehler** | Park-Alarme brauchen `/v1/openingtimes`; Attraktionsalarme laufen auch ohne |
| **CoroutineScope in Application/Service** | Nie anonymen Scope(SupervisorJob()) ohne Referenz erzeugen (Memory-Leak, kein Cancel). Immer als Property halten + cancel() in onTerminate/onDestroy. onTerminate ist nicht immer zuverlässig (Process kill). |

## 5. Release & Signierung

### Release-Keystore
- **Permanent:** `scripts/configure-release-signing.ps1` erzeugt Keystore + Secrets
- **Fingerprint:** Kanonisch in `config/release-signing.properties` (aktuell: `272e40a90d94e756d6b940aa410d88a0c42617d11c81fe42af7fe5680be5660e`). Bei Keystore-Rotation den Hash aus einem erfolgreichen (oder fehlgeschlagenen Verify-)Build übernehmen und hier + in der Properties aktualisieren. Siehe auch Workflow-Fehlerbehebung in der Historie.
- **Sicherung:** Außerhalb Repo aufbewahren, extern sichern
- **GitHub Actions:** Secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
- **Verifikation:** `.github/scripts/verify-release-certificate.ps1` prüft fertige APK gegen Repo-Fingerprint

### Release-Changelog & Website
- **Changelog:** Eintrag vor Commit schreiben (wichtige Nutzer-/Tech-Änderungen)
- **Website-Sync:** Automatisch von `main`-Branch → https://wartezeiten-app.tutorialfynn.workers.dev/
- **In-App-Updates:** `website/release.json` mit `versionCode`, `apkUrl`, `sha256`, `releaseNotesLocalized`
- **Download-Automation:** `website/download-from-github.ps1` lädt neueste APK + SHA-256
- **In-App-Update-Installation:** `update/ApkDownloader.kt` + `update/ApkInstaller.kt`

### GitHub-Actions-Fallstrick
- Lange Python-/Shell-Scripte nicht inline per Heredoc in `.github/workflows/*.yml` → YAML-Einrückung beschädigt Abschlussmarker
- Scripts stattdessen in `.github/scripts/` auslagern und aufrufen

## 6. Website & Browser-Version

### Live-Wartezeiten auf Website (2026-06-28)
- **Seite:** `website/wartezeiten.html` (+ `app.css`, `wartezeiten.js`)
- **Features:** Parksuche, aktuelle Wartezeiten/Öffnungszeiten, Statistik mit Diagramm
- **Architektur:** Statische HTML + Client-seitiges Hash-Routing (`#park=parkKey`), keine Abhängigkeiten

### Worker-API-Endpunkte
- `GET /api/parks?lang=de|en` – Parkliste (1h Cache)
- `GET /api/parks/{parkKey}/live?lang=de|en` – Wartezeiten/Öffnungszeiten/Auslastung (30s Cache)
- Beide proxyen direkt wartezeiten.APP-API (keine D1/KV benötigt)

### Website-Theming (Dark Mode Standard)
- **Default ohne JS:** Dark Mode in `:root` von `styles.css`; Light Mode überschreibt via `:root[data-theme='light']`
- **Umschalt-JS:** `website/theme.js` in `<head>` **vor** Stylesheets, setzt `data-theme` auf `<html>` sync aus `localStorage`
- **Farben an App angeglichen:** Material3 Light-/DarkColorScheme Hex-Werte
- **Wartezeit-Farben theme-invariant:** `--wait-low/#4CAF50`, `--wait-mid/#FFB300`, `--wait-high/#F44336` (wie App)
- **Footer theme-invariant:** Feste `--footer-bg`/`--footer-text` statt `--text-primary`

### queue-times.com-Fallback (2026-07-01)
**Problem:** Wartezeiten.app-Ausfälle (z.B. `/v1/parks` HTTP 500)

**Lösung:**
- **Kuratierte Tabelle:** `worker/src/fallbackParks.js` + `app/.../QueueTimesParkMapping.kt` (ca. 40 Parks)
- **Worker `/api/parks`:** `buildFallbackParksResponse()` bei Fehler → HTTP 200 + `degraded: true`, `source: "queue-times.com"`
- **`collectParkSnapshot()`:** `fetchQueueTimesWaitingItems()` nutzt `queue-times.com/parks/{id}/queue_times.json`, mappt auf wartezeiten-Form
- **Attraktions-IDs:** Mit `qt-` Prefix um UUID-Kollidionen zu vermeiden
- **D1-Statistik:** `qt-` IDs erscheinen als separate Einträge (akzeptierter Trade-off)
- **Push/Watchlist:** Park-Alarme laufen weiter; Attraktions-Alarme laden Ziel nicht → kein Alarm
- **Website-Banner:** `#parksDegradedBanner`, `#fallbackSourceBanner` bei `degraded`/`dataSource === "queue-times.com"`
- **Android-Fallback:** Separate `QueueTimesApiService` nur für App-Live-Anzeige

## 7. Attribution & API-Nutzungsbedingungen (PFLICHT)

**Wartezeiten.APP API Nutzung erfordert sichtbaren anklickbaren Link zu https://www.wartezeiten.app**

### Implementierung
- `ParkListScreen.kt` & `WaitingTimesScreen.kt` – `AttributionFooter` in `bottomBar`
- Text: "Daten bereitgestellt von wartezeiten.app" (anklickbar, unterstrichen)
- **DARF NICHT entfernt werden**

### Attribution & System-Navigationsleiste (Fallstrick)
- **Als `bottomBar`-Slot:** `AttributionFooter` (= Banner + `Modifier.navigationBarsPadding()`) verwenden (nicht nur Banner)
- **Als Inhalt:** Banner selbst ok (Scaffold-`innerPadding` schützt vor System-Buttons)

## 8. Verifikations-Richtlinie

Nach jeder Änderung **MUSS** geprüft werden:
- **UI-Änderungen:** Visuell oder via UI-Inspektion
- **Logik-Änderungen:** Tests oder manuelle Ausführung
- **Misserfolge:** Dokumentieren und beheben
