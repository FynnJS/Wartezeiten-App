# Wartezeiten App Website

Die Website stellt die aktuelle APK bereit und dient gleichzeitig als Cloudflare Worker fuer zentrale App-Daten.

## Dateien

- `index.html`, `styles.css`, `script.js`: Download-Seite
- `release.json`: Release-Metadaten fuer Website und In-App-Update
- `CLOUDFLARE-APP-DATA.md`: Setup fuer Worker, KV und Cron
- `download-from-github.ps1`: optionales Hilfsskript zum Aktualisieren von `release.json`

## Release-Metadaten

`release.json` enthaelt:

```json
{
  "versionName": "1.0.3",
  "versionCode": 10003,
  "releaseDate": "2026-06-03",
  "releasePageUrl": "https://github.com/FynnJS/Wartezeiten-App/releases/tag/v1.0.3",
  "apkUrl": "https://github.com/FynnJS/Wartezeiten-App/releases/download/v1.0.3/wartezeiten-app-1.0.3.apk",
  "apkSize": "",
  "releaseNotes": [],
  "showBanner": true
}
```

Zusaetzliche Sicherheits-Scan-Metadaten werden nicht mehr auf der Website angezeigt und nicht mehr in `release.json` geschrieben.

## Deployment

Die Website wird ueber Cloudflare Workers mit Static Assets deployed. `wrangler.jsonc` verweist auf das Verzeichnis `website` und auf den Worker unter `worker/src/index.js`.

Deploy:

```powershell
npm install
npm run worker:deploy
```

## App-Daten

Der Worker stellt zentrale JSON-Endpunkte bereit:

- `/app-data/latest.json`
- `/app-data/trend-history.json`

Diese Daten werden per Cron aktualisiert und von der Android-App als schnelle Quelle fuer Ranking- und Trenddaten genutzt.
