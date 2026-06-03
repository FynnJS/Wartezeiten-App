# Cloudflare App-Data Worker

Der Worker sammelt zentrale Live-Snapshots fuer die Android-App und stellt sie als kompakte JSON-Endpunkte bereit:

- `/app-data/latest.json` fuer aktuelle Park-Snapshots und Ranking-Daten
- `/app-data/trend-history.json` fuer Verlauf/Messpunkte

Die App importiert diese Daten in Room und nutzt lokale/API-Scans nur noch als Fallback.

## Einmalige Einrichtung

1. Abhaengigkeiten installieren:

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

5. Optional Parkliste fuer den Cron anpassen:

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

Endpunkte pruefen:

```powershell
curl "http://localhost:8787/app-data/latest.json"
curl "http://localhost:8787/app-data/trend-history.json"
```

## Deploy

```powershell
npm run worker:deploy
```

Nach dem Deploy kann es laut Cloudflare einige Minuten dauern, bis Cron-Trigger global aktiv sind.
