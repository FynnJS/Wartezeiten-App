CREATE TABLE IF NOT EXISTS push_installations (
  installation_id TEXT PRIMARY KEY,
  fcm_token TEXT NOT NULL,
  language TEXT NOT NULL DEFAULT 'de',
  updated_at INTEGER NOT NULL,
  disabled_at INTEGER
);

CREATE TABLE IF NOT EXISTS push_watchlist_alerts (
  installation_id TEXT NOT NULL,
  local_alert_id TEXT NOT NULL,
  park_key TEXT NOT NULL,
  attraction_id TEXT,
  type TEXT NOT NULL,
  threshold_value INTEGER NOT NULL DEFAULT 0,
  last_seen_value TEXT,
  last_notified_value TEXT,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (installation_id, local_alert_id),
  FOREIGN KEY (installation_id)
    REFERENCES push_installations (installation_id)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_push_watchlist_alerts_park
  ON push_watchlist_alerts (park_key);

CREATE INDEX IF NOT EXISTS idx_push_installations_active
  ON push_installations (disabled_at, updated_at);
