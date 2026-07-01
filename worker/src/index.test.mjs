import assert from "node:assert/strict";
import test from "node:test";

import {
  buildFallbackParksResponse,
  buildLiveParkResponse,
  buildScheduledAppDataOptions,
  buildManualRefreshOptions,
  buildStatisticsIndexFromD1Rows,
  collectParkSnapshot,
  cronParkShard,
  deriveAttractionSnapshotTiming,
  ensureAttractionHistoryD1,
  evaluatePushAlert,
  fetchQueueTimesWaitingItems,
  mergeStatisticsIndexes,
  normalizeApiLanguage,
  pruneAttractionHistoryD1,
  selectCronParkShard,
  toAttractionSnapshotRow,
} from "./index.js";
import { FALLBACK_PARKS } from "./fallbackParks.js";

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
      cron: "3-59/5 * * * *",
      scheduledTime: Date.parse("2026-06-22T10:00:00Z"),
    },
    {
      APP_DATA_DB: {},
      APP_DATA_HISTORY_SHARDS: "54",
    },
  );

  assert.equal(options.skipped, false);
  assert.equal(options.options.shardIndex, 3);
  assert.equal(options.options.shardCount, 4);
  assert.equal(options.options.historyShardIndex, null);
  assert.equal(options.options.historyShardCount, null);
});

test("shared minute cron rotates D1 app-data shards by scheduled minute", () => {
  const first = buildScheduledAppDataOptions(
    {
      cron: "* * * * *",
      scheduledTime: Date.parse("2026-06-22T10:02:00Z"),
    },
    { APP_DATA_DB: {} },
  );
  const second = buildScheduledAppDataOptions(
    {
      cron: "* * * * *",
      scheduledTime: Date.parse("2026-06-22T10:03:00Z"),
    },
    { APP_DATA_DB: {} },
  );

  assert.equal(first.options.shardIndex, 2);
  assert.equal(second.options.shardIndex, 3);
  assert.equal(first.options.shardCount, 4);
});

test("manual D1 refresh defaults to one small app-data shard", () => {
  const options = buildManualRefreshOptions(
    new URL("https://example.com/app-data/refresh?shardIndex=2&shardCount=4"),
    { APP_DATA_DB: {} },
  );

  assert.equal(options.shardIndex, 2);
  assert.equal(options.shardCount, 4);
  assert.equal(options.writeLatest, false);
  assert.equal(options.writeTrend, false);
  assert.equal(options.includeCrowd, false);
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

test("cron sharding keeps representative parks assigned to multiple real cron shards", () => {
  const parks = [
    "europapark",
    "phantasialand",
    "heidepark",
    "toverland",
    "walibibelgium",
    "bobbejaanland",
  ];

  const shards = new Set(parks.map((parkKey) => cronParkShard(parkKey, 6)));

  assert.ok([...shards].every((shard) => shard >= 0 && shard < 6));
  assert.ok(shards.size >= 3);
});

test("cron park selection balances the current park list across four deployable app-data triggers", () => {
  const parks = [
    "altontowers",
    "bobbejaanland",
    "caribeaquaticpark",
    "chessingtonworld",
    "disneyadventureworld",
    "disneycaliforniaadventurepark",
    "disneylandparis",
    "disneylandpark",
    "disneysanimalkingdomthemepark",
    "disneyshollywoodstudios",
    "djurssommerland",
    "efteling",
    "energylandia",
    "epcot",
    "europapark",
    "familypark",
    "ferrariland",
    "futuroscope",
    "gardaland",
    "hansapark",
    "heidepark",
    "legoland",
    "legolandbillund",
    "legolandcalifornia",
    "legolandflorida",
    "legolandnewyork",
    "legolandwindsor",
    "liseberg",
    "magickingdompark",
    "movieparkgermany",
    "nigloland",
    "parcasterix",
    "phantasialand",
    "plopsalandbelgium",
    "plopsalanddeutschland",
    "portaventurapark",
    "rulantica",
    "thorpepark",
    "toverland",
    "traumatica",
    "universalepicuniverse",
    "universalislandsofadventure",
    "universalstudiosflorida",
    "universalvolcanobay",
    "walibibelgium",
    "walibiholland",
  ];

  const shardSizes = [0, 1, 2, 3].map((shard) => selectCronParkShard(parks, shard, 4).length);

  assert.deepEqual(shardSizes, [12, 12, 11, 11]);
});

test("D1 attraction history pruning removes old dates and orphaned day rows", async () => {
  const calls = [];
  const db = {
    prepare(sql) {
      return {
        bind(...values) {
          calls.push({ sql, values });
          return {
            run: async () => ({ success: true }),
          };
        },
        run: async () => ({ success: true }),
      };
    },
    batch: async (statements) => {
      await Promise.all(statements.map((statement) => statement.run()));
      return statements.map(() => ({ success: true }));
    },
  };

  await pruneAttractionHistoryD1(
    {
      APP_DATA_DB: db,
      APP_DATA_D1_RETENTION_DAYS: "14",
    },
    Date.parse("2026-06-28T12:00:00Z"),
  );

  const deleteSnapshots = calls.find((call) => call.sql.includes("DELETE FROM attraction_history_snapshots"));
  const deleteDays = calls.find((call) => call.sql.includes("DELETE FROM attraction_history_days"));

  assert.ok(deleteSnapshots);
  assert.ok(deleteDays);
  assert.equal(deleteSnapshots.values[0], "2026-06-14");
  assert.equal(deleteDays.values[0], "2026-06-14");
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

test("normalizeApiLanguage only accepts de/en and defaults to de", () => {
  assert.equal(normalizeApiLanguage("en"), "en");
  assert.equal(normalizeApiLanguage("de"), "de");
  assert.equal(normalizeApiLanguage("fr"), "de");
  assert.equal(normalizeApiLanguage(undefined), "de");
});

test("live park response sorts open attractions by wait time before closed ones", () => {
  const snapshot = {
    parkKey: "europapark",
    capturedAtMillis: 1000,
    openedToday: true,
    openFrom: "2026-06-22T09:00:00+02:00",
    closedFrom: "2026-06-22T18:00:00+02:00",
    displayCrowdLevel: 42,
    openAttractions: 2,
    totalAttractions: 3,
    errors: [],
    attractions: [
      { id: "a", name: "Silver Star", value: 10, statusCode: 0, status: "opened" },
      { id: "b", name: "Wodan", value: 30, statusCode: 0, status: "opened" },
      { id: "c", name: "Blue Fire", value: -1, statusCode: -1, status: "closed" },
    ],
  };

  const response = buildLiveParkResponse(snapshot);

  assert.deepEqual(response.attractions.map((item) => item.id), ["b", "a", "c"]);
  assert.equal(response.attractions[0].waitMinutes, 30);
  assert.equal(response.attractions[2].waitMinutes, null);
  assert.equal(response.crowdLevel, 42);
});

test("live park response keeps crowd level suppressed when park is not open", () => {
  const snapshot = {
    parkKey: "phantasialand",
    capturedAtMillis: 1000,
    openedToday: false,
    openFrom: null,
    closedFrom: null,
    displayCrowdLevel: null,
    openAttractions: 0,
    totalAttractions: 0,
    errors: [],
    attractions: [],
  };

  const response = buildLiveParkResponse(snapshot);

  assert.equal(response.crowdLevel, null);
  assert.equal(response.openedToday, false);
});

test("fallback parks response is marked degraded and reuses the curated queue-times mapping", () => {
  const response = buildFallbackParksResponse("de");

  assert.equal(response.degraded, true);
  assert.equal(response.source, "queue-times.com");
  assert.equal(response.parks.length, FALLBACK_PARKS.length);
  assert.ok(response.parks.every((park) => park.parkKey && park.name && park.land));
  const names = response.parks.map((park) => park.name);
  assert.deepEqual(names, [...names].sort((a, b) => a.localeCompare(b)));
});

test("queue-times waiting items are mapped to the wartezeiten-like shape used by collectParkSnapshot", async (t) => {
  t.mock.method(globalThis, "fetch", async (url) => {
    assert.equal(url, "https://queue-times.com/parks/56/queue_times.json");
    return new Response(
      JSON.stringify({
        lands: [
          {
            name: "Berlin",
            rides: [
              { id: 1, name: "Taron", is_open: true, wait_time: 20, last_updated: "2026-06-22T10:00:00.000Z" },
              { id: 2, name: "F.L.Y.", is_open: false, wait_time: 0, last_updated: "2026-06-22T10:00:00.000Z" },
            ],
          },
        ],
      }),
      { status: 200 },
    );
  });

  const items = await fetchQueueTimesWaitingItems(56);

  assert.deepEqual(
    items.map((item) => [item.id, item.name, item.status, item.waitingtime]),
    [
      ["qt-1", "Taron", "opened", 20],
      ["qt-2", "F.L.Y.", "closed", 0],
    ],
  );
});

test("queue-times waiting items fetch failure throws so collectParkSnapshot can fall back to the original error", async (t) => {
  t.mock.method(globalThis, "fetch", async () => new Response("", { status: 500 }));

  await assert.rejects(() => fetchQueueTimesWaitingItems(56));
});

function mockWartezeitenAndQueueTimesFetch(
  t,
  {
    openingtimesStatus = 200,
    waitingtimesStatus = 200,
    waitingtimesBody = [],
    crowdlevelStatus = 200,
  } = {},
) {
  t.mock.method(globalThis, "fetch", async (url) => {
    const href = String(url);
    if (href.includes("/v1/openingtimes")) {
      return new Response(
        JSON.stringify([{ opened_today: true, open_from: "2026-06-22T09:00:00+02:00", closed_from: "2026-06-22T18:00:00+02:00" }]),
        { status: openingtimesStatus },
      );
    }
    if (href.includes("/v1/waitingtimes")) {
      return new Response(JSON.stringify(waitingtimesBody), { status: waitingtimesStatus });
    }
    if (href.includes("/v1/crowdlevel")) {
      return new Response(JSON.stringify({ crowd_level: "12,50" }), { status: crowdlevelStatus });
    }
    if (href.includes("queue-times.com/parks/56/queue_times.json")) {
      return new Response(
        JSON.stringify({
          lands: [
            {
              rides: [
                { id: 1, name: "Taron", is_open: true, wait_time: 15, last_updated: "2026-06-22T10:00:00.000Z" },
              ],
            },
          ],
        }),
        { status: 200 },
      );
    }
    throw new Error(`unexpected fetch url in test: ${href}`);
  });
}

test("collectParkSnapshot falls back to queue-times.com waiting times when /v1/waitingtimes fails and a mapping exists", async (t) => {
  mockWartezeitenAndQueueTimesFetch(t, { waitingtimesStatus: 500 });

  const snapshot = await collectParkSnapshot("phantasialand", Date.parse("2026-06-22T10:00:00+02:00"));

  assert.equal(snapshot.dataSource, "queue-times.com");
  assert.equal(snapshot.totalAttractions, 1);
  assert.equal(snapshot.attractions[0].id, "qt-1");
  assert.equal(snapshot.attractions[0].statusCode, 0);
  assert.deepEqual(snapshot.errors, []);
  // Oeffnungszeiten kamen erfolgreich von wartezeiten.app und bleiben massgeblich, auch im Fallback.
  assert.equal(snapshot.openedToday, true);
  assert.equal(snapshot.historyEligible, true);
});

test("collectParkSnapshot reports degraded queue-times fallback without upstream server errors", async (t) => {
  mockWartezeitenAndQueueTimesFetch(t, {
    openingtimesStatus: 500,
    waitingtimesStatus: 500,
    crowdlevelStatus: 500,
  });

  const snapshot = await collectParkSnapshot("phantasialand", Date.parse("2026-06-22T10:00:00+02:00"));

  assert.equal(snapshot.dataSource, "queue-times.com");
  assert.equal(snapshot.totalAttractions, 1);
  assert.equal(snapshot.openAttractions, 1);
  assert.equal(snapshot.openedToday, true);
  assert.deepEqual(snapshot.errors, []);
});

test("collectParkSnapshot without a fallback mapping keeps the original upstream-error behavior", async (t) => {
  mockWartezeitenAndQueueTimesFetch(t, { waitingtimesStatus: 500 });

  const snapshot = await collectParkSnapshot("some-unmapped-park", Date.parse("2026-06-22T10:00:00+02:00"));

  assert.equal(snapshot.dataSource, null);
  assert.equal(snapshot.historyEligible, false);
  assert.equal(snapshot.historySkipReason, "upstream_error");
  assert.equal(snapshot.totalAttractions, 0);
});

test("attraction-specific wait-time alerts do not fall back to a different attraction when the target is missing from the snapshot", () => {
  const snapshot = {
    parkKey: "phantasialand",
    openedToday: true,
    openFrom: "2026-06-22T09:00:00+02:00",
    closedFrom: "2026-06-22T18:00:00+02:00",
    displayCrowdLevel: null,
    attractions: [
      // Simuliert eine queue-times.com-Ausweichquelle: die echte Ziel-UUID des Alarms taucht hier
      // nicht auf, dafuer eine andere, guenstige Attraktion.
      { id: "qt-1", name: "Andere Attraktion", value: 5, statusCode: 0, status: "opened" },
    ],
  };
  const alert = {
    type: "WAIT_TIME_BELOW",
    attraction_id: "real-wartezeiten-uuid",
    threshold_value: 30,
    language: "de",
    last_seen_value: null,
  };

  const result = evaluatePushAlert(alert, snapshot);

  // Ohne den Fix wuerde "Andere Attraktion" (5 Min., unter dem Schwellwert 30) faelschlich als
  // Treffer fuer den eigentlich gesuchten - hier fehlenden - Ziel-Attraktions-Alarm gewertet.
  assert.equal(result.shouldNotify, false);
  assert.equal(result.nextSeenValue, "false");
});
