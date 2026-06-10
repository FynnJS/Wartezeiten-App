CREATE TABLE IF NOT EXISTS attraction_history_days (
  park_key TEXT NOT NULL,
  date TEXT NOT NULL,
  generated_at_millis INTEGER NOT NULL,
  open_from TEXT,
  closed_from TEXT,
  schema_version INTEGER NOT NULL DEFAULT 1,
  PRIMARY KEY (park_key, date)
);

CREATE TABLE IF NOT EXISTS attraction_history_snapshots (
  park_key TEXT NOT NULL,
  date TEXT NOT NULL,
  captured_at_millis INTEGER NOT NULL,
  generated_at_millis INTEGER NOT NULL,
  opened_today INTEGER NOT NULL,
  open_from TEXT,
  closed_from TEXT,
  attractions_json TEXT NOT NULL,
  PRIMARY KEY (park_key, date, captured_at_millis),
  FOREIGN KEY (park_key, date)
    REFERENCES attraction_history_days (park_key, date)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_attraction_history_snapshots_date_park_captured
  ON attraction_history_snapshots (date, park_key, captured_at_millis);

CREATE INDEX IF NOT EXISTS idx_attraction_history_days_park_date
  ON attraction_history_days (park_key, date);
