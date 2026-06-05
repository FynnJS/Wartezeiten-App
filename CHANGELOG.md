# Changelog

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
