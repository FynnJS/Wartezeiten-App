# Cloudflare App-Data Worker

Der Worker sammelt zentrale Live-Snapshots für die Android-App und stellt sie als kompakte JSON-Endpunkte bereit:

- `/app-data/latest.json` für aktuelle Park-Snapshots und Ranking-Daten
- `/app-data/trend-history.json` für Verlauf/Messpunkte
- `/app-data/global-markers/latest.json` für globale, aus den aktuellen 5-Minuten-Shards abgeleitete Park-Marker
- `/app-data/statistics/index.json` für verfügbare Park-/Datumsbereiche der zentralen Attraktionsstatistiken
- `/app-data/statistics/parks/{parkKey}/dates.json` für verfügbare Tage eines Parks
- `/app-data/statistics/parks/{parkKey}/days/{yyyy-MM-dd}.json` für Wartezeiten- und Statusverlauf aller Attraktionen eines Tages

Die App importiert Park- und Trenddaten in Room und nutzt lokale/API-Scans nur noch als Fallback.
Die Attraktionsstatistiken werden nicht lokal gespeichert, sondern pro Park und Tag zentral im Cloudflare-KV abgelegt.
Dadurch kann die App später gezielt alte Tagesdateien laden, z.B. für Vergleiche oder Monatsübersichten.
Der Cron läuft in drei versetzten Shards alle fünf Minuten. Damit der Free-Plan mit 1.000 KV-Schreibvorgängen pro Tag nicht überschritten wird, schreibt der Worker standardmäßig drei kompakte Tages-Shards und aktualisiert den Statistik-Index nur etwa stündlich bzw. wenn ein neuer Park/Tag auftaucht. Im Shard-Modus werden für Statistik-Snapshots nur Öffnungszeiten und Wartezeiten geladen; Crowd-Level bleibt full Refreshes vorbehalten. Globale Marker werden serverseitig aus diesen Shards gelesen und benötigen keine zusätzlichen KV-Schreibvorgänge. Der Multi-Park-Vergleich in der App nutzt diese bzw. lokal aktualisierte Wartezeitdaten read-only und erzeugt keine zusätzlichen KV-Schreibvorgänge.

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

6. Optional Statistik-Schreibverhalten anpassen:

   ```powershell
   npx wrangler secret put APP_DATA_INDEX_UPDATE_INTERVAL_MILLIS
   npx wrangler secret put APP_DATA_HISTORY_SHARDS
   ```

   Standard: Index-Update ca. stündlich und drei Tages-Shards. Mehr Shards reduzieren die Dateigröße einzelner KV-Werte, erhöhen aber die KV-Schreibvorgänge pro Cron-Lauf.

## KV-Budget

Bei drei 5-Minuten-Cron-Shards entstehen 864 Shard-Läufe pro Tag. Mit der Standardkonfiguration schreibt jeder Lauf genau einen Tages-Statistik-Shard; der Index wird zusätzlich ungefähr stündlich geschrieben. Das ergibt grob 888 KV-Schreibvorgänge pro Tag und bleibt unter dem Free-Plan-Limit von 1.000 Schreibvorgängen pro Tag.

Wichtig: Sehr viele Parks können zusätzlich an Worker-Subrequest-, API- und KV-Größenlimits stoßen. Der Shard-Modus ist auf den aktuellen Wartezeiten.APP-Umfang von über 40 Parks ausgelegt. Wenn die API deutlich wächst, sollte eher auf einen Paid-Plan und/oder R2, D1, Queues bzw. Durable Objects umgestellt werden.

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
curl "http://localhost:8787/app-data/global-markers/latest.json"
curl "http://localhost:8787/app-data/statistics/index.json"
curl "http://localhost:8787/app-data/statistics/parks/europapark/days/2026-06-03.json"
```

## Deploy

```powershell
npm run worker:deploy
```

Nach dem Deploy kann es laut Cloudflare einige Minuten dauern, bis Cron-Trigger global aktiv sind.
