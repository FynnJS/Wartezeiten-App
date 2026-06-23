# Wartezeiten App - Projekt-Dokumentation für KI-Agents

Diese Datei dient als Grundlage für alle KIs, die an diesem Projekt arbeiten. Sie enthält die wichtigsten Fakten zur Architektur, den verwendeten Technologien und der API-Dokumentation.

## Projektübersicht
Die **Wartezeiten App** ist eine Android-Anwendung zur Anzeige von Wartezeiten in Freizeitparks weltweit. Sie basiert auf der API von [Wartezeiten.APP](https://wartezeiten.app).

### Tech-Stack
- **Sprache:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architektur:** Clean Architecture mit MVVM
- **Dependency Injection:** Hilt
- **Datenhaltung:** Room (Lokal), Retrofit (Netzwerk)
- **Nebenläufigkeit:** Coroutines & Flow

## Architektur & Paketstruktur
Das Projekt ist in folgende Layer unterteilt:

- `core`: Übergreifende Hilfsklassen (Netzwerk-Handling, Dispatcher).
- `data`: Implementierung der Repositories, Datenquellen (Local & Remote), DTOs, Entities und Mapper.
- `domain`: Geschäftslogik (Modelle, Repository-Interfaces, Use-Cases).
- `di`: Hilt-Module für die Dependency Injection.
- `ui`: UI-Komponenten, aufgeteilt nach Features (Parks, WaitingTimes) und Theme.

## API Dokumentation (Wartezeiten.APP - v1.6.0)
Die App nutzt die Wartezeiten.APP API (OAS 3.0). Alle Endpunkte liefern JSON.

### Basis-URL
Die Basis-URL wird über das `NetworkModule` konfiguriert (aktuell `https://api.wartezeiten.app`).

### Endpunkte
Die vollständige API-Dokumentation findest du unter [https://api.wartezeiten.app](https://api.wartezeiten.app).

#### 1. Liste der Freizeitparks
`GET /v1/parks`
- **Header:**
    - `language` (erforderlich): `de` oder `en`
- **Response:** Liste von Park-Objekten (`id`, `uuid`, `name`, `land`).
- **Hinweis:** Caching für 24 Stunden.

#### 2. Öffnungszeiten
`GET /v1/openingtimes`
- **Header:**
    - `park` (erforderlich): Park ID oder UUID
- **Response:** Objekt mit `opened_today` (boolean), `open_from` und `closed_from` (ISO 8601).
- **Hinweis:** Caching für 24 Stunden.

#### 3. Aktuelle Wartezeiten
`GET /v1/waitingtimes`
- **Header:**
    - `park` (erforderlich): Park ID oder UUID
    - `language` (erforderlich): `de` oder `en`
- **Response:** Liste von Attraktionen mit `name`, `waitingtime` (Minuten), `status` (z.B. `opened`, `closed`), `uuid`, `datetime`.
- **Hinweis:** Caching für 5 Minuten.

#### 4. Crowd Level (Besucheraufkommen)
`GET /v1/crowdlevel`
- **Header:**
    - `park` (erforderlich): Park ID oder UUID
- **Response:** Objekt mit `crowd_level` (float als String, z.B. "12,43") und `timestamp`.
- **Hinweis:** Caching für 5-10 Minuten.

## Wichtige Implementierungsdetails
- **Offline-First:** Die App nutzt Room zur lokalen Speicherung. Daten werden beim Aktualisieren in die Datenbank geschrieben und via Flow an die UI gestreamt.
- **Error Handling:** Zentrales Error Handling über die `ApiResult` Sealed Interface.
- **Projektwissen aktuell halten:** Relevante Änderungen, Architekturentscheidungen, Integrationsdetails oder Fallstricke, die für zukünftige Programmiererinnen/Programmierer oder programmierende KI-Agents wichtig sind, sollen in dieser `Agents.md` ergänzt werden.
- **Visuelle & technische Qualität:** Bei jeder Änderung ist auf eine technisch robuste, reaktionsschnelle und visuell ansprechende Umsetzung zu achten. UI-Änderungen sollen klar, modern, konsistent mit Material 3 und im Nutzungskontext attraktiv wirken.
- **Header-Parameter:** WICHTIG - Die Parameter `park` und `language` werden bei den Detail-Endpunkten als **Header** übergeben, nicht als Query-Parameter.
- **HTTP-Cache für Header-Endpunkte:** Da die Detail-Endpunkte Parks und Sprache über Header unterscheiden, muss der OkHttp-Cache per `Vary` nach diesen Headern trennen. `/v1/openingtimes` und `/v1/crowdlevel` benötigen `Vary: park`; `/v1/waitingtimes` benötigt `Vary: park, language`. Ohne diese Trennung können Daten eines Parks bei einem anderen Park angezeigt werden.
- **Öffnungszeiten-Cache:** `/v1/openingtimes` darf trotz des API-Hinweises zum 24-Stunden-Caching clientseitig höchstens 30 Minuten gecacht werden. Ein gleitender 24-Stunden-Cache reicht über den Tageswechsel und kann dadurch gestrige Zeitfenster als heutigen Status anzeigen.
- **Öffnungszeiten-Mapping:** `opened_today` ist ein Boolean und die maßgebliche Quelle dafür, ob ein Park heute geöffnet ist. Der Wert darf nicht durch `status`-Strings überschrieben oder nur aus ihnen abgeleitet werden, wenn `opened_today` vorhanden ist.
- **Geschlossene Parks & Crowd Level:** Wenn `opened_today=false`, darf ein vorhandener `crowdlevel` nicht als aktuelle Auslastung dargestellt werden. Die UI soll dann sinngemäß "Heute geschlossen" anzeigen.
- **Geschlossene Parks & Attraktionsdaten:** Attraktionslisten sollen auch sichtbar bleiben, wenn ein Park aktuell geschlossen ist, damit Nutzerinnen/Nutzer den Attraktionskatalog sehen können. Öffnungszeiten bleiben aber maßgeblich für Live-Aussagen: Bei geschlossenem oder noch nicht geöffnetem Park dürfen Crowd-Level, offene Attraktionen, Empfehlungen und Statistiken nicht als aktuelle Auslastung bzw. echte Tagesmessung dargestellt werden.
- **Auto-Update:** Die App aktualisiert sich automatisch jede Minute (via `viewModelScope` und `delay`).
- **Refresh Feedback:** Nur manuelle Aktualisierungen geben eine visuelle Rückmeldung via Snackbar ("Wartezeiten aktualisiert" bzw. "Parks aktualisiert"). Initiales Laden und Auto-Refresh dürfen keine Snackbar anzeigen.
- **Zeit-Anzeige:** In den Park-Details wird sowohl die aktuelle Uhrzeit vor Ort als auch der Zeitpunkt der letzten erfolgreichen API-Aktualisierung angezeigt. Die Ortszeit wird aus API-Zeitstempeln bzw. Öffnungszeiten-Offsets abgeleitet; die Gerätezeit ist nur Fallback.
- **Filter & Sortierung:** Parks können nach Land und Status (Nur offen) gefiltert werden. Attraktionen können nach Wartezeit (Auf/Absteigend) und Name sortiert sowie nach Status gefiltert werden. Standardmäßig werden Attraktionen nach der höchsten Wartezeit sortiert.
- **Offen-Filter & Snapshot-Alter:** Parklisten- und Vergleichsfilter dürfen Öffnungsstatus-Snapshots nur als aktuell behandeln, wenn sie frisch sind. Der aktuelle Grenzwert liegt bei 30 Minuten; ältere Snapshots dürfen nicht mehr dazu führen, dass Parks als geöffnet gefiltert oder bewertet werden.
- **Keine Logos:** Es werden keine Bilder oder großen Icons (z.B. Achterbahn-Logos) als Park-Logos verwendet. Flaggen werden dezent im Text-Kontext angezeigt.
- **Flaggen:** Länderflaggen sollen robust aus ISO-Country-Codes als Regional-Indicator-Zeichen erzeugt werden. Besonders die USA muss korrekt als `US` -> 🇺🇸 gemappt werden; fehleranfällige Emoji-Literals vermeiden.
- **Watchlist-Benachrichtigungen:** Watchlist-Alarme haben zwei Pfade. Der lokale Fallback läuft über WorkManager (`NotificationWorker`), startet bei neuen Alarmen einen schnellen Einmal-Check und prüft periodisch alle 30 Minuten. Optional nutzt die App Firebase Cloud Messaging: `PushRegistrationManager` synchronisiert FCM-Token und lokale Watchlist an den Cloudflare Worker (`/push/register`, `/push/watchlist`), der Alerts in D1 (`push_installations`, `push_watchlist_alerts`) speichert und über einen separaten `* * * * *`-Cron serverseitig minütlich prüft. Für Android müssen `FIREBASE_APPLICATION_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID` und `FIREBASE_GCM_SENDER_ID` als Gradle-Properties gesetzt sein; für den Worker werden die Secrets `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL` und `FCM_PRIVATE_KEY` benötigt. Ohne diese Konfiguration bleibt Push deaktiviert und WorkManager übernimmt. Die Alarmtypen "Alle Änderungen" für Parks und Attraktionen vergleichen den zuletzt gesehenen Zustand und benachrichtigen erst bei einer echten Änderung; die Benachrichtigung öffnet die Parkseite.
- **Erweiterte Alarmregeln:** Neue Watchlist-Alarme können einmalig sein, nur während der Parköffnung gelten, eine Ruhezeit von 22:00 bis 08:00 respektieren und einen Mindestabstand von 15 bis 120 Minuten nutzen. Die Regeln gelten identisch für WorkManager und Standby-Push. Einmalige Alarme werden nach erfolgreicher Zustellung pausiert; die Watchlist zeigt pausierte Alarme und den letzten tatsächlichen Auslösezeitpunkt an. Der parkweite Alarmtyp `DAILY_SUMMARY` meldet einmal täglich gegen 18 Uhr Ortszeit des Parks Öffnungsstatus, Auslastung und offene Attraktionen.
- **Push-Diagnose & Releases:** Die Watchlist zeigt an, ob echter Standby-Push aktiv, deaktiviert oder fehlerhaft ist, und bietet eine lokale Testbenachrichtigung. Release-Builds müssen bei fehlenden Firebase-Android-Werten sowohl lokal in Gradle als auch in GitHub Actions abbrechen; ein still ohne FCM gebautes Release ist nicht zulässig. WorkManager ist nur ein verzögerbarer Fallback und keine Garantie für minutengenaue Zustellung im Doze-Modus.
- **Lokale Firebase-Konfiguration:** Android-Builds lesen die vier Firebase-Werte vorrangig aus Gradle-Properties und ersatzweise strukturiert aus `app/google-services.json` bzw. `google-services.json` im Projektstamm. `scripts/configure-push.ps1` kopiert die geprüfte Datei automatisch nach `app/`; die Datei bleibt per `.gitignore` lokal. Die Testbenachrichtigung fordert auf Android 13+ bei fehlender Berechtigung über den Systemdialog `POST_NOTIFICATIONS` an und wird nach Zustimmung direkt angezeigt.
- **Push-Health:** `/push/status` liefert ausschließlich die Booleans `d1Configured`, `fcmConfigured` und `pushReady`. Die App darf Standby-Push erst als aktiv melden, wenn dieser Endpunkt `pushReady=true` liefert und Token- sowie Watchlist-Synchronisierung erfolgreich waren.
- **Watchlist-API-Fehler:** Parkstatus- und Crowd-Level-Alarme brauchen verlässliche Öffnungszeiten und werden bei unbekanntem OpeningTimes-Status übersprungen. Attraktionsalarme dürfen aber weiter anhand erfolgreicher `/v1/waitingtimes`-Daten laufen, wenn der Öffnungszeiten-Call temporär fehlt.
- **Zentrale Statistik-Snapshots:** Der Cloudflare Worker sammelt App-Daten per Cron in drei versetzten 5-Minuten-Shards. Neue Attraktions-Messpunkte werden primär in Cloudflare D1 (`APP_DATA_DB`, Datenbank `wartezeiten-app-data`) gespeichert: ein Snapshot-JSON pro Park/Zeitpunkt in `attraction_history_snapshots`, Tagesmetadaten in `attraction_history_days`. Die öffentlichen Statistik-Endpunkte behalten ihren JSON-Vertrag; alte KV-Tagesdateien bleiben als Legacy-Fallback erhalten und werden mit D1-Daten zusammengeführt. `latest.json` wird im Shard-Cron nicht geschrieben. `trend-history.json` wird ebenfalls nicht mehr als eigene Datei im Shard-Cron geschrieben, sondern beim Abruf aus D1-Attraktionssnapshots plus Legacy-KV rekonstruiert; Park-Messpunkte nutzen dabei die aus Wartezeiten geschätzte Auslastung. Im Shard-Modus werden für Statistiken nur Öffnungszeiten und Wartezeiten abgefragt, nicht Crowd-Level. Globale Marker sind über `/app-data/global-markers/latest.json` abrufbar und werden aus D1 plus Legacy-KV abgeleitet. Ohne D1-Binding fällt der Worker weiterhin auf die bisherige KV-Schreiblogik zurück.
- **D1-Statistik-Write-Fallstrick:** Cron-Läufe müssen D1-Snapshots parkweise bzw. frühzeitig schreiben. Wenn alle Snapshots erst am Ende eines langen Shards geschrieben werden, kann ein Timeout oder später API-Fehler den kompletten heutigen Statistik-/Marker-Lauf verlieren.
- **Offene Parks & zentrale Marker:** Die Parkliste darf für den "Nur offen"-Filter nicht mehr `/app-data/latest.json` als aktuelle Quelle verwenden, weil diese Datei im Shard-Cron veralten kann. Aktuelle zentrale Parkzustände kommen aus `/app-data/global-markers/latest.json`; wenn dieser Marker-Endpunkt leer oder veraltet ist, muss die App auf den lokalen Öffnungs-/Wartezeiten-Scan zurückfallen.
- **Parkbezogener Trendabruf:** Parkdetailseiten laden `/app-data/trend-history.json?parkKey={parkKey}`. Der Filter muss bereits in der D1-Abfrage angewendet werden; das Rekonstruieren aller Parks in einer Anfrage überschreitet bei wachsender Historie das Cloudflare-Worker-Ressourcenlimit.
- **Statistik ohne heutige Messpunkte:** Wenn für den ausgewählten heutigen Tag noch keine zentrale Statistikdatei bzw. keine echten Öffnungs-Messpunkte existieren (z.B. morgens vor Parköffnung oder bei geschlossenem Park), soll die Statistikansicht dies ausdrücklich anzeigen und nicht automatisch so wirken, als seien Daten verloren gegangen. Heutige Parkdetail-Statistiken müssen Snapshots nach der aktuellen Parkzeit ausfiltern; Tagesdateien oder Fallbacks dürfen keine zukünftigen Messpunkte im Graphen anzeigen.
- **Statistik-Datumswahl:** Statistikseiten dürfen initial nur dann "heute" auswählen, wenn für heute zentrale Daten im Index stehen oder ein lokaler Live-Fallback vorhanden ist. Fehlt der heutige Tag im Index, muss standardmäßig `latestDate` verwendet werden, damit bestehende Attraktionsverläufe nicht als leer erscheinen.
- **Multi-Park-Vergleich:** Es gibt keine Park-Ratings mehr. Stattdessen nutzt `ui/compare` einen datenbasierten Vergleichsscreen für Parks. Die Kennzahlen werden aus lokal gespeicherten aktuellen Wartezeiten berechnet und bei Auswahl/Refresh per bestehendem `refreshParkDetail` aktualisiert. Der Vergleich bleibt read-only, erzeugt keine Cloudflare-KV-Writes und soll Besuchern eine schnelle Entscheidung über Wartezeitqualität, offene Attraktionen und Datenstand ermöglichen. Die Parkauswahl muss suchbar und klar geführt bleiben: ausgewählte Parks separat anzeigen, Treffer nach Parkname/Land filtern, bis zu vier Parks erlauben und bei weniger als zwei Parks einen verständlichen Leerzustand anzeigen. Vergleichskarten sind anklickbar und führen zur normalen Park-Übersicht.
- **Offline-/Such-/Share-UX:** Die Parkliste zeigt bei Netzwerkfehlern mit vorhandenem Cache einen prominenten Offline-Banner inklusive Alter der letzten Parkdaten. Park-Suchtext und die letzten fünf bestätigten Suchen werden in `PreferencesDataSource` gespeichert; Suchhistorie wird beim Öffnen eines Park- oder Attraktionstreffers aktualisiert. Die Statistikansicht kann den aktuell sichtbaren Screen als PNG über Android Sharesheet teilen; dafür ist ein `FileProvider` mit Cache-Pfad `shared_statistics/` im Manifest registriert.
- **Start-/Detail-UX:** Die Parkliste zeigt zuletzt angesehene Parks sowie ein Favoriten-Dashboard mit Öffnungs-/Wartezeit-Kennzahlen. Zuletzt angesehene Parks werden auch beim Öffnen einer Detailseite per Deep-Link/Notification gespeichert. Die Parksuche unterstützt lokale Aliasnamen für häufige Parks (z.B. EP/Europa Park, DLP/Disneyland). Parkdetails zeigen bei Cache-Nutzung einen Offline-Banner und eine Datenqualitätskarte; die aktuelle Parkübersicht kann als Text geteilt werden.
- **Startbildschirm-Widget:** Das Lieblingspark-Widget nutzt Jetpack Glance (`ui/widget`) mit einer normalen Compose-Konfigurations-Activity. Die Widget-Instanz speichert `park_key` und bis zu drei Attraktions-IDs im Glance-Preferences-State sowie als Fallback pro AppWidget-ID in `ParkWidgetConfigStore`, aktualisiert beim Widget-Update per bestehendem `WartezeitenRepository.refreshParkDetail` und öffnet den Park per Deep-Link `wartezeiten://parks/{parkKey}`. Durchschnitts- und Maximalwartezeit werden nur angezeigt, wenn `isParkCurrentlyOpen` den Park aktuell als geöffnet bewertet; bei geschlossenen oder unbekannten Öffnungszeiten dürfen keine alten Werte als Live-Metriken erscheinen. Ist ein Park konfiguriert, aber noch kein Detaildatensatz verfügbar, muss das Widget einen Ladezustand anzeigen und nicht in den unkonfigurierten Zustand zurückfallen.
- **Widget-Aktualisierung:** Das Glance-Widget darf sich nicht nur auf `android:updatePeriodMillis` verlassen. `ParkWidgetUpdateScheduler` plant zusätzlich einen WorkManager-Refresh alle 30 Minuten, und der manuelle "Neu"-Klick im Widget rendert die aktuelle Instanz sofort neu und stößt einen einmaligen Refresh für weitere Instanzen an.
- **Parkdetail-Statistik:** Parkdetailseiten zeigen anstelle eines Auslastungs-Trend-Dashboards die zentrale Parkstatistik des aktuellen bzw. neuesten verfügbaren Tages mit durchschnittlicher Wartezeit, Min/Max, letztem Wert, offenen Attraktionen, Messpunktzahl und Wartezeiten-Graph. Die aktuelle Auslastung bleibt dort nur als einzelne Textzeile sichtbar.
- **Parkdetail-Statistik & veraltete zentrale Daten:** Die Parkdetailseite darf ältere zentrale Statistik-Tage nicht als heutige Parkstatistik darstellen. Fehlt der heutige Tag im Statistikindex, muss die Detailkarte ausdrücklich melden, dass für heute noch keine zentralen Messpunkte verfügbar sind; vergangene Tage dürfen dort nur als Hintergrund für konservative Vergleiche genutzt werden.
- **Jetzt-oder-später-Empfehlung:** Geöffnete Attraktionen erhalten auf der Parkdetailseite eine konservative Einordnung der aktuellen Wartezeit. Verglichen werden bis zu sieben vergangene Statistik-Tage im Zeitfenster um die lokale Parkzeit. Eine Empfehlung erscheint erst ab mindestens drei Vergleichstagen; mögliche spätere Zeitpunkte werden nur aus historischen Messwerten der kommenden zwei Stunden abgeleitet und als Schätzung formuliert.
- **Wartezeit-Vergleichslabels:** Vergleichslabels dürfen nur aus repräsentativen historischen Messfenstern entstehen. Tage bzw. Snapshots mit zu wenigen offenen Attraktionen sind auszufiltern; wenn die aktuelle Wartezeit stark von der historischen typischen Wartezeit abweicht und keine belastbare "später besser"-Aussage möglich ist, soll kein harmloses "üblich"-Label angezeigt werden.
- **Cache-Verwaltung:** In den Einstellungen kann der lokale API-/Statistik-Cache geleert werden. Favoriten, Watchlist-Alarme und Einstellungen bleiben erhalten; nicht favorisierte Parkstammdaten, Detaildaten und Snapshot-Historien werden gelöscht und beim nächsten Refresh neu geladen.
- **Attraktions-Details & Notizen:** Attraktionszeilen öffnen über die bestehende Detailroute `parks/{parkKey}?attractionId={attractionId}` eine Detailkarte mit Status, heutigem Verlauf, historischer 1-3-Stunden-Prognose, Watchlist-Shortcut, Deep-Link-Share und persönlicher Notiz. Notizen liegen lokal in Room (`attraction_notes`) und dürfen bei Cache-Leerung nicht entfernt werden.
- **Wartezeit-Prognose:** Die Prognose erweitert `AttractionWaitAdvice` um Timeline-Punkte aus den bereits geladenen D1-`AttractionHistoryDay`-Snapshots. Aussagen bleiben konservativ und erscheinen nur bei mindestens drei vergleichbaren historischen Tagen.
- **Alias-Suche:** Wartbare Park-Aliase liegen zusätzlich in `app/src/main/assets/park_aliases.csv`. Neue Tippvarianten oder internationale Schreibweisen sollen dort ergänzt werden; die alte kleine Code-Heuristik bleibt nur Fallback.

## Verifizierungs-Richtlinie (WICHTIG)
Nach jeder Änderung am Programm **MUSS** geprüft werden, ob die Änderung erfolgreich war und wie erwartet funktioniert. 
- Bei UI-Änderungen (z.B. Logo, Layout) muss die korrekte Anzeige visuell oder via UI-Inspektion verifiziert werden.
- Bei Logik-Änderungen müssen betroffene Funktionen (z.B. API-Calls, Datenbank-Operationen) durch Tests oder manuelle Ausführung bestätigt werden.
- Misserfolge oder unerwartetes Verhalten müssen dokumentiert und behoben werden.

## Status der App
- Alle Build-Fehler wurden behoben.
- `android.useAndroidX=true` ist in `gradle.properties` gesetzt.
- Die API-Integration in `WartezeitenApiService` wurde auf Header-Parameter korrigiert.
- Release-APK-Updates müssen mit demselben Keystore signiert werden wie die zuvor installierte Version, um Paketkonflikte zu vermeiden. `app/build.gradle.kts` unterstützt jetzt eine `keystore.properties`-basierte Release-Signaturkonfiguration; ohne eigene Keystore-Datei wird lokal der Android-Debug-Keystore verwendet, damit Entwicklungs-Updates installierbar bleiben.
- **Dauerhafter Release-Keystore:** GitHub-Releases verwenden ausschließlich den mit `scripts/configure-release-signing.ps1` erzeugten und als Repository-Secrets hinterlegten Keystore. Zusätzlich wird `RELEASE_CERT_SHA256` nach jedem Build gegen die fertige APK geprüft. Der lokale Sicherungsordner außerhalb des Repositories muss dauerhaft aufbewahrt und extern gesichert werden; ohne privaten Schlüssel sind signaturkompatible Updates nicht wiederherstellbar.

## Website & Release-Deployment

### Release-Changelog (PFLICHT)
Bei jedem neuen Release muss vor dem Commit ein Changelog-Eintrag geschrieben bzw. aktualisiert werden. Der Eintrag soll die wichtigsten Nutzer- und Technikänderungen der Version knapp zusammenfassen.
Bei jedem Release muss zusätzlich geprüft werden, ob die Projekt-README neue Informationen zur Version, Installation, Website oder Release-/Download-Automation benötigt. Relevante Änderungen sind vor dem Commit einzutragen.

### Live Website
- **URL:** https://wartezeiten-app.tutorialfynn.workers.dev/
- **Hosting:** Cloudflare Workers
- **Sync:** Website wird automatisch von GitHub `main`-Branch deployed

### GitHub Release Integration
- **Download-Automation:** `website/download-from-github.ps1` (PowerShell)
- **Dokumentation:** `website/GITHUB-DOWNLOAD.md`
- **Funktionalität:**
  - Lädt neueste APK-Release von GitHub herunter
  - Berechnet SHA-256 Hash automatisch
  - Aktualisiert `release.json` mit Versionsinformationen
  - Funktioniert mit öffentlichen Repositories (kein Token nötig)
  - Unterstützt optionalen Token für private Repos (aktuell nicht nötig)
- **Repository:** FynnJS/Wartezeiten-App (public)
- **Status:** ✅ Seit 2026-06-01 integriert und getestet
- **GitHub-Actions-Fallstrick:** Lange Python-/Shell-Scripte nicht inline per Heredoc in `.github/workflows/*.yml` pflegen. YAML-Einrückung kann den Heredoc-Abschlussmarker beschädigen und den Build im Metadata-Step scheitern lassen. Release-Logik deshalb in versionierte Dateien unter `.github/scripts/` auslagern und im Workflow nur aufrufen.
- **Release-APK-Pfad:** Die Pipeline darf nicht starr nur `app/build/outputs/apk/release/app-release.apk` erwarten. Bei Änderungen an Android Gradle Plugin, Build-Varianten oder Output-Namen soll das Script die `*.apk` im Release-Output-Verzeichnis ermitteln und bei fehlenden Dateien mit einer klaren Fehlermeldung abbrechen.
- **Pflicht-Updates & APK-Signatur:** Erkennt die App in `website/release.json` einen höheren `versionCode`, blockiert sie die Nutzung mit einem nicht wegklickbaren Update-Screen bis zur Installation der neuen APK. GitHub-Release-APKs müssen mit einem stabilen Release-Keystore signiert werden; die Pipeline erwartet `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` und `RELEASE_KEY_PASSWORD` und bricht bei Release-Events ohne diese Secrets ab, damit Android-Update-Konflikte durch abweichende Signaturen vermieden werden.

### Website-Updates (2026-06-01)
- Website synchronisiert mit Live-Version
- Modernere HTML/CSS-Struktur
- Verbesserte Release-Info-Anzeige
- Copy-to-Clipboard für SHA-256 Hash
- GitHub Actions CI/CD vorbereitet (optional)

## API-Nutzungsbedingungen & Attribution (PFLICHT)

Die Nutzung der Wartezeiten.APP API setzt voraus, dass ein **sichtbarer und anklickbarer Link** zur Webseite [https://www.wartezeiten.app](https://www.wartezeiten.app) an einer **prominenten Stelle** in der App platziert wird.

### Aktuelle Implementierung
Ein Attribution-Footer wurde in beiden Hauptscreens ergänzt:
- `ParkListScreen.kt` – als `bottomBar` im `Scaffold`
- `WaitingTimesScreen.kt` – als `bottomBar` im `Scaffold`

Der Footer zeigt den Text **„Daten bereitgestellt von wartezeiten.app"** mit einem anklickbaren, unterstrichenen Link, der den Browser mit `https://www.wartezeiten.app` öffnet.

> **Wichtig:** Dieser Link darf **nicht entfernt** werden. Bei zukünftigen Umstrukturierungen der UI muss die Attribution weiterhin prominent sichtbar und anklickbar bleiben.
