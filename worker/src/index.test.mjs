import assert from "node:assert/strict";
import test from "node:test";

import {
  buildScheduledAppDataOptions,
  buildStatisticsIndexFromD1Rows,
  cronParkShard,
  deriveAttractionSnapshotTiming,
  ensureAttractionHistoryD1,
  evaluatePushAlert,
  mergeStatisticsIndexes,
  toAttractionSnapshotRow,
} from "./index.js";

function openParkSnapshot() {
  return {
    parkKey: "europapark",
    openedToday: true,
    openFrom: "2026-06-22T09:00:00+02:00",
    closedFrom: "2026-06-22T18:00:00+02:00",
    displayCrowdLevel: 42,
    attractions: [
      { id: "ride-1", name: "Silver Star", value: 10, statusCode: 0, status: "opened" },
    ],
  };
}

test("D1 scheduled app-data uses cron shards and ignores history shards", () => {
  const options = buildScheduledAppDataOptions(
    {
      cron: "1-59/5 * * * *",
      scheduledTime: Date.parse("2026-06-22T10:00:00Z"),
    },
    {
      APP_DATA_DB: {},
      APP_DATA_HISTORY_SHARDS: "54",
    },
  );

  assert.equal(options.skipped, false);
  assert.equal(options.options.shardIndex, 1);
  assert.equal(options.options.shardCount, 3);
  assert.equal(options.options.historyShardIndex, null);
  assert.equal(options.options.historyShardCount, null);
});

test("D1 attraction history schema is created lazily for worker updates", async () => {
  const preparedSql = [];
  const batches = [];
  const db = {
    prepare(sql) {
      preparedSql.push(sql);
      return {
        sql,
        run: async () => ({ success: true }),
      };
    },
    batch: async (statements) => {
      batches.push(statements.map((statement) => statement.sql));
      return statements.map(() => ({ success: true }));
    },
  };

  await ensureAttractionHistoryD1({ APP_DATA_DB: db });
  await ensureAttractionHistoryD1({ APP_DATA_DB: db });

  assert.equal(batches.length, 1);
  assert.equal(preparedSql.filter((sql) => sql.includes("CREATE TABLE IF NOT EXISTS attraction_history_days")).length, 1);
  assert.equal(preparedSql.filter((sql) => sql.includes("CREATE TABLE IF NOT EXISTS attraction_history_snapshots")).length, 1);
  assert.equal(preparedSql.filter((sql) => sql.includes("idx_attraction_history_snapshots_date_park_captured")).length, 1);
});

test("cron sharding keeps representative parks assigned to the three real cron shards", () => {
  const parks = [
    "europapark",
    "phantasialand",
    "heidepark",
    "toverland",
    "walibibelgium",
    "bobbejaanland",
  ];

  const shards = new Set(parks.map((parkKey) => cronParkShard(parkKey, 3)));

  assert.deepEqual([...shards].sort(), [0, 1, 2]);
});

test("stale previous-day waiting times are not eligible for history writes", () => {
  const timing = deriveAttractionSnapshotTiming(
    {
      opened_today: true,
      open_from: "2026-06-23T10:00:00+02:00",
      closed_from: "2026-06-23T17:00:00+02:00",
    },
    [
      {
        datetime: "2026-06-22T17:45:00+02:00",
        date: "2026-06-22",
        waitingtime: 10,
        status: "opened",
      },
    ],
    Date.parse("2026-06-23T06:00:00Z"),
  );

  assert.equal(timing.historyEligible, false);
  assert.equal(timing.skipReason, "stale_waitingtimes");
  assert.equal(timing.historyDate, "2026-06-23");
});

test("valid waiting-time timestamp drives D1 snapshot date while collection time drives capture time", () => {
  const collectionTime = Date.parse("2026-06-22T10:31:15Z");
  const timing = deriveAttractionSnapshotTiming(
    {
      opened_today: true,
      open_from: "2026-06-22T10:00:00+02:00",
      closed_from: "2026-06-22T17:30:00+02:00",
    },
    [
      {
        datetime: "2026-06-22T12:30:00+02:00",
        date: "2026-06-22",
        waitingtime: 15,
        status: "opened",
      },
    ],
    collectionTime,
  );
  const row = toAttractionSnapshotRow(
    {
      parkKey: "toverland",
      capturedAtMillis: timing.capturedAtMillis,
      historyDate: timing.historyDate,
      openedToday: true,
      openFrom: "2026-06-22T10:00:00+02:00",
      closedFrom: "2026-06-22T17:30:00+02:00",
      attractions: [{ id: "a", name: "A", value: 15, statusCode: 0, status: "opened" }],
    },
    collectionTime,
  );

  assert.equal(timing.historyEligible, true);
  assert.equal(row.date, "2026-06-22");
  assert.equal(row.capturedAtMillis, collectionTime);
});

test("repeated cron collections keep distinct D1 capture times when upstream timestamp is unchanged", () => {
  const opening = {
    opened_today: true,
    open_from: "2026-06-22T10:00:00+02:00",
    closed_from: "2026-06-22T17:30:00+02:00",
  };
  const waitingItems = [
    {
      datetime: "2026-06-22T12:30:00+02:00",
      date: "2026-06-22",
      waitingtime: 15,
      status: "opened",
    },
  ];
  const firstCollection = Date.parse("2026-06-22T10:31:15Z");
  const secondCollection = Date.parse("2026-06-22T10:36:15Z");

  const first = deriveAttractionSnapshotTiming(opening, waitingItems, firstCollection);
  const second = deriveAttractionSnapshotTiming(opening, waitingItems, secondCollection);

  assert.equal(first.historyEligible, true);
  assert.equal(second.historyEligible, true);
  assert.equal(first.historyDate, "2026-06-22");
  assert.equal(second.historyDate, "2026-06-22");
  assert.notEqual(first.capturedAtMillis, second.capturedAtMillis);
  assert.equal(first.capturedAtMillis, firstCollection);
  assert.equal(second.capturedAtMillis, secondCollection);
});

test("empty waiting-time payloads are reported as skipped history", () => {
  const timing = deriveAttractionSnapshotTiming(
    {
      opened_today: true,
      open_from: "2026-06-22T10:00:00+02:00",
      closed_from: "2026-06-22T17:30:00+02:00",
    },
    [],
    Date.parse("2026-06-22T10:31:15Z"),
  );

  assert.equal(timing.historyEligible, false);
  assert.equal(timing.skipReason, "empty_waitingtimes");
});

test("D1 statistics index ignores raw rows with no deliverable day snapshots", async () => {
  const index = await buildStatisticsIndexFromD1Rows([
    {
      parkKey: "walibiholland",
      date: "2026-06-21",
      generatedAtMillis: 1,
      deliverableSampleCount: 0,
    },
  ]);

  assert.deepEqual(index.parks, []);
  assert.equal(index._coveredDatesByPark.get("walibiholland").has("2026-06-21"), true);
});

test("D1 statistics index sample count matches cleaned day snapshots", async () => {
  const index = await buildStatisticsIndexFromD1Rows([
    {
      parkKey: "toverland",
      date: "2026-06-22",
      generatedAtMillis: 1,
      deliverableSampleCount: 1,
    },
  ], async () => ({
    generatedAtMillis: 3,
    parkKey: "toverland",
    date: "2026-06-22",
    snapshots: [{ capturedAtMillis: Date.parse("2026-06-22T10:01:00Z"), attractions: [{ id: "a" }] }],
    attractions: [{ id: "a", name: "A", sampleCount: 1, averageWaitMinutes: 10, lastValue: 10, lastStatusCode: 0 }],
  }));

  assert.equal(index.parks.length, 1);
  assert.equal(index.parks[0].sampleCount, 1);
  assert.equal(index.parks[0].attractionCount, 1);
  assert.deepEqual(index.parks[0].dates, ["2026-06-22"]);
});

test("legacy index dates covered by D1 are not reintroduced after D1 filters them", async () => {
  const d1Index = await buildStatisticsIndexFromD1Rows([
    {
      parkKey: "walibiholland",
      date: "2026-06-21",
      generatedAtMillis: 1,
      deliverableSampleCount: 0,
    },
  ]);
  const merged = mergeStatisticsIndexes(
    {
      generatedAtMillis: 5,
      parks: [{
        parkKey: "walibiholland",
        dates: ["2026-06-21"],
        latestDate: "2026-06-21",
        sampleCount: 53,
        attractionCount: 37,
        updatedAtMillis: 5,
        attractions: [{ id: "a", name: "A" }],
      }],
    },
    d1Index,
  );

  assert.deepEqual(merged.parks, []);
});

test("push alert text is localized per installation language", () => {
  const alert = {
    type: "NOW_OPENED",
    language: "fr",
    last_seen_value: null,
  };

  const evaluation = evaluatePushAlert(alert, openParkSnapshot());

  assert.equal(evaluation.title, "Entrée prête : europapark");
  assert.equal(evaluation.body, "Le parc est actuellement signalé comme ouvert.");
});

test("push alert text falls back to German when no language is stored", () => {
  const alert = {
    type: "NOW_OPENED",
    last_seen_value: null,
  };

  const evaluation = evaluatePushAlert(alert, openParkSnapshot());

  assert.equal(evaluation.title, "Einlass bereit: europapark");
  assert.equal(evaluation.body, "Der Park ist aktuell als geoeffnet gemeldet.");
});

test("push alert text is localized for Dutch wait-time alerts", () => {
  const alert = {
    type: "WAIT_TIME_BELOW",
    language: "nl",
    threshold_value: 20,
    attraction_id: null,
    last_seen_value: null,
  };

  const evaluation = evaluatePushAlert(alert, openParkSnapshot());

  assert.equal(evaluation.title, "Rijvenster: Silver Star");
  assert.equal(evaluation.body, "Silver Star staat op 10 min.");
});
