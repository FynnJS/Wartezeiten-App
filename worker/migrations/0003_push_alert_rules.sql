ALTER TABLE push_watchlist_alerts ADD COLUMN notify_once INTEGER NOT NULL DEFAULT 0;
ALTER TABLE push_watchlist_alerts ADD COLUMN only_when_park_open INTEGER NOT NULL DEFAULT 0;
ALTER TABLE push_watchlist_alerts ADD COLUMN quiet_hours_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE push_watchlist_alerts ADD COLUMN quiet_start_minutes INTEGER NOT NULL DEFAULT 1320;
ALTER TABLE push_watchlist_alerts ADD COLUMN quiet_end_minutes INTEGER NOT NULL DEFAULT 480;
ALTER TABLE push_watchlist_alerts ADD COLUMN cooldown_minutes INTEGER NOT NULL DEFAULT 0;
ALTER TABLE push_watchlist_alerts ADD COLUMN last_notified_at INTEGER;
