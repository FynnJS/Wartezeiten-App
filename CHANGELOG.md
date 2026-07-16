# Changelog

## v1.2.4 - 2026-07-16

- Adjusted the crowd level calculation to provide more realistic and precise estimates based on live wait times.
- Fixed statistics charts: Attractions that were closed all day now correctly display their historical status data instead of an error message.
- Improved loading states for park statistics: Added a dedicated loading state for historical data to ensure the "Loading data..." message remains visible until both live and statistical data are fully fetched, preventing a brief "No data" flicker.
- Bumped Android release metadata to `versionCode 10204` / `versionName 1.2.4`.

## v1.2.3 - 2026-07-13

- Refined the waiting-times, park-list, and statistics screens with more consistent loading states, clearer error banners, and smoother pull-to-refresh behavior.
- Improved park-list filtering and loading flow, plus the statistics detail view now presents headers and empty states more cleanly.
- Hardened the data mapping and refresh timing for trend and wait-time data to reduce UI inconsistencies.
- Bumped Android release metadata to `versionCode 10203` / `versionName 1.2.3`.

## v1.2.2 - 2026-07-08

- Fixed statistics and statistic graphs to use the park's local timezone for "today" instead of the device's timezone. Parks in the USA (or other timezones) now correctly show the current local day's data and history even when viewed late at night or early morning from Germany.
- Hardened APK downloader with temporary files, guaranteed cleanup on errors, and broader exception handling to prevent partial downloads and resource leaks.
- Fixed memory leak in Application: CoroutineScope for watchlist sync is now properly stored and cancelled.
- Added robust error handling and logging in Firebase Messaging Service (token registration and one-time alert disabling).
- Prevented potential ClassCastException crashes in ParkListViewModel with safe runtime casts and fallbacks.
- Improved notification ID generation across NotificationWorker and FCM service to reduce collision risk using better key separators.
- Fixed potential zero-delay busy loop in the park detail time ticker.
- Corrected p75 percentile calculation in worker crowd level estimation with proper linear interpolation.
- Enhanced input sanitization in worker (cleanString now strips control characters).
- Updated Agents.md with new pitfall documentation for CoroutineScope lifecycle management.
- Bumped Android release metadata to `versionCode 10202` / `versionName 1.2.2`.

## v1.2.1 - 2026-07-01

- Added a queue-times.com fallback for live waiting times when the Wartezeiten.APP upstream returns server errors, including Android, the website live page, Worker live snapshots, D1 statistics collection, and park-wide Watchlist/push scans.
- Added reduced-source banners on Android and the website so users can see when fallback data is active without showing misleading generic server errors.
- Fixed cached park lists so temporary upstream 5xx errors no longer keep showing the stale "Serverfehler / last successful park update" banner when usable park data is already present.
- Removed the per-attraction "now / later / typical" wait-time advice chips from the waiting-times list and attraction detail header while keeping forecast and history charts intact.
- Hardened attraction-specific Watchlist evaluation during fallback periods so queue-times IDs can never trigger an alert for a different watched attraction.
- Bumped Android release metadata to `versionCode 10201` / `versionName 1.2.1`.

## v1.2.0 - 2026-06-30

- Added a live wait-times page on the website showing real-time park status, waiting times, opening hours, crowd levels, and statistics directly in the browser — no app installation required.
- Added dark mode support for the website, matching the app's visual design.
- Fixed central wait-time measurement points for parks and attractions not being recorded since the cron migration in v1.1.8: a bug in the scheduled handler caused statistics to never run on the shared `* * * * *` cron. Statistics now run every minute with rotating four-shard coverage.
- Fixed app startup loading: the park list no longer triggers a slow per-park API scan when the "open only" filter is active at startup.
- Fixed the UI-language switch from triggering a redundant park recommendation scan.
- Fixed statistics collection resilience: a failing pre-run D1 snapshot prune no longer aborts the entire shard's statistics run.
- Improved statistics index response time: the `statistics/index.json` endpoint now uses two parallel D1 queries instead of N sequential per-park queries.
- Improved park statistics loading speed on the detail screen: up to 8 history-day HTTP requests now load in parallel instead of sequentially.
- Hardened CI release signing: the pipeline validates the APK certificate fingerprint before uploading to prevent update-incompatible APKs.
- Bumped Android release metadata to `versionCode 10200` / `versionName 1.2.0`.

## v1.1.9 - 2026-06-25

- Added clearer loading information across the app so empty lists no longer look like final results while parks, attractions, statistics, or comparison data are still loading.
- Improved the "open parks only" flow with a dedicated loading card while current opening and wait-time snapshots are being checked.
- Improved park detail loading feedback with localized messages for initial park-data loading and attraction refreshes.
- Improved the park comparison screen with initial loading states and parallel refreshes for selected parks, making comparison updates faster.
- Improved statistics, Watchlist, and weather screens with friendlier loading and empty states plus more localized labels.
- Hardened Cloudflare Worker statistics after updates by lazily ensuring the D1 attraction-history schema exists before central statistic reads and writes.
- Bumped Android release metadata to `versionCode 10109` / `versionName 1.1.9`.

## v1.1.8 - 2026-06-24

- Fixed local Watchlist wait-time notifications for attraction-specific alerts so a closed target attraction no longer falls back to another attraction while keeping the original attraction name in the title.
- Fixed local push crowd-level percentages to use whole-number rounding, matching the rest of the app and the Cloudflare Worker.
- Improved the settings language selector with country flags and native language names, making each language recognizable regardless of the currently selected UI language.
- Added a park-detail warning when a park is open according to opening times for at least 15 minutes but no attraction reports live wait-time data, clarifying likely upstream data-source outages.
- Fixed attribution footers in bottom bars so they respect the Android system navigation bar on gesture and 3-button navigation.
- Fixed the mandatory update screen so release notes are fully scrollable and shown in the selected app language.
- Hardened release signing so local release builds fail without the stable release keystore instead of producing update-incompatible APKs.
- Fixed global statistics collection after v1.1.6: central D1 snapshots now use the actual Worker collection time as the measurement timestamp again, while upstream wait-time timestamps are only used to reject stale days.
- Bumped Android release metadata to `versionCode 10108` / `versionName 1.1.8`.

## v1.1.7 - 2026-06-24

- Added in-app update downloads: the mandatory update screen now downloads the new APK itself (with progress and SHA-256 verification) and opens the system install dialog directly, instead of requiring a manual download via GitHub/browser.
- Added French and Dutch as fully supported app languages, alongside German and English. The Wartezeiten.app API itself still only returns "de"/"en" content, so French/Dutch app users transparently get English-language park/attraction data from the upstream API.
- Added a "What's new" dialog shown once after an app update, summarizing the latest release's highlights in the user's selected language.
- Redesigned the favorites dashboard: open parks now sort first (by lowest max wait), each card shows an explicit open/closed status badge, and the layout switched from a horizontally-scrolling row to a two-column grid with an "X of Y open" summary.
- Fixed standby push notifications always being sent in German regardless of the installation's selected app language; push text (including the local WorkManager fallback) is now localized to German, English, French, or Dutch.
- Fixed mojibake (`â€¦`, `â€¢`, `â‰¤`, `â†‘`, `â†“`) reintroduced into the attraction detail screen by the v1.1.6 encoding fix, which had broken the loading title, the date/time separator, the max-wait filter chip, and the sort labels.
- Fixed the attraction detail "Today's history" chart silently showing a previous day's measurements (mislabeled as today) when the central statistics index does not yet contain a day for today.
- Bumped Android release metadata to `versionCode 10107` / `versionName 1.1.7`.

## v1.1.6 - 2026-06-23

- Added attraction detail cards with status badge, historical wait chart, 1-3 hour wait forecast, personal Room-backed notes, direct Watchlist setup, and deep-link sharing for parks and attractions.
- Moved park aliases into a maintainable local asset file.
- Improved attraction-detail charts with clearer wait-time and time axes.
- Fixed broken umlauts and special characters in the attraction detail UI.
- Bumped Android release metadata to `versionCode 10106` / `versionName 1.1.6`.

## v1.1.5 - 2026-06-23

- Fixed central worker statistics so D1 cron collection no longer uses legacy history shards, stale upstream wait-time dates are skipped, and the statistics index only advertises days with deliverable measurement points.
- Added worker tests for D1 cron sharding, stale wait-time filtering, and statistics index consistency.
- Bumped Android release metadata to `versionCode 10105` / `versionName 1.1.5`.

## v1.1.4 - 2026-06-17

- Fixed park-detail statistics so outdated central days are no longer shown as today's live park statistics.
- Added a clear empty state when today's central wait-time measurements are not available yet.
- Made attraction wait-time advice more conservative by ignoring weak historical samples and hiding misleading "typical" labels for large current deviations.
- Fixed the Phantasialand-style 60-minute versus "typical 8 minutes" case with a regression test.
- Improved the favorite-park widget with a manual refresh action, WorkManager refreshes every 30 minutes, and a more compact MIUI-friendly layout.
- Hardened central Cloudflare statistics collection by accepting object-shaped opening-time responses and writing successful D1 park snapshots immediately during cron runs.
- Documented the new statistics, comparison-label, widget-refresh, and D1 snapshot safeguards for future maintenance.

## v1.1.3 - 2026-06-16

- Added a configurable Android home-screen widget for a favorite park with opening status, average and highest wait time, selected attractions, data age, and direct park deep link.
- Improved the widget configuration flow and launcher preview, including a clearly marked example preview and a loading state for configured widgets while data is still unavailable.
- Fixed the open-parks filter by using fresh global markers and a local scan fallback instead of stale central latest data.
- Fixed restored park search state so a fresh install no longer opens with an old search term.
- Stabilized park-list search focus so the keyboard no longer closes unexpectedly during typing.
- Fixed park and attraction statistics loading after the central D1 snapshot migration, including a same-day live fallback.
- Fixed park-detail statistic graphs so today's chart does not show future measurement points and X-axis labels use the park timezone.

## v1.1.2 - 2026-06-15

- Added conservative "now or later" guidance for open attractions based on matching local-time samples from up to seven historical days.
- Added configurable Watchlist delivery rules for one-time alerts, park-opening restrictions, quiet hours, cooldown intervals, and pausing or resuming individual alerts.
- Added a daily park summary alert around 18:00 local park time and made the last successful trigger visible in the Watchlist.
- Applied the same delivery rules to local WorkManager checks and server-side Firebase standby push.
- Fixed local Firebase configuration by reading a validated `google-services.json` when Gradle properties are not supplied.
- Improved push diagnostics and test notifications by requesting Android notification permission and linking to app notification settings after a denial.
- Added release validation for Firebase configuration and a D1 migration guard before deploying the updated Cloudflare Worker.
- Established a permanent release keystore with certificate verification in CI; installations signed with the previous unrecoverable key require one final reinstall for v1.1.2.

## v1.1.1 - 2026-06-14

- Replaced the park-detail utilization card with complete central park statistics, including average wait times and the daily graph; current utilization remains visible as compact text.
- Fixed parks remaining orange despite open attractions by combining fresh opening-time and attraction status signals and shortening the opening-time HTTP cache.
- Fixed park-specific global trend loading by scoping the public trend-history endpoint to the selected park.
- Added explicit standby-push diagnostics, a test notification, retry controls, and a server readiness check in the Watchlist UI.
- Hardened local notification retries when upstream APIs are temporarily unavailable and added the Android wake-lock permission for background work.
- Added automated Firebase and Cloudflare push setup plus a release-pipeline guard that prevents publishing APKs without Firebase configuration.
- Documented the complete push deployment and v1.1.1 release process.

## v1.1.0 - 2026-06-10

- Added a clearer offline mode with cache-age banners in the park list and park details.
- Added persistent park search, recent search history, recently viewed parks, favorites-first sorting, and a favorites dashboard.
- Added share actions for statistics screenshots and current park summaries.
- Added data-quality indicators and cache management so stale or local data is easier to understand and reset.
- Added Watchlist alerts for all park changes and all attraction changes, with notifications opening the park page or attraction.
- Added optional server-side Firebase Cloud Messaging push for Watchlist alerts, backed by Cloudflare Worker Cron and D1, while keeping local WorkManager checks as fallback.
- Repaired global park measurement points by serving public trend history from D1 attraction snapshots plus legacy KV data.
- Added calculated park utilization to central snapshots so park trend charts keep receiving global points without extra crowd-level cron calls.
- Preserved opening-window metadata in public trend imports and fixed parks that reported the closing timestamp on the previous date.
- Changed the top-bar Watchlist action to use the same notification bell icon as park and attraction alert actions.
- Replaced the dismissible in-app update banner with a required update screen that blocks outdated app versions once a newer release is detected.
- Shortened update messaging in the app and background notification so release notes remain readable on small screens.
- Hardened the GitHub release pipeline so published APK releases require stable signing secrets and stay update-compatible.

## v1.0.8 - 2026-06-09

- Fixed public statistics collection so central park and attraction measurement points are tracked regularly and shared for all users.
- Limited same-day statistics charts to the current time instead of extending empty graph space to park closing time.
- Restored required Wartezeiten.APP attribution on park detail and compare screens.
- Kept park recommendation snapshots current when crowd-level data fails but opening and waiting-time data are available.
- Hardened Watchlist notifications against partial API failures, including unknown park-opening states and failed crowd-level calls.
- Improved local notification permission recovery with an app-settings shortcut and permission recheck after returning to the app.
- Suppressed silent auto-refresh error banners when cached wait-time data is already available.
- Localized the Watchlist dialog more consistently for German and English users.
- Made open-park filters and comparisons ignore stale opening snapshots older than 30 minutes.
- Cleaned obsolete repository artifacts and stopped tracking local machine configuration.
- Hardened website release-note rendering for safer release metadata display.

## v1.0.7 - 2026-06-08

- Added a data-based multi-park comparison screen with searchable park selection, sorting, and direct navigation to park detail pages.
- Replaced park ratings with comparison-focused decision support based on current waits, open attractions, and data freshness.
- Added local Watchlist background notifications without Firebase/FCM setup and rescheduling after reboot or app update.
- Added an offline/cache status badge so users can see when cached data is being shown.
- Improved central Cloudflare app-data logging and global marker handling for free-plan-friendly operation.
- Fixed stale attraction data so closed or currently unavailable parks no longer keep old wait-time lists from previous opening days.
- Cleaned up start-page actions and updated the statistics/comparison icon treatment.

## v1.0.6 - 2026-06-05

- Fixed park statistics navigation from the start page so the selected park is reliably applied.
- Stabilized central statistics loading with safer date fallback when today's central snapshots are not available yet.
- Improved Worker app-data collection so partial API failures no longer prevent usable attraction snapshots from being stored.
- Added separate status colors for closed, weather-related closures, and maintenance in attraction statistics charts.
- Added tests for statistics date handling and park-average calculations.

## v1.0.5 - 2026-06-04

- Fixed park statistics deep links from the start page so the selected park opens directly in the statistics view.
- Added direct park statistics access from park cards and park detail pages.
- Separated park statistics from attraction statistics and added park-level average wait time charts.
- Fixed attraction statistics data loading and improved chart scaling for wait times versus closed/status markers.
- Limited utilization charts to one park day or the park opening window instead of spanning multiple days.
- Improved refresh stability so optional server errors no longer block core park and wait-time data.
- Updated build configuration to remove deprecated Android Gradle options for the v1.0.5 build.

## v1.0.4 - 2026-06-03

- Added a combined park and attraction search on the start page with direct attraction deep links.
- Removed share actions from park and attraction views to keep the UI calmer.
- Improved the statistics page with stable park, date, and attraction selection even when a day has no stored data yet.
- Added current attraction data to the public app-data feed so attraction search can work without visiting each park first.
- Fixed German umlaut rendering in affected UI and release texts.
- Prepared central attraction history charts and statistics for future daily/monthly review.

## v1.0.3 - 2026-06-03

- Added Cloudflare Worker app-data endpoints with Cron/KV setup for central park snapshots, recommendations, and trend history.
- Added "Best value today" ranking, quick access to favorite parks, and share actions for park status and attractions.
- Improved watchlist notifications with presets, richer watchlist details, and notification deep links to parks/attractions.
- Added trend chart points with support for public history plus local fallback.
- Moved API attribution from persistent bottom bars to fixed content placements.
- Removed SHA-256/VirusTotal display and metadata from the website release flow.
- Hardened opening-time, crowd-level, notification, and cancellation handling.

## v1.0.2 - 2026-06-02

- Added German/English language setting and refreshed API calls when the language changes.
- Localized the main park list, park detail controls, watchlist, attribution, update banner, and best-park scan status.
- Added background recommendation scans via WorkManager so "Best park today" can stay warm while the app is closed.
- Improved "Best park today" with scan progress and estimated remaining time.
- Reworked visitor-focused notification categories and notification copy.
