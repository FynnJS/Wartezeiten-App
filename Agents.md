# Wait Times App – Project Documentation for AI Agents

## 1. Project Overview
**Wait Times App** is an Android application for displaying wait times in theme parks. Based on the [Wartezeiten.APP](https://wartezeiten.app) API.

### Tech Stack
| Component | Technology |
|-----------|-------------|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Architecture | Clean Architecture + MVVM |
| Dependency Injection | Hilt |
| Data Storage | Room (local) + Retrofit (API) |
| Concurrency | Coroutines & Flow |

### Architecture Layers
- **`core`** – Network, Dispatcher, i18n, Utils
- **`data`** – Repositories, local/remote data sources, DTOs, mappers
- **`domain`** – Business logic, use cases, models
- **`di`** – Hilt Dependency Injection modules
- **`ui`** – Jetpack Compose screens, components, theming

## 2. API Documentation (Wartezeiten.APP v1.6.0)
**Base:** `https://api.wartezeiten.app`, header-based parameters ([full documentation](https://api.wartezeiten.app))

| Endpoint | Header | Response | Cache |
|----------|--------|----------|-------|
| `/v1/parks` | `language` (de/en) | Parks: `id`, `uuid`, `name`, `land` | 24h |
| `/v1/openingtimes` | `park` | `opened_today`, `open_from`, `closed_from` | 30min* |
| `/v1/waitingtimes` | `park`, `language` | Attractions: `name`, `waitingtime`, `status`, `uuid`, `datetime` | 5min |
| `/v1/crowdlevel` | `park` | `crowd_level`, `timestamp` | 5-10min |

*client-side max 30min despite API hint (prevents day-change issues)

## 3. Core Architecture & Quality Guidelines

### Offline-First & Error Handling
- Local storage via Room, data via Flow to UI
- Central error handling via `ApiResult` sealed interface
- On network errors: Offline banner with age of cached data

### Quality Requirements
✅ **Verification after every change** (UI visual; logic tested; build runs to catch errors)  
✅ **Commit Message:** After changes, provide a meaningful but short and concise commit message suggestion based on the changes made (in English)
✅ **Commit Message Format:** Suggested messages should follow the pattern `fix: ...` for bug fixes, `feat: ...` for new features, and `chore: ...` for maintenance or documentation updates
✅ **Technically robust, responsive and visually modern** (Material 3 consistent)  
✅ **Update project knowledge:** Document architectural decisions and pitfalls in this file  

## 4. Critical Implementation Details

### API & Caching
| ⚠️ Detail | Impact |
|-----------|--------|
| **Header Parameters** | `park`/`language` as headers (not query), otherwise wrong data per park |
| **OkHttp Vary Header** | `/v1/openingtimes`, `/v1/crowdlevel` → `Vary: park`; `/v1/waitingtimes` → `Vary: park, language` |
| **Cache Strategy** | `/v1/openingtimes`: Max 30min (not 24h), prevents day-change bugs |

### Opening Times & Park Status
| ⚠️ Detail | Rule |
|-----------|-------|
| **Source of Truth** | `opened_today` Boolean is decisive, not `status` strings |
| **Crowd Level for closed parks** | If `opened_today=false` → show "Closed today", no old `crowd_level` |
| **Closed Parks & Attractions** | Attraction lists visible (catalog), but live info (crowd, recommendations) only when open |
| **Detect missing wait time data** | `isParkOpenWithoutWaitingTimeData` in `domain/model/ParkOpeningWindow.kt` detects outages; show `WaitingTimeDataGapBanner` |

### UI Behavior
- **Auto-Update:** Every minute (via `viewModelScope` + `delay`)
- **Refresh Feedback:** Only manual updates → Snackbar. Initial loading / Auto-refresh → no Snackbar. Handled via `refreshTrigger` + `refreshError` in ViewModels to show success/failure feedback consistently.
- **Time Display:** Current park time + last API update time (from API timestamp or device time as fallback)
- **Filter & Sorting:** Parks (country, status), attractions (wait time asc/desc, name, status); Default: highest wait time first
- **Open Filter:** Snapshots max 30min old; older → do not display as open
- **No Logos:** Text only + subtle flags (ISO code → Regional Indicators, e.g., `US` → 🇺🇸)

### Push Notifications & Watchlist
**Two Modes:**
1. **WorkManager (Fallback):** `NotificationWorker` loads once for new alarms, then checks every 30min
2. **Firebase Cloud Messaging (Premium):** `PushRegistrationManager` sync token/watchlist → Cloudflare Worker → D1 → minute cron

**Configuration Required:**
- Android: `FIREBASE_APPLICATION_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_GCM_SENDER_ID` (Gradle Properties)
- Worker: `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY` (Secrets)
- Without configuration: WorkManager takes over, no push error

**Alarm Types:**
- "All changes": Park status/attraction comparison → only on actual changes
- `WAIT_TIME_BELOW`/`WAIT_TIME_ABOVE`: With `attraction_id` exclusively this attraction (no fallback to others!)
- **Extended Rules:** One-time, during park opening, quiet hours 22:00-08:00, min interval 15-120min
- `DAILY_SUMMARY`: Daily ~18:00 park time with status/crowd/open attractions

**Push Health:**
- `/push/status` returns `d1Configured`, `fcmConfigured`, `pushReady` (Booleans)

### Statistics & D1 (Cloudflare)
**Central Snapshot Management:**
- Worker collects via cron in **staggered 5-minute shards** (4 park shards in parallel)
- D1: `attraction_history_snapshots` (park/time/attractions) + `attraction_history_days` (metadata)
- Legacy KV fallback for old daily statistics

**⚠️ D1 Pitfalls:**
| Pitfall | Solution |
|-----------|--------|
| **Write Order** | Write snapshots *per park* early, not at the end → Timeout → empty statistics |
| **Schema Guard** | `ensureAttractionHistoryD1` idempotent before reads/writes |
| **Cron Subrequests** | Shards too large → "Too many subrequests" → empty markers; adjust `DEFAULT_CRON_SHARDS` |
| **Retention** | `APP_DATA_D1_RETENTION_DAYS` (Default 14), delete old snapshots before writes |
| **Timestamp** | `captured_at_millis` = Worker time (`Date.now()`), not API `datetime` |
| **Parallel Queries** | GROUP-BY + JOIN both parallel, not N sequential reads per park |

**Live Source for Park Filter:**
- `/app-data/global-markers/latest.json` for current park status
- Fallback: local opening/wait time scans

### Statistics UI
- **Today's Data:** Initially only "today" if central data exists, otherwise `latestDate`
- **Today's Snapshots:** Do not show future measurement points in the graph
- **Without today's measurements:** Explicitly show "No central data for today yet"
- **Attraction Prediction:** 1-3h forecast + up to 7 historical comparison days (min 3 for conservative statements)
- **Trend Retrieval:** `/app-data/trend-history.json?parkKey={parkKey}` (Filter in D1 query)

### Multi-Park Comparison
- Data-driven screen in `ui/compare` (no more park ratings)
- Read-only, max 4 parks, searchable by name/country
- Metrics calculated from current wait times

### Offline/Search/Share UX
- **Offline Banner:** Network error + cache → banner with age
- **Search History:** Last 5 confirmed searches in `PreferencesDataSource`
- **Alias Search:** `app/src/main/assets/park_aliases.csv` (Code heuristic only fallback)
- **Share:** Statistics screens → PNG via Android Sharesheet
- **Recently Viewed:** 8 entries in `PreferencesDataSource`, update on deep link/notification

### Home/Detail UX
- **Dashboard:** Recently viewed + favorites with opening/wait time metrics
- **Park Details:** Offline banner, data quality card; shareable as text
- **Attraction Details:** Route `parks/{parkKey}?attractionId={attractionId}` → status, history, forecast, watchlist, note
- **Notes:** Local in Room, preserved during cache clear

### Home Screen Widget (Jetpack Glance)
- Stores `park_key` + up to 3 attraction IDs
- Update via `refreshParkDetail`, opens park via `wartezeiten://parks/{parkKey}`
- Wait times only if park is open (`isParkCurrentlyOpen`)
- **Update:** WorkManager every 30min; manual refresh immediate + other instances

### Cache Management
- **Clear in Settings:** API/Statistics cache → favorites/watchlist/settings remain

### Multi-language support (DE/EN/FR/NL)
- **UI Strings:** `localized(language, de = ..., en = ..., fr = ..., nl = ...)` (no if-ternaries)
- **API Calls:** `language.toApiLanguage()` (API only de/en)
- **Push Texts:** Worker uses installation language → `localizedPushText()` (4 languages)
- **Attraction ID:** Uses fixed "de" (not UI language dependent)
- **Language Selection UI:** With flag + native language name

### "What's New" Dialog & Versioning
- One-time after update, controlled via `lastSeenVersionCode` vs `VERSION_CODE`
- **On Release:** `WhatsNewRelease` entry in `WhatsNewContent.kt` with the same `versionCode`

### ⚠️ Pitfalls
| Pitfall | Solution |
|-----------|--------|
| **Encoding Bug (v1.1.6)** | Mojibake instead of UTF-8 (…→â€¦). Before every commit: check `grep -n "â€"` |
| **Today's History** | `buildAttractionHistorySeries` do not use `firstOrNull()`; filter `it.date == today` |
| **Wait Time Alarm Candidates** | `candidates = alert.attraction_id ? (target ? [target] : []) : openAttractions` |
| **Push Percent** | `formatPercent()` round to integer |
| **Watchlist API Error** | Park alarms need `/v1/openingtimes`; attraction alarms work without |
| **CoroutineScope in Application/Service** | Never create anonymous Scope(SupervisorJob()) without reference (memory leak, no cancel). Always hold as property + cancel() in onTerminate/onDestroy. onTerminate is not always reliable (process kill). |
| **Flow Combine Limit** | `combine(f1, f2, f3, f4, f5)` is the limit. For more flows, nest `combine` calls or group related states into sub-objects. |
| **Statistics Loading Flicker** | Use a dedicated `isStatisticsLoading` state to keep "Loading data..." visible until async background statistics are fully fetched, preventing a brief "No data" flicker. |

## 5. Release & Signing

### Release Keystore
- **Permanent:** `scripts/configure-release-signing.ps1` creates keystore + secrets
- **Fingerprint:** Canonical in `config/release-signing.properties` (currently: `272e40a90d94e756d6b940aa410d88a0c42617d11c81fe42af7fe5680be5660e`). On keystore rotation, take the hash from a successful (or failed verify) build and update here + in the properties. See also workflow troubleshooting in history.
- **Backup:** Store outside repo, secure externally
- **GitHub Actions:** Secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
- **Verification:** `.github/scripts/verify-release-certificate.ps1` checks finished APK against repo fingerprint

### Release Changelog & Website
- **Changelog:** Write entry before commit (important user/tech changes)
- **Website Sync:** Automatic from `main` branch → https://wartezeiten-app.tutorialfynn.workers.dev/
- **In-App Updates:** `website/release.json` with `versionCode`, `apkUrl`, `sha256`, `releaseNotesLocalized`
- **Download Automation:** `website/download-from-github.ps1` downloads latest APK + SHA-256
- **In-App Update Installation:** `update/ApkDownloader.kt` + `update/ApkInstaller.kt`

### GitHub Actions Pitfall
- Long Python/Shell scripts not inline via heredoc in `.github/workflows/*.yml` → YAML indentation breaks closing marker
- Put scripts in `.github/scripts/` instead and call them

## 6. Website & Browser Version

### Live Wait Times on Website (2026-06-28)
- **Page:** `website/wartezeiten.html` (+ `app.css`, `wartezeiten.js`)
- **Features:** Park search, current wait times/opening times, statistics with chart
- **Architecture:** Static HTML + client-side hash routing (`#park=parkKey`), no dependencies

### Worker API Endpoints
- `GET /api/parks?lang=de|en` – Park list (1h cache)
- `GET /api/parks/{parkKey}/live?lang=de|en` – Wait times/opening times/crowd (30s cache)
- Both proxy directly wartezeiten.APP API (no D1/KV required)

### Website Theming (Dark Mode Standard)
- **Default without JS:** Dark Mode in `:root` of `styles.css`; Light Mode overrides via `:root[data-theme='light']`
- **Switch JS:** `website/theme.js` in `<head>` **before** stylesheets, sets `data-theme` on `<html>` sync from `localStorage`
- **Colors matched to App:** Material3 Light/DarkColorScheme hex values
- **Wait time colors theme-invariant:** `--wait-low/#4CAF50`, `--wait-mid/#FFB300`, `--wait-high/#F44336` (same as app)
- **Footer theme-invariant:** Fixed `--footer-bg`/`--footer-text` instead of `--text-primary`

### queue-times.com Fallback (2026-07-01)
**Problem:** Wartezeiten.app outages (e.g., `/v1/parks` HTTP 500)

**Solution:**
- **Curated Table:** `worker/src/fallbackParks.js` + `app/.../QueueTimesParkMapping.kt` (approx. 40 parks)
- **Worker `/api/parks`:** `buildFallbackParksResponse()` on error → HTTP 200 + `degraded: true`, `source: "queue-times.com"`
- **`collectParkSnapshot()`:** `fetchQueueTimesWaitingItems()` uses `queue-times.com/parks/{id}/queue_times.json`, maps to wartezeiten format
- **Attraction IDs:** Prefixed with `qt-` to avoid UUID collisions
- **D1 Statistics:** `qt-` IDs appear as separate entries (accepted trade-off)
- **Push/Watchlist:** Park alarms continue; attraction alarms do not load target → no alarm
- **Website Banner:** `#parksDegradedBanner`, `#fallbackSourceBanner` on `degraded`/`dataSource === "queue-times.com"`
- **Android Fallback:** Separate `QueueTimesApiService` for app live display only

## 7. Attribution & API Terms of Use (MANDATORY)

**Wartezeiten.APP API usage requires a visible clickable link to https://www.wartezeiten.app**

### Implementation
- `ParkListScreen.kt` & `WaitingTimesScreen.kt` – `AttributionFooter` in `bottomBar`
- Text: "Daten bereitgestellt von wartezeiten.app" (clickable, underlined)
- **MUST NOT be removed**

### Attribution & System Navigation Bar (Pitfall)
- **As `bottomBar` slot:** Use `AttributionFooter` (= banner + `Modifier.navigationBarsPadding()`) (not just banner)
- **As content:** Banner itself ok (Scaffold `innerPadding` protects from system buttons)

## 8. Verification Policy

After every change **MUST** be checked:
- **UI changes:** Visually or via UI inspection
- **Logic changes:** Tests or manual execution
- **Failures:** Document and fix
