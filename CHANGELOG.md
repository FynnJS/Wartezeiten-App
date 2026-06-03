# Changelog

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
