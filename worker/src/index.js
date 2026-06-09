const WARTEZEITEN_API_BASE = "https://api.wartezeiten.app/v1";
const LATEST_KEY = "app-data/latest.json";
const TREND_KEY = "app-data/trend-history.json";
const GLOBAL_MARKERS_PATH = "/app-data/global-markers/latest.json";
const ATTRACTION_HISTORY_INDEX_KEY = "app-data/attraction-history/index.json";
const ATTRACTION_HISTORY_PREFIX = "app-data/attraction-history";
const ATTRACTION_HISTORY_DAILY_PREFIX = "app-data/attraction-history-days";
const MAX_HISTORY_AGE_MILLIS = 48 * 60 * 60 * 1000;
const MAX_HISTORY_POINTS_PER_PARK = 576;
const MAX_ATTRACTION_SNAPSHOTS_PER_DAY = 288;
const REQUEST_DELAY_MILLIS = 900;
const DEFAULT_ATTRACTION_HISTORY_SHARDS = 3;
const DEFAULT_INDEX_UPDATE_INTERVAL_MILLIS = 60 * 60 * 1000;
const DEFAULT_CRON_SHARDS = 3;

const DEFAULT_PARK_KEYS = [
  "europapark",
  "phantasialand",
  "heidepark",
  "hansapark",
  "legoland-de",
  "disneyland-paris",
  "efteling",
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/app-data/latest.json") {
      return jsonResponse(await readJson(env, LATEST_KEY, emptyLatest()));
    }

    if (url.pathname === "/app-data/trend-history.json") {
      return jsonResponse(await readJson(env, TREND_KEY, emptyTrendHistory()));
    }

    if (url.pathname === GLOBAL_MARKERS_PATH) {
      return jsonResponse(await readGlobalMarkers(env));
    }

    if (url.pathname === "/app-data/statistics/index.json") {
      return jsonResponse(await readJson(env, ATTRACTION_HISTORY_INDEX_KEY, emptyAttractionHistoryIndex()));
    }

    const datesMatch = url.pathname.match(/^\/app-data\/statistics\/parks\/([^/]+)\/dates\.json$/);
    if (datesMatch) {
      const parkKey = decodeURIComponent(datesMatch[1]);
      const index = await readJson(env, ATTRACTION_HISTORY_INDEX_KEY, emptyAttractionHistoryIndex());
      const park = index.parks.find((item) => item.parkKey === parkKey);
      return jsonResponse({
        generatedAtMillis: index.generatedAtMillis ?? 0,
        parkKey,
        dates: park?.dates ?? [],
      });
    }

    const dayMatch = url.pathname.match(/^\/app-data\/statistics\/parks\/([^/]+)\/days\/(\d{4}-\d{2}-\d{2})\.json$/);
    if (dayMatch) {
      const parkKey = decodeURIComponent(dayMatch[1]);
      const date = dayMatch[2];
      return jsonResponse(await readAttractionDay(env, parkKey, date));
    }

    if (url.pathname === "/app-data/refresh" && request.method === "POST") {
      const expectedToken = env.APP_DATA_REFRESH_TOKEN;
      if (expectedToken && request.headers.get("authorization") !== `Bearer ${expectedToken}`) {
        return jsonResponse({ error: "Unauthorized" }, 401);
      }
      const result = await updateAppData(env);
      return jsonResponse(result);
    }

    if (env.ASSETS) {
      return env.ASSETS.fetch(request);
    }
    return new Response("Not found", { status: 404 });
  },

  async scheduled(controller, env, ctx) {
    ctx.waitUntil(updateScheduledAppData(controller, env));
  },
};

async function updateScheduledAppData(controller, env) {
  const shardCount = parsePositiveInt(env.APP_DATA_CRON_SHARDS) ?? DEFAULT_CRON_SHARDS;
  const shardIndex = scheduledShardIndex(controller?.cron, shardCount);
  return updateAppData(env, {
    shardIndex,
    shardCount,
    writeLatest: false,
    writeTrend: false,
    includeCrowd: false,
  });
}

async function updateAppData(env, options = {}) {
  const now = Date.now();
  const allParkKeys = await resolveParkKeys(env);
  const parkKeys = options.shardIndex == null
    ? allParkKeys
    : allParkKeys.filter((parkKey) => cronParkShard(parkKey, options.shardCount) === options.shardIndex);
  const existingHistory = options.writeTrend === false
    ? emptyTrendHistory()
    : await readJson(env, TREND_KEY, emptyTrendHistory());
  const historyByPark = new Map(
    existingHistory.parks.map((park) => [park.parkKey, park.snapshots ?? []]),
  );

  const parks = [];
  const recommendations = [];
  const errors = [];
  const attractionDayUpdates = [];

  for (const parkKey of parkKeys) {
    try {
      const snapshot = await collectParkSnapshot(parkKey, now, {
        includeCrowd: options.includeCrowd !== false,
      });
      parks.push(toLatestParkSnapshot(snapshot));

      if (snapshot.openedToday && snapshot.openAttractions > 0 && snapshot.displayCrowdLevel != null) {
        const score = recommendationScore(snapshot);
        recommendations.push({
          parkKey,
          score,
          crowdLevel: snapshot.displayCrowdLevel,
          openAttractions: snapshot.openAttractions,
          totalAttractions: snapshot.totalAttractions,
          reason: buildReason(snapshot),
        });

        const recentSnapshots = (historyByPark.get(parkKey) ?? [])
          .filter((item) => now - Number(item.capturedAtMillis ?? 0) <= MAX_HISTORY_AGE_MILLIS);
        recentSnapshots.push(toTrendSnapshot(snapshot));
        historyByPark.set(
          parkKey,
          recentSnapshots
            .sort((a, b) => Number(a.capturedAtMillis) - Number(b.capturedAtMillis))
            .slice(-MAX_HISTORY_POINTS_PER_PARK),
        );
      }

      if (snapshot.attractions.length > 0) {
        attractionDayUpdates.push(await buildUpdatedAttractionHistory(env, snapshot, now));
      }

      if (snapshot.errors.length > 0) {
        errors.push(...snapshot.errors.map((message) => ({ parkKey, message })));
      }

      await delay(REQUEST_DELAY_MILLIS);
    } catch (error) {
      errors.push({ parkKey, message: error instanceof Error ? error.message : String(error) });
    }
  }

  recommendations.sort((a, b) => b.score - a.score);

  const latest = {
    generatedAtMillis: now,
    parks,
    recommendations,
    errors,
  };
  const trendHistory = {
    generatedAtMillis: now,
    parks: [...historyByPark.entries()]
      .filter(([, snapshots]) => snapshots.length > 0)
      .map(([parkKey, snapshots]) => ({ parkKey, snapshots })),
  };

  if (options.writeLatest !== false) {
    await env.APP_DATA.put(LATEST_KEY, JSON.stringify(latest));
  }
  if (options.writeTrend !== false) {
    await env.APP_DATA.put(TREND_KEY, JSON.stringify(trendHistory));
  }
  if (attractionDayUpdates.length > 0) {
    await writeAttractionHistoryBatch(env, attractionDayUpdates, now);
  }
  return {
    ok: true,
    generatedAtMillis: now,
    parks: parks.length,
    totalParks: allParkKeys.length,
    shardIndex: options.shardIndex ?? null,
    shardCount: options.shardCount ?? null,
    errors,
  };
}

async function resolveParkKeys(env) {
  const configured = parseParkKeys(env.APP_DATA_PARK_KEYS);
  const maxParks = parsePositiveInt(env.APP_DATA_MAX_PARKS);
  if (configured.length > 0) {
    return maxParks == null ? configured : configured.slice(0, maxParks);
  }

  try {
    const parks = await apiJson("/parks", { language: "de" });
    const discovered = Array.isArray(parks)
      ? parks
        .map((park) => park.id || park.uuid || stableAttractionId(park.name))
        .filter(Boolean)
      : [];
    const unique = [...new Set(discovered)];
    if (unique.length > 0) {
      return maxParks == null ? unique : unique.slice(0, maxParks);
    }
  } catch (error) {
    console.warn("Could not discover park keys", error);
  }

  return maxParks == null ? DEFAULT_PARK_KEYS : DEFAULT_PARK_KEYS.slice(0, maxParks);
}

async function collectParkSnapshot(parkKey, now, options = {}) {
  const includeCrowd = options.includeCrowd !== false;
  const [openingResult, waitingResult, crowdResult] = await Promise.allSettled([
    apiJson("/openingtimes", { park: parkKey }),
    apiJson("/waitingtimes", { park: parkKey, language: "de" }),
    includeCrowd ? apiJson("/crowdlevel", { park: parkKey }) : Promise.resolve(null),
  ]);

  const openingTimes = settledValue(openingResult);
  const waitingTimes = settledValue(waitingResult);
  const crowdLevel = settledValue(crowdResult);
  const opening = Array.isArray(openingTimes) ? openingTimes[0] : null;
  const waitingItems = Array.isArray(waitingTimes) ? waitingTimes : [];
  const attractionItems = waitingItems.map((item) => toAttractionSnapshotItem(item));
  const openedToday = opening?.opened_today === true;
  const openAttractions = attractionItems.filter((item) => item.statusCode === 0).length;
  const totalAttractions = waitingItems.length;
  const apiCrowdLevel = parseCrowdLevel(crowdLevel?.crowd_level);
  const displayCrowdLevel = openedToday && openAttractions > 0 ? apiCrowdLevel : null;
  const errors = [
    settledError("/openingtimes", openingResult),
    settledError("/waitingtimes", waitingResult),
    includeCrowd ? settledError("/crowdlevel", crowdResult) : null,
  ].filter(Boolean);

  if (
    openingResult.status === "rejected" &&
    waitingResult.status === "rejected" &&
    (!includeCrowd || crowdResult.status === "rejected")
  ) {
    throw new Error(errors.join("; ") || "all upstream requests failed");
  }

  return {
    parkKey,
    capturedAtMillis: now,
    apiCrowdLevel,
    calculatedCrowdLevel: null,
    displayCrowdLevel,
    openedToday,
    openFrom: opening?.open_from ?? opening?.opening ?? null,
    closedFrom: opening?.closed_from ?? opening?.closing ?? null,
    openAttractions,
    totalAttractions,
    attractions: attractionItems,
    errors,
  };
}

function settledValue(result) {
  return result.status === "fulfilled" ? result.value : null;
}

function settledError(label, result) {
  if (result.status === "fulfilled") return null;
  const message = result.reason instanceof Error ? result.reason.message : String(result.reason);
  return `${label}: ${message}`;
}

async function apiJson(path, headers) {
  const response = await fetch(`${WARTEZEITEN_API_BASE}${path}`, {
    headers,
    cf: { cacheTtl: 60, cacheEverything: false },
  });
  if (!response.ok) {
    throw new Error(`${path} failed with HTTP ${response.status}`);
  }
  return response.json();
}

function recommendationScore(snapshot) {
  const crowdScore = Math.max(0, Math.min(100, 100 - (snapshot.displayCrowdLevel ?? 65)));
  const attractionScore = snapshot.totalAttractions > 0
    ? Math.max(0, Math.min(100, (snapshot.openAttractions / snapshot.totalAttractions) * 100))
    : 0;
  return Math.max(0, Math.min(100, Math.round((crowdScore * 0.55) + (attractionScore * 0.45))));
}

function buildReason(snapshot) {
  const crowd = snapshot.displayCrowdLevel == null
    ? "Auslastung unbekannt"
    : `ca. ${Math.round(snapshot.displayCrowdLevel)}% Auslastung`;
  const attractions = snapshot.totalAttractions > 0
    ? `${snapshot.openAttractions} von ${snapshot.totalAttractions} Attraktionen offen`
    : `${snapshot.openAttractions} Attraktionen offen`;
  return `${crowd}, ${attractions}`;
}

function toTrendSnapshot(snapshot) {
  return {
    capturedAtMillis: snapshot.capturedAtMillis,
    apiCrowdLevel: snapshot.apiCrowdLevel,
    calculatedCrowdLevel: snapshot.calculatedCrowdLevel,
    displayCrowdLevel: snapshot.displayCrowdLevel,
    openedToday: snapshot.openedToday,
    openAttractions: snapshot.openAttractions,
    totalAttractions: snapshot.totalAttractions,
  };
}

function toLatestParkSnapshot(snapshot) {
  return snapshot;
}

async function buildUpdatedAttractionHistory(env, snapshot, now) {
  const date = isoDate(now);
  const existing = await readAttractionDay(env, snapshot.parkKey, date);
  const snapshots = [
    ...(existing.snapshots ?? []).filter((item) => Number(item.capturedAtMillis) !== snapshot.capturedAtMillis),
    {
      capturedAtMillis: snapshot.capturedAtMillis,
      openedToday: snapshot.openedToday,
      openFrom: snapshot.openFrom,
      closedFrom: snapshot.closedFrom,
      attractions: snapshot.attractions,
    },
  ]
    .sort((a, b) => Number(a.capturedAtMillis) - Number(b.capturedAtMillis))
    .slice(-MAX_ATTRACTION_SNAPSHOTS_PER_DAY);

  const openFrom = snapshot.openFrom ?? existing.openFrom ?? snapshots.find((item) => item.openFrom)?.openFrom ?? null;
  const closedFrom = snapshot.closedFrom ?? existing.closedFrom ?? snapshots.find((item) => item.closedFrom)?.closedFrom ?? null;
  const cleanedSnapshots = filterOperatingWindowSnapshots(snapshots, openFrom, closedFrom);

  return buildAttractionDayData(snapshot.parkKey, date, cleanedSnapshots, now, openFrom, closedFrom);
}

async function writeAttractionHistoryBatch(env, dayUpdates, now) {
  const byShard = new Map();
  for (const dayData of dayUpdates) {
    const shard = attractionHistoryShard(env, dayData.parkKey);
    const aggregateKey = `${dayData.date}:${shard}`;
    const aggregate = byShard.get(aggregateKey)
      ?? await readJson(env, attractionDailyKey(dayData.date, shard), emptyAttractionDaily(dayData.date, shard));
    const parks = (aggregate.parks ?? []).filter((park) => park.parkKey !== dayData.parkKey);
    parks.push(dayData);
    parks.sort((a, b) => a.parkKey.localeCompare(b.parkKey));
    byShard.set(aggregateKey, {
      generatedAtMillis: now,
      date: dayData.date,
      shard,
      parks,
    });
  }

  for (const aggregate of byShard.values()) {
    await env.APP_DATA.put(attractionDailyKey(aggregate.date, aggregate.shard), JSON.stringify(aggregate));
  }

  await maybeUpdateAttractionHistoryIndex(env, dayUpdates, now);
}

async function maybeUpdateAttractionHistoryIndex(env, dayUpdates, generatedAtMillis) {
  const index = await readJson(env, ATTRACTION_HISTORY_INDEX_KEY, emptyAttractionHistoryIndex());
  const intervalMillis = parsePositiveInt(env.APP_DATA_INDEX_UPDATE_INTERVAL_MILLIS)
    ?? DEFAULT_INDEX_UPDATE_INTERVAL_MILLIS;
  const indexAgeMillis = generatedAtMillis - Number(index.generatedAtMillis ?? 0);
  const shouldUpdate =
    !Number.isFinite(indexAgeMillis) ||
    indexAgeMillis >= intervalMillis ||
    dayUpdates.some((dayData) => {
      const park = (index.parks ?? []).find((item) => item.parkKey === dayData.parkKey);
      return !park || !(park.dates ?? []).includes(dayData.date);
    });

  if (!shouldUpdate) return;

  const parks = [...(index.parks ?? [])];

  for (const dayData of dayUpdates) {
    const existingIndex = parks.findIndex((park) => park.parkKey === dayData.parkKey);
    const existing = existingIndex >= 0 ? parks[existingIndex] : { parkKey: dayData.parkKey, dates: [] };
    const dates = [...new Set([...(existing.dates ?? []), dayData.date])].sort();
    const updated = {
      parkKey: dayData.parkKey,
      dates,
      latestDate: dates[dates.length - 1] ?? dayData.date,
      attractionCount: dayData.attractions.length,
      sampleCount: dayData.snapshots.length,
      updatedAtMillis: dayData.generatedAtMillis,
      attractions: mergeAttractionIndex(existing.attractions ?? [], dayData),
    };
    if (existingIndex >= 0) {
      parks[existingIndex] = updated;
    } else {
      parks.push(updated);
    }
  }

  parks.sort((a, b) => a.parkKey.localeCompare(b.parkKey));
  await env.APP_DATA.put(
    ATTRACTION_HISTORY_INDEX_KEY,
    JSON.stringify({ generatedAtMillis, parks }),
  );
}

function mergeAttractionIndex(existingAttractions, dayData) {
  const byId = new Map(existingAttractions.map((item) => [item.id, item]));
  for (const attraction of dayData.attractions) {
    const existing = byId.get(attraction.id) ?? {};
    byId.set(attraction.id, {
      id: attraction.id,
      name: attraction.name,
      latestDate: dayData.date,
      sampleCount: Math.max(Number(existing.sampleCount ?? 0), attraction.sampleCount ?? 0),
      averageWaitMinutes: attraction.averageWaitMinutes ?? existing.averageWaitMinutes ?? null,
      lastValue: attraction.lastValue ?? existing.lastValue ?? null,
      lastStatusCode: attraction.lastStatusCode ?? existing.lastStatusCode ?? null,
    });
  }
  return [...byId.values()].sort((a, b) => String(a.name).localeCompare(String(b.name)));
}

function buildAttractionDayData(parkKey, date, snapshots, generatedAtMillis, openFrom = null, closedFrom = null) {
  const attractionsById = new Map();
  for (const snapshot of snapshots) {
    for (const item of snapshot.attractions ?? []) {
      const existing = attractionsById.get(item.id) ?? {
        id: item.id,
        name: item.name,
        values: [],
        statusCodes: [],
      };
      existing.name = item.name || existing.name;
      existing.values.push(item.value);
      existing.statusCodes.push(item.statusCode);
      attractionsById.set(item.id, existing);
    }
  }

  const attractions = [...attractionsById.values()]
    .map((item) => {
      const openValues = item.values.filter((value) => value >= 0);
      return {
        id: item.id,
        name: item.name,
        sampleCount: item.values.length,
        openSampleCount: openValues.length,
        closedSampleCount: item.values.length - openValues.length,
        averageWaitMinutes: openValues.length > 0 ? round(openValues.reduce((sum, value) => sum + value, 0) / openValues.length, 1) : null,
        minWaitMinutes: openValues.length > 0 ? Math.min(...openValues) : null,
        maxWaitMinutes: openValues.length > 0 ? Math.max(...openValues) : null,
        lastValue: item.values[item.values.length - 1] ?? null,
        lastStatusCode: item.statusCodes[item.statusCodes.length - 1] ?? null,
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name));

  return {
    generatedAtMillis,
    parkKey,
    date,
    openFrom: openFrom ?? snapshots.find((snapshot) => snapshot.openFrom)?.openFrom ?? null,
    closedFrom: closedFrom ?? snapshots.find((snapshot) => snapshot.closedFrom)?.closedFrom ?? null,
    snapshots,
    attractions,
  };
}

function filterOperatingWindowSnapshots(snapshots, openFrom, closedFrom) {
  const openAtMillis = parseDateMillis(openFrom);
  const closeAtMillis = parseDateMillis(closedFrom);
  if (openAtMillis == null && closeAtMillis == null) return snapshots;
  const firstOpenAttractionMillis = snapshots
    .filter((snapshot) => (snapshot.attractions ?? []).some((attraction) => Number(attraction.statusCode) === 0 && Number(attraction.value) >= 0))
    .map((snapshot) => Number(snapshot.capturedAtMillis))
    .filter(Number.isFinite)
    .sort((a, b) => a - b)[0];
  const startAtMillis = openAtMillis != null && firstOpenAttractionMillis != null
    ? Math.min(openAtMillis, firstOpenAttractionMillis)
    : (openAtMillis ?? firstOpenAttractionMillis);

  return snapshots.filter((snapshot) => {
    const capturedAtMillis = Number(snapshot.capturedAtMillis);
    if (!Number.isFinite(capturedAtMillis)) return false;
    if (startAtMillis != null && capturedAtMillis < startAtMillis) return false;
    if (closeAtMillis != null && capturedAtMillis > closeAtMillis) return false;
    return true;
  });
}

function parseDateMillis(value) {
  if (!value) return null;
  const millis = Date.parse(value);
  return Number.isFinite(millis) ? millis : null;
}

function toAttractionSnapshotItem(item) {
  const id = item.id || item.uuid || stableAttractionId(item.name);
  const status = String(item.status ?? "").toLowerCase();
  const waitingTime = Number(item.waitingtime ?? item.waitingTime ?? item.wait_time);
  const statusCode = attractionStatusCode(status, waitingTime);
  const openValue = Number.isFinite(waitingTime) ? Math.max(0, Math.round(waitingTime)) : 0;
  return {
    id,
    name: item.name || id,
    value: statusCode === 0 ? openValue : statusCode,
    statusCode,
    status: status || "unknown",
  };
}

function attractionStatusCode(status, waitingTime = NaN) {
  if (status === "opened" || status === "open") return 0;
  if (status === "closedweather" || status === "closed_weather" || status === "weather") return -2;
  if (status === "maintenance") return -3;
  if (status === "closed") return -1;
  if (Number.isFinite(waitingTime) && waitingTime >= 0) return 0;
  return -4;
}

function parseCrowdLevel(value) {
  if (value == null) return null;
  const parsed = Number(String(value).replace(",", "."));
  return Number.isFinite(parsed) ? Math.max(0, Math.min(100, parsed)) : null;
}

async function readJson(env, key, fallback) {
  const value = await env.APP_DATA.get(key);
  if (!value) return fallback;
  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

async function readAttractionDay(env, parkKey, date) {
  const aggregate = await readJson(env, attractionDailyKey(date, attractionHistoryShard(env, parkKey)), null);
  const day = aggregate?.parks?.find((park) => park.parkKey === parkKey);
  if (day) return day;
  const unshardedAggregate = await readJson(env, attractionDailyKey(date), null);
  const unshardedDay = unshardedAggregate?.parks?.find((park) => park.parkKey === parkKey);
  if (unshardedDay) return unshardedDay;
  return readJson(env, attractionDayKey(parkKey, date), emptyAttractionDay(parkKey, date));
}

async function readGlobalMarkers(env) {
  const date = isoDate(Date.now());
  const shardCount = parsePositiveInt(env.APP_DATA_HISTORY_SHARDS) ?? DEFAULT_ATTRACTION_HISTORY_SHARDS;
  const markers = [];

  for (let shard = 0; shard < shardCount; shard += 1) {
    const aggregate = await readJson(env, attractionDailyKey(date, shard), emptyAttractionDaily(date, shard));
    for (const park of aggregate.parks ?? []) {
      const latestSnapshot = (park.snapshots ?? [])
        .slice()
        .sort((a, b) => Number(b.capturedAtMillis) - Number(a.capturedAtMillis))[0];
      if (!latestSnapshot) continue;

      const attractions = latestSnapshot.attractions ?? [];
      const openAttractions = attractions.filter((attraction) => Number(attraction.statusCode) === 0).length;
      markers.push({
        parkKey: park.parkKey,
        capturedAtMillis: Number(latestSnapshot.capturedAtMillis) || park.generatedAtMillis || 0,
        openedToday: latestSnapshot.openedToday === true,
        openFrom: latestSnapshot.openFrom ?? park.openFrom ?? null,
        closedFrom: latestSnapshot.closedFrom ?? park.closedFrom ?? null,
        openAttractions,
        totalAttractions: attractions.length,
        attractionCount: attractions.length,
      });
    }
  }

  markers.sort((a, b) => a.parkKey.localeCompare(b.parkKey));
  return {
    generatedAtMillis: Date.now(),
    date,
    markers,
  };
}

function emptyLatest() {
  return { generatedAtMillis: 0, parks: [], recommendations: [], errors: [] };
}

function emptyTrendHistory() {
  return { generatedAtMillis: 0, parks: [] };
}

function emptyAttractionHistoryIndex() {
  return { generatedAtMillis: 0, parks: [] };
}

function emptyAttractionDay(parkKey, date) {
  return { generatedAtMillis: 0, parkKey, date, openFrom: null, closedFrom: null, snapshots: [], attractions: [] };
}

function emptyAttractionDaily(date, shard = 0) {
  return { generatedAtMillis: 0, date, shard, parks: [] };
}

function attractionDayKey(parkKey, date) {
  return `${ATTRACTION_HISTORY_PREFIX}/${parkKey}/${date}.json`;
}

function attractionDailyKey(date, shard = null) {
  return shard == null
    ? `${ATTRACTION_HISTORY_DAILY_PREFIX}/${date}.json`
    : `${ATTRACTION_HISTORY_DAILY_PREFIX}/${date}/shard-${shard}.json`;
}

function stableAttractionId(name) {
  return String(name || "unknown")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function isoDate(timestampMillis) {
  return new Date(timestampMillis).toISOString().slice(0, 10);
}

function round(value, decimals) {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}

function jsonResponse(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "public, max-age=60",
      "access-control-allow-origin": "*",
    },
  });
}

function parseParkKeys(value) {
  return String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function parsePositiveInt(value) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function attractionHistoryShard(env, parkKey, explicitShardCount = null) {
  const shardCount = explicitShardCount
    ?? parsePositiveInt(env.APP_DATA_HISTORY_SHARDS)
    ?? DEFAULT_ATTRACTION_HISTORY_SHARDS;
  return hashString(parkKey) % shardCount;
}

function cronParkShard(parkKey, shardCount) {
  return hashString(parkKey) % shardCount;
}

function scheduledShardIndex(cron, shardCount) {
  const configured = String(cron || "");
  const offset = Number(configured.match(/^(\d+)-59\/5 /)?.[1]);
  if (Number.isInteger(offset)) return Math.max(0, Math.min(shardCount - 1, offset));
  return 0;
}

function hashString(value) {
  let hash = 0;
  for (let index = 0; index < String(value).length; index += 1) {
    hash = ((hash << 5) - hash + String(value).charCodeAt(index)) | 0;
  }
  return Math.abs(hash);
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
