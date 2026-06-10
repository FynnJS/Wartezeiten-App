# Cloudflare App-Data Worker

Der Worker sammelt zentrale Live-Snapshots für die Android-App und stellt sie als kompakte JSON-Endpunkte bereit:

- `/app-data/latest.json` für aktuelle Park-Snapshots und Ranking-Daten
- `/app-data/trend-history.json` für Verlauf/Messpunkte
- `/app-data/global-markers/latest.json` für globale, aus den aktuellen 5-Minuten-Shards abgeleitete Park-Marker
- `/app-data/statistics/index.json` für verfügbare Park-/Datumsbereiche der zentralen Attraktionsstatistiken
- `/app-data/statistics/parks/{parkKey}/dates.json` für verfügbare Tage eines Parks
- `/app-data/statistics/parks/{parkKey}/days/{yyyy-MM-dd}.json` für Wartezeiten- und Statusverlauf aller Attraktionen eines Tages

Die App importiert Park- und Trenddaten in Room und nutzt lokale/API-Scans nur noch als Fallback.
Die Attraktionsstatistiken werden nicht lokal gespeichert, sondern zentral in Cloudflare D1 abgelegt.
Dadurch kann die App später gezielt alte Tagesdateien laden, z.B. für Vergleiche oder Monatsübersichten.
Der Cron läuft in drei versetzten Shards alle fünf Minuten. Neue Statistik-Snapshots werden in D1 als ein kompaktes Snapshot-JSON pro Park und Messzeitpunkt gespeichert. Die öffentlichen Statistik-Endpunkte behalten ihr bisheriges JSON-Format; alte KV-Tagesdateien bleiben als Legacy-Fallback erhalten und werden mit den D1-Daten zusammengeführt. Im Shard-Modus werden für Statistik-Snapshots nur Öffnungszeiten und Wartezeiten geladen; Crowd-Level bleibt full Refreshes vorbehalten. Globale Marker werden serverseitig aus D1 plus Legacy-KV abgeleitet. Der Multi-Park-Vergleich in der App nutzt diese bzw. lokal aktualisierte Wartezeitdaten read-only und erzeugt keine zusätzlichen Writes.

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

3. KV Namespace erstellen oder bestehende ID weiterverwenden:

   ```powershell
   npx wrangler kv namespace create APP_DATA
   ```

4. Die ausgegebene `id` in `wrangler.jsonc` bei `kv_namespaces[0].id` eintragen.

5. D1-Datenbank erstellen und Migration anwenden:

   ```powershell
   npx wrangler d1 create wartezeiten-app-data
   npx wrangler d1 migrations apply wartezeiten-app-data --remote
   ```

   Die ausgegebene `database_id` in `wrangler.jsonc` bei `d1_databases[0].database_id` eintragen. Die Binding muss `APP_DATA_DB` heißen.

6. Optional Parkliste für den Cron anpassen:

   ```powershell
   npx wrangler secret put APP_DATA_PARK_KEYS
   ```

   Wertbeispiel:

   ```text
   europapark,phantasialand,heidepark,hansapark,legoland-de,disneyland-paris,efteling
   ```

7. Optional Statistik-Schreibverhalten anpassen:

   ```powershell
   npx wrangler secret put APP_DATA_INDEX_UPDATE_INTERVAL_MILLIS
   ```

   `APP_DATA_HISTORY_SHARDS` wird nur noch für den Legacy-KV-Fallback gebraucht. Mit aktivem D1-Binding steuert D1 die zentrale Snapshot-Historie.

## Speicher-Budget

Bei aktivem D1-Binding schreibt ein erfolgreicher Cron-Parklauf im Wesentlichen eine Tagesmetadaten-Zeile und einen Snapshot pro Park. Die Attraktionen liegen als JSON im Snapshot, damit nicht jede Attraktion eine eigene D1-Zeile erzeugt. Dadurch bleibt die Write-Anzahl auch bei vielen Attraktionen deutlich unter einer normalisierten Attraktionspunkt-Tabelle.

Wichtig: Sehr viele Parks können zusätzlich an Worker-Subrequest-, API- und D1-Limits stoßen. Der Shard-Modus ist auf den aktuellen Wartezeiten.APP-Umfang von über 40 Parks ausgelegt. Wenn die API deutlich wächst, sollte die Statistik eher über Queues, R2-Roharchive oder eine stärker aggregierte D1-Struktur erweitert werden.

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
