# Wartezeiten App Website

Die Website stellt die aktuelle APK bereit und dient gleichzeitig als Cloudflare Worker für zentrale App-Daten.

## Dateien

- `index.html`, `styles.css`, `script.js`: Download-Seite
- `release.json`: Release-Metadaten für Website und In-App-Update
- `CLOUDFLARE-APP-DATA.md`: Setup für Worker, KV und Cron
- `download-from-github.ps1`: optionales Hilfsskript zum Aktualisieren von `release.json`

## Release-Metadaten

`release.json` enthält:

```json
{
  "versionName": "1.0.5",
  "versionCode": 10005,
  "releaseDate": "2026-06-04",
  "releasePageUrl": "https://github.com/FynnJS/Wartezeiten-App/releases/tag/v1.0.5",
  "apkUrl": "https://github.com/FynnJS/Wartezeiten-App/releases/download/v1.0.5/wartezeiten-app-1.0.5.apk",
  "apkSize": "",
  "releaseNotes": [],
  "showBanner": true
}
```

Zusätzliche Sicherheits-Scan-Metadaten werden nicht mehr auf der Website angezeigt und nicht mehr in `release.json` geschrieben.

## Deployment

Die Website wird über Cloudflare Workers mit Static Assets deployed. `wrangler.jsonc` verweist auf das Verzeichnis `website` und auf den Worker unter `worker/src/index.js`.

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

Diese Daten werden per Cron aktualisiert und von der Android-App als schnelle Quelle für Ranking-, Trend- und Attraktionsdaten genutzt.
