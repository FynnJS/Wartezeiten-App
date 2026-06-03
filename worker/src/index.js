const WARTEZEITEN_API_BASE = "https://api.wartezeiten.app/v1";
const LATEST_KEY = "app-data/latest.json";
const TREND_KEY = "app-data/trend-history.json";
const MAX_HISTORY_AGE_MILLIS = 48 * 60 * 60 * 1000;
const MAX_HISTORY_POINTS_PER_PARK = 96;
const REQUEST_DELAY_MILLIS = 900;

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

  async scheduled(_controller, env, ctx) {
    ctx.waitUntil(updateAppData(env));
  },
};

async function updateAppData(env) {
  const now = Date.now();
  const parkKeys = parseParkKeys(env.APP_DATA_PARK_KEYS);
  const existingHistory = await readJson(env, TREND_KEY, emptyTrendHistory());
  const historyByPark = new Map(
    existingHistory.parks.map((park) => [park.parkKey, park.snapshots ?? []]),
  );

  const parks = [];
  const recommendations = [];
  const errors = [];

  for (const parkKey of parkKeys) {
    try {
      const snapshot = await collectParkSnapshot(parkKey, now);
      parks.push(snapshot);

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

  await env.APP_DATA.put(LATEST_KEY, JSON.stringify(latest));
  await env.APP_DATA.put(TREND_KEY, JSON.stringify(trendHistory));
  return { ok: true, generatedAtMillis: now, parks: parks.length, errors };
}

async function collectParkSnapshot(parkKey, now) {
  const [openingTimes, waitingTimes, crowdLevel] = await Promise.all([
    apiJson("/openingtimes", { park: parkKey }),
    apiJson("/waitingtimes", { park: parkKey, language: "de" }),
    apiJson("/crowdlevel", { park: parkKey }),
  ]);

  const opening = Array.isArray(openingTimes) ? openingTimes[0] : null;
  const openedToday = opening?.opened_today === true;
  const openAttractions = Array.isArray(waitingTimes)
    ? waitingTimes.filter((item) => String(item.status ?? "").toLowerCase() === "opened").length
    : 0;
  const totalAttractions = Array.isArray(waitingTimes) ? waitingTimes.length : 0;
  const apiCrowdLevel = parseCrowdLevel(crowdLevel?.crowd_level);
  const displayCrowdLevel = openedToday && openAttractions > 0 ? apiCrowdLevel : null;

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
  };
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

function emptyLatest() {
  return { generatedAtMillis: 0, parks: [], recommendations: [], errors: [] };
}

function emptyTrendHistory() {
  return { generatedAtMillis: 0, parks: [] };
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
  return String(value || DEFAULT_PARK_KEYS.join(","))
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
