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
- **Header-Parameter:** WICHTIG - Die Parameter `park` und `language` werden bei den Detail-Endpunkten als **Header** übergeben, nicht als Query-Parameter.
- **HTTP-Cache für Header-Endpunkte:** Da die Detail-Endpunkte Parks und Sprache über Header unterscheiden, muss der OkHttp-Cache per `Vary` nach diesen Headern trennen. `/v1/openingtimes` und `/v1/crowdlevel` benötigen `Vary: park`; `/v1/waitingtimes` benötigt `Vary: park, language`. Ohne diese Trennung können Daten eines Parks bei einem anderen Park angezeigt werden.
- **Öffnungszeiten-Mapping:** `opened_today` ist ein Boolean und die maßgebliche Quelle dafür, ob ein Park heute geöffnet ist. Der Wert darf nicht durch `status`-Strings überschrieben oder nur aus ihnen abgeleitet werden, wenn `opened_today` vorhanden ist.
- **Geschlossene Parks & Crowd Level:** Wenn `opened_today=false`, darf ein vorhandener `crowdlevel` nicht als aktuelle Auslastung dargestellt werden. Die UI soll dann sinngemäß "Heute geschlossen" anzeigen.
- **Auto-Update:** Die App aktualisiert sich automatisch jede Minute (via `viewModelScope` und `delay`).
- **Refresh Feedback:** Nur manuelle Aktualisierungen geben eine visuelle Rückmeldung via Snackbar ("Wartezeiten aktualisiert" bzw. "Parks aktualisiert"). Initiales Laden und Auto-Refresh dürfen keine Snackbar anzeigen.
- **Zeit-Anzeige:** In den Park-Details wird sowohl die aktuelle Uhrzeit vor Ort als auch der Zeitpunkt der letzten erfolgreichen API-Aktualisierung angezeigt. Die Ortszeit wird aus API-Zeitstempeln bzw. Öffnungszeiten-Offsets abgeleitet; die Gerätezeit ist nur Fallback.
- **Filter & Sortierung:** Parks können nach Land und Status (Nur offen) gefiltert werden. Attraktionen können nach Wartezeit (Auf/Absteigend) und Name sortiert sowie nach Status gefiltert werden. Standardmäßig werden Attraktionen nach der höchsten Wartezeit sortiert.
- **Keine Logos:** Es werden keine Bilder oder großen Icons (z.B. Achterbahn-Logos) als Park-Logos verwendet. Flaggen werden dezent im Text-Kontext angezeigt.
- **Flaggen:** Länderflaggen sollen robust aus ISO-Country-Codes als Regional-Indicator-Zeichen erzeugt werden. Besonders die USA muss korrekt als `US` -> 🇺🇸 gemappt werden; fehleranfällige Emoji-Literals vermeiden.

## Verifizierungs-Richtlinie (WICHTIG)
Nach jeder Änderung am Programm **MUSS** geprüft werden, ob die Änderung erfolgreich war und wie erwartet funktioniert. 
- Bei UI-Änderungen (z.B. Logo, Layout) muss die korrekte Anzeige visuell oder via UI-Inspektion verifiziert werden.
- Bei Logik-Änderungen müssen betroffene Funktionen (z.B. API-Calls, Datenbank-Operationen) durch Tests oder manuelle Ausführung bestätigt werden.
- Misserfolge oder unerwartetes Verhalten müssen dokumentiert und behoben werden.

## Status der App
- Alle Build-Fehler wurden behoben.
- `android.useAndroidX=true` ist in `gradle.properties` gesetzt.
- Die API-Integration in `WartezeitenApiService` wurde auf Header-Parameter korrigiert.

## Website & Release-Deployment

### Release-Changelog (PFLICHT)
Bei jedem neuen Release muss vor dem Commit ein Changelog-Eintrag geschrieben bzw. aktualisiert werden. Der Eintrag soll die wichtigsten Nutzer- und Technikänderungen der Version knapp zusammenfassen.

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
