# Wartezeiten App Website

Die Website stellt die aktuelle APK bereit, bietet unter `wartezeiten.html` Live-Wartezeiten direkt im Browser an und dient gleichzeitig als Cloudflare Worker fuer zentrale App-Daten, Statistik-Snapshots und optionale Watchlist-Push-Alarme.

## Dateien

- `index.html`, `styles.css`, `script.js`: Download-Seite
- `wartezeiten.html`, `app.css`, `wartezeiten.js`: Live-Wartezeiten-Seite (Park-Dropdown/-Suche, aktuelle Wartezeiten/Öffnungszeiten/Auslastung, Statistik-Diagramm) - reines Client-seitiges Hash-Routing (`#park=parkKey`), keine eigene Server-Route nötig
- `theme.js`: gemeinsamer Dark/Light-Mode-Umschalter fuer beide Seiten (Standard: Dark Mode, Speicherung in `localStorage`, Farben an `app/.../ui/theme/Theme.kt` angeglichen - siehe `Agents.md`)
- `release.json`: Release-Metadaten fuer Website und In-App-Update
- `CLOUDFLARE-APP-DATA.md`: Setup fuer Worker, KV, D1 und Cron
- `../worker/migrations/`: D1-Migrationen fuer Statistik- und Push-Tabellen
- `download-from-github.ps1`: optionales Hilfsskript zum Aktualisieren von `release.json`
- `../docs/PUSH-SETUP.md`: Firebase-, GitHub- und Cloudflare-Einrichtung für Standby-Push

## Release-Metadaten

`release.json` enthaelt:

```json
{
  "versionName": "1.0.8",
  "versionCode": 10008,
  "releaseDate": "2026-06-09",
  "releasePageUrl": "https://github.com/FynnJS/Wartezeiten-App/releases/tag/v1.0.8",
  "apkUrl": "https://github.com/FynnJS/Wartezeiten-App/releases/download/v1.0.8/wartezeiten-app-1.0.8.apk",
  "apkSize": "",
  "releaseNotes": [],
  "showBanner": true
}
```

Bei einem neuen GitHub-Release wird `release.json` durch `.github/workflows/release-pipeline.yml` automatisch aktualisiert. Die Release-Notes fuer manuell gestartete Release-Builds liegen unter `docs/releases/vX.Y.Z.md` und werden von `.github/scripts/prepare-release-metadata.py` in die Website-Metadaten uebernommen. Wegen der Pflicht-Update-Sperre in der Android-App darf `release.json` erst auf eine neue Version zeigen, wenn die zugehoerige APK als GitHub-Release-Asset verfuegbar ist.

Zusaetzliche Sicherheits-Scan-Metadaten werden nicht mehr auf der Website angezeigt und nicht mehr in `release.json` geschrieben.

## Deployment

Die Website wird ueber Cloudflare Workers mit Static Assets deployed. `wrangler.jsonc` verweist auf das Verzeichnis `website` und auf den Worker unter `worker/src/index.js`.

Vor einem Release muessen D1-Migrationen remote angewendet werden, bevor der Worker mit neuen Tabellen deployed wird:

```powershell
npx wrangler d1 migrations apply wartezeiten-app-data --remote
```

Deploy:

```powershell
npm install
npm run worker:deploy
```

## App-Daten

Der Worker stellt zentrale JSON-Endpunkte bereit:

- `/app-data/latest.json`
- `/app-data/trend-history.json`
- `/app-data/statistics/index.json`
- `/app-data/statistics/parks/{parkKey}/dates.json`
- `/app-data/statistics/parks/{parkKey}/days/{date}.json`

Diese Daten werden per Cron aktualisiert und von der Android-App als schnelle Quelle fuer Ranking-, Trend- und Attraktionsdaten genutzt. Dieselben Statistik-Endpunkte werden auch von `wartezeiten.js` fuer die Browser-Statistikansicht verwendet.

Fuer die Live-Wartezeiten-Seite proxyt der Worker zusaetzlich direkt die Wartezeiten.APP-API (kein D1/KV noetig, daher ohne Cron sofort verfuegbar):

- `GET /api/parks?lang=de|en` - Parkliste (`parkKey`, `name`, `land`), 1h Browser-Cache
- `GET /api/parks/{parkKey}/live?lang=de|en` - aktuelle Oeffnungszeiten, Auslastung und Wartezeiten je Attraktion, 30s Browser-Cache

Watchlist-Push nutzt zusaetzlich die Worker-Endpunkte `/push/register`, `/push/watchlist`, `/push/unregister` und `/push/status`. Der Push-Cron laeuft separat minuetlich; ohne FCM-Secrets bleibt dieser Pfad inaktiv und die Android-App nutzt den lokalen WorkManager-Fallback. Vor einer Veroeffentlichung mit Standby-Push muss `/push/status` den Wert `pushReady: true` liefern.

## Lokal testen

```powershell
npm run worker:dev
```

Startet die Website samt Worker unter `http://localhost:8787/`. `wartezeiten.html` (Live-Wartezeiten) funktioniert sofort, da `/api/parks` und `/api/parks/{parkKey}/live` nur die oeffentliche Wartezeiten.APP-API proxyen und kein D1/KV brauchen. Fuer die Statistikansicht in `wartezeiten.html` muss die lokale D1-Datenbank erst Messpunkte enthalten; dafuer den Cron manuell ein paar Mal antriggern (siehe `CLOUDFLARE-APP-DATA.md`).
