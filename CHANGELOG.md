# Changelog

## Unreleased

- Fixed release metadata so the website and in-app update check can detect version 1.0.2.
- Restored the Gradle wrapper JAR for GitHub Actions release builds.
- Added an immediate app update check on startup in addition to periodic checks.
- Fixed WorkManager initialization so Hilt-backed notification and update workers can run correctly.
- Prevented pre-opening park recommendation scans from using waiting-time or crowd-level data before a park is currently open.
- Removed duplicate crowd-level text from the park detail header.

## v1.0.2 - 2026-06-02

- Added German/English language setting and refreshed API calls when the language changes.
- Localized the main park list, park detail controls, watchlist, attribution, update banner, and best-park scan status.
- Added background recommendation scans via WorkManager so "Best park today" can stay warm while the app is closed.
- Improved "Best park today" with scan progress and estimated remaining time.
- Reworked visitor-focused notification categories and notification copy.
