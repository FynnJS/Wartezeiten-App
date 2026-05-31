# Wartezeiten App - Projekt-Dokumentation für KI-Agents

Diese Datei dient als Grundlage für alle KIs, die an diesem Projekt arbeiten. Sie enthält die wichtigsten Fakten zur Architektur, den verwendeten Technologien und der API-Dokumentation.

## Projektübersicht
Die **Freizeitpark Wartezeiten App** ist eine Android-Anwendung zur Anzeige von Wartezeiten in Freizeitparks weltweit. Sie basiert auf der API von [Wartezeiten.APP](https://wartezeiten.app).

**Wichtig:** Diese App ist eine **inoffizielle Drittanwendung** und wird **nicht von den Entwicklern von Wartezeiten.APP** entwickelt. Sie nutzt nur deren öffentliche API.

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

## Cloudflare Workers Deployment

Die Website wird via Cloudflare Workers deployed: **https://wartezeiten-app.tutorialfynn.workers.dev**

### Architektur
- **Worker-Handler:** `src/index.ts` – Routet APK-Downloads zu GitHub Releases weiter
- **Static Assets:** `website/` – HTML, CSS, JavaScript, Metadaten (release.json)
- **APK-Speicherung:** GitHub Releases (privates Repo `FynnJS/Wartezeiten-App`)
- **TypeScript:** Wrangler-Projekt mit TypeScript-Support

### Download-Flow
1. Benutzer klickt auf "APK herunterladen" Button
2. JavaScript aktualisiert Links basierend auf `release.json`
3. Request geht zu `/releases/freizeitpark-wartezeiten-<VERSION>.apk`
4. Cloudflare Worker leitet um zu GitHub Releases:
   ```
   https://github.com/FynnJS/Wartezeiten-App/releases/download/wartezeiten-app-1.0/freizeitpark-wartezeiten-1.0.apk
   ```
5. GitHub liefert die APK aus dem Release

### Website Features
- **Professionelles Design:** Moderne UI mit Gradient-Header, Feature-Cards, expandable FAQ
- **SHA-256 Verifizierung:** Benutzer können den APK-Hash kopieren und lokal verifizieren
- **Responsive Layout:** Mobile-first Design, optimiert für alle Geräte
- **Release-Verwaltung:** Dynamisches Laden aus `release.json`
- **Error-Handling:** Graceful fallbacks wenn Daten nicht verfügbar
- **Disclaimer:** Deutliche Kennzeichnung, dass die App inoffiziell ist

### Deployment
```bash
npm install           # Dependencies installieren
npx wrangler deploy   # Zu Cloudflare deployen
```

### Wichtige Dateien
- `wrangler.jsonc` – Wrangler-Konfiguration mit `main: "src/index.ts"`
- `src/index.ts` – Worker-Handler für GitHub-Umleitungen
- `website/index.html` – Hauptseite mit modernem Layout
- `website/styles.css` – Umfassendes CSS mit CSS-Variablen
- `website/script.js` – JavaScript für Release-Daten-Laden und SHA-256-Anzeige
- `website/release.json` – Release-Metadaten (versionName, releaseDate, sha256, etc.)
- `package.json` – npm-Projekt-Konfiguration

### Release-Verwaltung
1. Neue APK bauen und auf GitHub Releases unter Tag `wartezeiten-app-X.Y` hochladen
2. `release.json` aktualisieren mit:
   - `versionName` – Version (z.B. "1.0")
   - `releaseDate` – ISO-Datum (z.B. "2026-05-31")
   - `apkUrl` – Pfad zur APK (z.B. "./releases/freizeitpark-wartezeiten-1.0.apk")
   - `sha256` – SHA-256 Hash der APK
   - `apkSize` – Größe in MB (optional)
   - `releaseNotes` – Array mit Release-Notes
3. `npx wrangler deploy` ausführen

## Git Workflow & Commits

**Wichtig:** Commits und Pushes dürfen **NUR nach Verifikation und Tests** durchgeführt werden!

### Verifikations-Checkliste vor jedem Commit:
- ✅ Website funktioniert: Download-Button funktioniert, SHA-256 wird angezeigt
- ✅ Website responsive: Auf Handy und Desktop getestet
- ✅ App-Name überall aktualisiert: Website, App (strings.xml), Agents.md
- ✅ Inoffiziell-Disclaimer prominent sichtbar
- ✅ GitHub Release-Tag korrekt und APK verfügbar
- ✅ Lokale Tests bestanden (wenn vorhanden)

### Commit-Prozess
1. **Alle Änderungen verifizieren** (siehe Checkliste oben)
2. **Git-Status prüfen:**
   ```bash
   git status
   ```
3. **Änderungen stagen:**
   ```bash
   git add .
   ```
4. **Mit aussagekräftiger Commit-Message committen:**
   ```bash
   git commit -m "Feat: App-Name zu 'Freizeitpark Wartezeiten' & Website-Redesign

   - Rename: 'Wartezeiten App' → 'Freizeitpark Wartezeiten App' (Website & App)
   - Website: Modernes professionelles Design mit Gradient-Header
   - Website: SHA-256 Hash-Anzeige mit Kopier-Funktion
   - Website: Disclaimer für inoffizielle Drittanwendung
   - Cloudflare: Fixed GitHub Release-URL (wartezeiten-app-1.0)
   - Cloudflare: Error-Handling für Worker-Handler"
   ```
5. **Push zu Remote:**
   ```bash
   git push
   ```

### Branching-Strategie
- **main:** Produktiv, nur getestete Releases
- **Feature-Branches:** Für Entwicklung, z.B. `fix/download-button`, `feat/app-rename`
- **Current Session Branch:** `agents-fix-download-button-issue-cloudflare`

### Beispiel-Workflow für eine Session
```bash
# Feature-Branch erstellen
git checkout -b feat/app-rename

# Änderungen machen, testen, verifizieren...

# Lokal committen (mehrere Commits möglich)
git add .
git commit -m "Update app name in website"
git add .
git commit -m "Update app name in Android strings"

# Nach Verifikation: Push
git push origin feat/app-rename

# Pull Request erstellen und mergen
```

## Naming & Branding

### App-Name-Änderung: "Wartezeiten App" → "Freizeitpark Wartezeiten App"
- **Grund:** Klarstellung, dass die App inoffiziell ist und nicht von Wartezeiten.APP entwickelt wurde
- **Orte, wo aktualisiert werden muss:**
  - Website: `website/index.html` (Title, Header, Footer)
  - App: `app/src/main/res/values/strings.xml` (app_name)
  - Agents.md: Überall wo der App-Name erwähnt wird
  - Release-Assets: APK-Dateiname (z.B. freizeitpark-wartezeiten-1.0.apk)

### Inoffizielle Kennzeichnung
- **Website:** Footer mit deutlichem Disclaimer
- **App:** Kann in About-Screen oder Settings hinzugefügt werden (optional)
- **Agents.md:** Klar dokumentiert dass es inoffiziell ist

## API-Nutzungsbedingungen & Attribution (PFLICHT)

Die Nutzung der Wartezeiten.APP API setzt voraus, dass ein **sichtbarer und anklickbarer Link** zur Webseite [https://www.wartezeiten.app](https://www.wartezeiten.app) an einer **prominenten Stelle** in der App platziert wird.

### Aktuelle Implementierung
Ein Attribution-Footer wurde in beiden Hauptscreens ergänzt:
- `ParkListScreen.kt` – als `bottomBar` im `Scaffold`
- `WaitingTimesScreen.kt` – als `bottomBar` im `Scaffold`

Der Footer zeigt den Text **„Daten bereitgestellt von wartezeiten.app"** mit einem anklickbaren, unterstrichenen Link, der den Browser mit `https://www.wartezeiten.app` öffnet.

> **Wichtig:** Dieser Link darf **nicht entfernt** werden. Bei zukünftigen Umstrukturierungen der UI muss die Attribution weiterhin prominent sichtbar und anklickbar bleiben.
