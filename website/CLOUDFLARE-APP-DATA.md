# Cloudflare App-Data Worker

Der Worker sammelt zentrale Live-Snapshots für die Android-App und stellt sie als kompakte JSON-Endpunkte bereit:

- `/app-data/latest.json` für aktuelle Park-Snapshots und Ranking-Daten
- `/app-data/trend-history.json` für Verlauf/Messpunkte
- `/app-data/statistics/index.json` für verfügbare Park-/Datumsbereiche der zentralen Attraktionsstatistiken
- `/app-data/statistics/parks/{parkKey}/dates.json` für verfügbare Tage eines Parks
- `/app-data/statistics/parks/{parkKey}/days/{yyyy-MM-dd}.json` für Wartezeiten- und Statusverlauf aller Attraktionen eines Tages

Die App importiert Park- und Trenddaten in Room und nutzt lokale/API-Scans nur noch als Fallback.
Die Attraktionsstatistiken werden nicht lokal gespeichert, sondern pro Park und Tag zentral im Cloudflare-KV abgelegt.
Dadurch kann die App später gezielt alte Tagesdateien laden, z.B. für Vergleiche oder Monatsübersichten.

## Einmalige Einrichtung

1. Abhängigkeiten installieren:

   ```powershell
   npm install
   ```

2. Bei Cloudflare einloggen oder ein API-Token setzen.

   Interaktiv:

   ```powershell
   npx wrangler login
   ```

   Oder non-interactive:

   ```powershell
   $env:CLOUDFLARE_API_TOKEN="..."
   ```

3. KV Namespace erstellen:

   ```powershell
   npx wrangler kv namespace create APP_DATA
   ```

4. Die ausgegebene `id` in `wrangler.jsonc` bei `kv_namespaces[0].id` eintragen.

5. Optional Parkliste für den Cron anpassen:

   ```powershell
   npx wrangler secret put APP_DATA_PARK_KEYS
   ```

   Wertbeispiel:

   ```text
   europapark,phantasialand,heidepark,hansapark,legoland-de,disneyland-paris,efteling
   ```

## Lokal testen

```powershell
npm run worker:dev
```

Cron manuell triggern:

```powershell
curl "http://localhost:8787/__scheduled?cron=*+*+*+*+*"
```

Endpunkte prüfen:

```powershell
curl "http://localhost:8787/app-data/latest.json"
curl "http://localhost:8787/app-data/trend-history.json"
curl "http://localhost:8787/app-data/statistics/index.json"
curl "http://localhost:8787/app-data/statistics/parks/europapark/days/2026-06-03.json"
```

## Deploy

```powershell
npm run worker:deploy
```

Nach dem Deploy kann es laut Cloudflare einige Minuten dauern, bis Cron-Trigger global aktiv sind.
