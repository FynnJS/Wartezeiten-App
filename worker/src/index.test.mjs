import assert from "node:assert/strict";
import test from "node:test";

import {
  buildScheduledAppDataOptions,
  buildStatisticsIndexFromD1Rows,
  cronParkShard,
  deriveAttractionSnapshotTiming,
  mergeStatisticsIndexes,
  toAttractionSnapshotRow,
} from "./index.js";

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

test("valid waiting-time timestamp drives D1 snapshot date and capture time", () => {
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
    Date.parse("2026-06-22T10:31:15Z"),
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
    Date.parse("2026-06-22T10:31:15Z"),
  );

  assert.equal(timing.historyEligible, true);
  assert.equal(row.date, "2026-06-22");
  assert.equal(row.capturedAtMillis, Date.parse("2026-06-22T12:30:00+02:00"));
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
