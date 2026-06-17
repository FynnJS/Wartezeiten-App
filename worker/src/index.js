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
const DEFAULT_PUSH_SCAN_LIMIT = 1000;
const D1_SCHEMA_VERSION = 1;
let cachedFcmAccessToken = null;

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

    if (request.method === "OPTIONS") {
      return jsonResponse({ ok: true });
    }

    if (url.pathname === "/app-data/latest.json") {
      return jsonResponse(await readJson(env, LATEST_KEY, emptyLatest()));
    }

    if (url.pathname === "/app-data/trend-history.json") {
      const parkKey = cleanString(url.searchParams.get("parkKey"), 160) || null;
      return jsonResponse(await readTrendHistory(env, parkKey));
    }

    if (url.pathname === GLOBAL_MARKERS_PATH) {
      return jsonResponse(await readGlobalMarkers(env));
    }

    if (url.pathname === "/app-data/statistics/index.json") {
      return jsonResponse(await readStatisticsIndex(env));
    }

    const datesMatch = url.pathname.match(/^\/app-data\/statistics\/parks\/([^/]+)\/dates\.json$/);
    if (datesMatch) {
      const parkKey = decodeURIComponent(datesMatch[1]);
      const index = await readStatisticsIndex(env);
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

    if (url.pathname === "/push/register" && request.method === "POST") {
      return pushJsonResponse(registerPushInstallation, env, request);
    }

    if (url.pathname === "/push/status" && request.method === "GET") {
      const d1Configured = hasD1(env);
      const fcmConfigured = hasFcmConfig(env);
      return jsonResponse({
        ok: true,
        d1Configured,
        fcmConfigured,
        pushReady: d1Configured && fcmConfigured,
      });
    }

    if (url.pathname === "/push/watchlist" && request.method === "POST") {
      return pushJsonResponse(syncPushWatchlist, env, request);
    }

    if (url.pathname === "/push/unregister" && request.method === "POST") {
      return pushJsonResponse(unregisterPushInstallation, env, request);
    }

    if (env.ASSETS) {
      return env.ASSETS.fetch(request);
    }
    return new Response("Not found", { status: 404 });
  },

  async scheduled(controller, env, ctx) {
    if (controller?.cron === "* * * * *") {
      ctx.waitUntil(runPushWatchlistScan(env));
      return;
    }
    ctx.waitUntil(updateScheduledAppData(controller, env));
  },
};

async function updateScheduledAppData(controller, env) {
  const cronShardCount = parsePositiveInt(env.APP_DATA_CRON_SHARDS) ?? DEFAULT_CRON_SHARDS;
  const historyShardCount = parsePositiveInt(env.APP_DATA_HISTORY_SHARDS) ?? DEFAULT_ATTRACTION_HISTORY_SHARDS;
  const cronShardIndex = scheduledShardIndex(controller?.cron, cronShardCount);
  const historyShardIndex = scheduledHistoryShardIndex(
    Number(controller?.scheduledTime) || Date.now(),
    cronShardIndex,
    cronShardCount,
    historyShardCount,
  );
  if (historyShardIndex == null) {
    return {
      ok: true,
      generatedAtMillis: Date.now(),
      parks: 0,
      totalParks: 0,
      shardIndex: cronShardIndex,
      shardCount: cronShardCount,
      historyShardIndex: null,
      historyShardCount,
      skipped: true,
      reason: "No unique history shard assigned to this cron trigger.",
      errors: [],
    };
  }
  return updateAppData(env, {
    shardIndex: cronShardIndex,
    shardCount: cronShardCount,
    historyShardIndex,
    historyShardCount,
    writeLatest: false,
    writeTrend: false,
    includeCrowd: false,
  });
}

async function updateAppData(env, options = {}) {
  const now = Date.now();
  const allParkKeys = await resolveParkKeys(env);
  const historyShardIndex = options.historyShardIndex ?? null;
  const historyShardCount = options.historyShardCount ?? null;
  const parkKeys = historyShardIndex != null
    ? allParkKeys.filter((parkKey) => attractionHistoryShard(env, parkKey, historyShardCount) === historyShardIndex)
    : options.shardIndex == null
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
        if (hasD1(env)) {
          await writeAttractionSnapshotsD1(env, [toAttractionSnapshotRow(snapshot, now)]);
        } else {
          attractionDayUpdates.push(await buildUpdatedAttractionHistory(env, snapshot, now));
        }
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
    historyShardIndex,
    historyShardCount,
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
  const opening = firstItem(openingTimes);
  const waitingItems = Array.isArray(waitingTimes) ? waitingTimes : [];
  const attractionItems = waitingItems.map((item) => toAttractionSnapshotItem(item));
  const openedToday = opening?.opened_today === true;
  const openAttractions = attractionItems.filter((item) => item.statusCode === 0).length;
  const totalAttractions = waitingItems.length;
  const apiCrowdLevel = parseCrowdLevel(crowdLevel?.crowd_level);
  const calculatedCrowdLevel = estimateCrowdLevelFromAttractions(attractionItems);
  const displayCrowdLevel = openedToday && openAttractions > 0
    ? (apiCrowdLevel ?? calculatedCrowdLevel)
    : null;
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
    calculatedCrowdLevel,
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

function firstItem(value) {
  if (Array.isArray(value)) return value[0] ?? null;
  return value && typeof value === "object" ? value : null;
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

async function registerPushInstallation(env, payload) {
  ensurePushD1(env);
  const installationId = cleanString(payload?.installationId, 160);
  const token = cleanString(payload?.token, 512);
  const language = cleanString(payload?.language, 8) === "en" ? "en" : "de";
  if (!installationId || !token) {
    return { ok: false, error: "installationId and token are required" };
  }

  await env.APP_DATA_DB.prepare(
    `
      INSERT INTO push_installations (installation_id, fcm_token, language, updated_at, disabled_at)
      VALUES (?, ?, ?, ?, NULL)
      ON CONFLICT(installation_id) DO UPDATE SET
        fcm_token = excluded.fcm_token,
        language = excluded.language,
        updated_at = excluded.updated_at,
        disabled_at = NULL
    `,
  ).bind(installationId, token, language, Date.now()).run();
  return { ok: true };
}

async function pushJsonResponse(handler, env, request) {
  try {
    const result = await handler(env, await request.json());
    return jsonResponse(result, result.ok === false ? 400 : 200);
  } catch (error) {
    return jsonResponse({
      ok: false,
      error: error instanceof Error ? error.message : String(error),
    }, 500);
  }
}

async function syncPushWatchlist(env, payload) {
  ensurePushD1(env);
  const installationId = cleanString(payload?.installationId, 160);
  const alerts = Array.isArray(payload?.alerts) ? payload.alerts : [];
  if (!installationId) {
    return { ok: false, error: "installationId is required" };
  }

  const installation = await env.APP_DATA_DB.prepare(
    "SELECT installation_id FROM push_installations WHERE installation_id = ? AND disabled_at IS NULL",
  ).bind(installationId).first();
  if (!installation) {
    return { ok: false, error: "installation is not registered" };
  }

  const now = Date.now();
  const normalizedAlerts = alerts
    .map((alert) => normalizePushAlert(alert))
    .filter(Boolean)
    .slice(0, 100);
  const existingRows = await env.APP_DATA_DB.prepare(
    "SELECT local_alert_id FROM push_watchlist_alerts WHERE installation_id = ?",
  ).bind(installationId).all();
  const nextIds = new Set(normalizedAlerts.map((alert) => alert.localAlertId));
  const statements = [];

  for (const row of existingRows.results ?? []) {
    if (!nextIds.has(row.local_alert_id)) {
      statements.push(
        env.APP_DATA_DB.prepare(
          "DELETE FROM push_watchlist_alerts WHERE installation_id = ? AND local_alert_id = ?",
        ).bind(installationId, row.local_alert_id),
      );
    }
  }

  for (const alert of normalizedAlerts) {
    statements.push(
      env.APP_DATA_DB.prepare(
        `
          INSERT INTO push_watchlist_alerts (
            installation_id, local_alert_id, park_key, attraction_id, type,
            threshold_value, notify_once, only_when_park_open, quiet_hours_enabled,
            quiet_start_minutes, quiet_end_minutes, cooldown_minutes,
            last_seen_value, last_notified_value, updated_at
          )
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?)
          ON CONFLICT(installation_id, local_alert_id) DO UPDATE SET
            park_key = excluded.park_key,
            attraction_id = excluded.attraction_id,
            type = excluded.type,
            threshold_value = excluded.threshold_value,
            notify_once = excluded.notify_once,
            only_when_park_open = excluded.only_when_park_open,
            quiet_hours_enabled = excluded.quiet_hours_enabled,
            quiet_start_minutes = excluded.quiet_start_minutes,
            quiet_end_minutes = excluded.quiet_end_minutes,
            cooldown_minutes = excluded.cooldown_minutes,
            updated_at = excluded.updated_at
        `,
      ).bind(
        installationId,
        alert.localAlertId,
        alert.parkKey,
        alert.attractionId,
        alert.type,
        alert.threshold,
        alert.notifyOnce ? 1 : 0,
        alert.onlyWhenParkOpen ? 1 : 0,
        alert.quietHoursEnabled ? 1 : 0,
        alert.quietStartMinutes,
        alert.quietEndMinutes,
        alert.cooldownMinutes,
        now,
      ),
    );
  }

  if (statements.length > 0) {
    await env.APP_DATA_DB.batch(statements);
  }
  return { ok: true, alerts: normalizedAlerts.length };
}

async function unregisterPushInstallation(env, payload) {
  ensurePushD1(env);
  const installationId = cleanString(payload?.installationId, 160);
  if (!installationId) {
    return { ok: false, error: "installationId is required" };
  }
  await env.APP_DATA_DB.batch([
    env.APP_DATA_DB.prepare("DELETE FROM push_watchlist_alerts WHERE installation_id = ?").bind(installationId),
    env.APP_DATA_DB.prepare(
      "UPDATE push_installations SET disabled_at = ?, updated_at = ? WHERE installation_id = ?",
    ).bind(Date.now(), Date.now(), installationId),
  ]);
  return { ok: true };
}

async function runPushWatchlistScan(env) {
  if (!hasD1(env) || !hasFcmConfig(env)) {
    return { ok: true, skipped: true };
  }

  const limit = parsePositiveInt(env.PUSH_SCAN_LIMIT) ?? DEFAULT_PUSH_SCAN_LIMIT;
  const rowsResult = await env.APP_DATA_DB.prepare(
    `
      SELECT
        a.installation_id,
        a.local_alert_id,
        a.park_key,
        a.attraction_id,
        a.type,
        a.threshold_value,
        a.notify_once,
        a.only_when_park_open,
        a.quiet_hours_enabled,
        a.quiet_start_minutes,
        a.quiet_end_minutes,
        a.cooldown_minutes,
        a.last_notified_at,
        a.last_seen_value,
        a.last_notified_value,
        i.fcm_token,
        i.language
      FROM push_watchlist_alerts a
      JOIN push_installations i ON i.installation_id = a.installation_id
      WHERE i.disabled_at IS NULL
      ORDER BY a.park_key, a.updated_at DESC
      LIMIT ?
    `,
  ).bind(limit).all();
  const rows = rowsResult.results ?? [];
  const groupedByPark = groupBy(rows, (row) => row.park_key);
  let scannedParks = 0;
  let sent = 0;
  const errors = [];

  for (const [parkKey, alerts] of groupedByPark.entries()) {
    try {
      const snapshot = await collectParkSnapshot(parkKey, Date.now(), { includeCrowd: true });
      scannedParks += 1;
      for (const alert of alerts) {
        if (!pushAlertDeliveryAllowed(alert, snapshot, Date.now())) continue;
        const evaluation = evaluatePushAlert(alert, snapshot);
        if (!evaluation) continue;

        if (evaluation.nextSeenValue !== alert.last_seen_value) {
          await updatePushAlertSeenValue(env, alert, evaluation.nextSeenValue);
        }
        if (!evaluation.shouldNotify) continue;

        const message = {
          title: evaluation.title,
          body: evaluation.body,
          parkKey,
          attractionId: evaluation.attractionId ?? "",
          localAlertId: alert.local_alert_id,
          notifyOnce: Number(alert.notify_once) === 1 ? "true" : "false",
        };
        const result = await sendFcmDataMessage(env, alert.fcm_token, message);
        if (result.ok) {
          sent += 1;
          await updatePushAlertNotifiedValue(env, alert, evaluation.nextSeenValue);
          if (Number(alert.notify_once) === 1) {
            await env.APP_DATA_DB.prepare(
              "DELETE FROM push_watchlist_alerts WHERE installation_id = ? AND local_alert_id = ?",
            ).bind(alert.installation_id, alert.local_alert_id).run();
          }
        } else {
          errors.push({ parkKey, installationId: alert.installation_id, error: result.error });
          if (result.disableToken) {
            await disablePushInstallation(env, alert.installation_id);
          }
        }
      }
      await delay(REQUEST_DELAY_MILLIS);
    } catch (error) {
      errors.push({ parkKey, error: error instanceof Error ? error.message : String(error) });
    }
  }

  return { ok: true, scannedParks, alerts: rows.length, sent, errors };
}

function evaluatePushAlert(alert, snapshot) {
  const type = String(alert.type || "");
  const threshold = Number(alert.threshold_value ?? 0);
  const isParkOpen = isParkOpenNow(snapshot, Date.now());
  const openAttractions = snapshot.attractions.filter((item) => item.statusCode === 0);
  const target = alert.attraction_id
    ? snapshot.attractions.find((item) => item.id === alert.attraction_id || stableAttractionId(item.name) === alert.attraction_id)
    : null;

  if (type === "NOW_OPENED") {
    return booleanEvaluation(alert, isParkOpen, `Einlass bereit: ${snapshot.parkKey}`, "Der Park ist aktuell als geoeffnet gemeldet.", null);
  }
  if (type === "PARK_STATUS_CHANGED") {
    const state = isParkOpen ? "open" : "closed";
    return valueEvaluation(alert, state, false, `${snapshot.parkKey} im Park-Ticker`, isParkOpen ? "Heute geoeffnet. Pruefe jetzt deine Route." : "Aktuell geschlossen. Plane lieber um.", null);
  }
  if (type === "CROWD_LEVEL_BELOW" || type === "CROWD_LEVEL_ABOVE") {
    const crowd = snapshot.displayCrowdLevel;
    const triggered = isParkOpen && crowd != null && (
      type === "CROWD_LEVEL_BELOW" ? crowd <= threshold : crowd >= threshold
    );
    const direction = type === "CROWD_LEVEL_BELOW" ? "unter" : "ueber";
    return booleanEvaluation(
      alert,
      triggered,
      type === "CROWD_LEVEL_BELOW" ? `Entspannter Park: ${snapshot.parkKey}` : `Andrang-Warnung: ${snapshot.parkKey}`,
      `Auslastung ${crowd == null ? "unbekannt" : `${Math.round(crowd)}%`} und damit ${direction} deinem Grenzwert.`,
      null,
    );
  }
  if (type === "PARK_ALL_CHANGES") {
    const state = [
      `open=${isParkOpen}`,
      `crowd=${snapshot.displayCrowdLevel == null ? "unknown" : Math.round(snapshot.displayCrowdLevel)}`,
      `openAttractions=${openAttractions.length}`,
      `totalAttractions=${snapshot.attractions.length}`,
    ].join("|");
    return valueEvaluation(
      alert,
      state,
      false,
      `Park-Aenderung: ${snapshot.parkKey}`,
      `${openAttractions.length} von ${snapshot.attractions.length} Attraktionen offen${snapshot.displayCrowdLevel == null ? "" : `, Auslastung ${Math.round(snapshot.displayCrowdLevel)}%`}.`,
      null,
    );
  }
  if (type === "DAILY_SUMMARY") {
    const local = parkLocalDateParts(Date.now(), snapshot.openFrom);
    if (local.hour !== 18) return null;
    const state = `summary=${local.date}`;
    return valueEvaluation(
      alert,
      state,
      true,
      `Tagesblick: ${snapshot.parkKey}`,
      `${isParkOpen ? "Park geoeffnet" : "Park geschlossen"}, ${openAttractions.length} von ${snapshot.attractions.length} Attraktionen offen${snapshot.displayCrowdLevel == null ? "" : `, Auslastung ${Math.round(snapshot.displayCrowdLevel)}%`}.`,
      null,
    );
  }
  if (type === "WAIT_TIME_BELOW" || type === "WAIT_TIME_ABOVE") {
    const candidates = target ? [target] : openAttractions;
    const selected = type === "WAIT_TIME_BELOW"
      ? candidates.filter(hasOpenWait).sort((a, b) => a.value - b.value)[0]
      : candidates.filter(hasOpenWait).sort((a, b) => b.value - a.value)[0];
    const triggered = Boolean(selected) && (
      type === "WAIT_TIME_BELOW" ? selected.value <= threshold : selected.value >= threshold
    );
    return booleanEvaluation(
      alert,
      triggered,
      type === "WAIT_TIME_BELOW" ? `Ride-Fenster: ${selected?.name ?? snapshot.parkKey}` : `Zu voll: ${selected?.name ?? snapshot.parkKey}`,
      `${selected?.name ?? "Eine Attraktion"} liegt bei ${selected?.value ?? "?"} Min.`,
      selected?.id ?? target?.id ?? null,
    );
  }
  if (
    type === "ATTRACTION_OPEN" ||
    type === "ATTRACTION_CLOSED" ||
    type === "ATTRACTION_MAINTENANCE" ||
    type === "ATTRACTION_STATUS_CHANGE" ||
    type === "ATTRACTION_ALL_CHANGES"
  ) {
    if (!target) return null;
    const status = normalizedAttractionStatus(target);
    if (type === "ATTRACTION_OPEN") {
      return booleanEvaluation(alert, status === "opened", `Wieder offen: ${target.name}`, "Wenn sie auf deiner Liste steht: jetzt hin.", target.id);
    }
    if (type === "ATTRACTION_CLOSED") {
      return booleanEvaluation(alert, status === "closed", `Gerade zu: ${target.name}`, "Spar dir den Weg und nimm eine Alternative.", target.id);
    }
    if (type === "ATTRACTION_MAINTENANCE") {
      return booleanEvaluation(alert, status === "maintenance", `Technikpause: ${target.name}`, "Plane die Attraktion spaeter nochmal ein.", target.id);
    }
    const state = type === "ATTRACTION_ALL_CHANGES" ? `${status}|wait=${target.value}` : status;
    return valueEvaluation(
      alert,
      state,
      type === "ATTRACTION_STATUS_CHANGE" && status === "opened",
      type === "ATTRACTION_ALL_CHANGES" ? `Aenderung: ${target.name}` : `Status-Radar: ${target.name}`,
      type === "ATTRACTION_ALL_CHANGES" ? `${target.name}: ${status}, ${hasOpenWait(target) ? `${target.value} Min.` : "keine Wartezeit"}` : readablePushStatus(status),
      type === "ATTRACTION_ALL_CHANGES" ? null : target.id,
    );
  }
  return null;
}

function booleanEvaluation(alert, triggered, title, body, attractionId) {
  const nextSeenValue = String(Boolean(triggered));
  return {
    nextSeenValue,
    shouldNotify: Boolean(triggered) && alert.last_seen_value !== nextSeenValue,
    title,
    body,
    attractionId,
  };
}

function valueEvaluation(alert, nextSeenValue, notifyOnFirstMatch, title, body, attractionId) {
  return {
    nextSeenValue,
    shouldNotify: (alert.last_seen_value != null && alert.last_seen_value !== nextSeenValue) ||
      (alert.last_seen_value == null && notifyOnFirstMatch),
    title,
    body,
    attractionId,
  };
}

async function updatePushAlertSeenValue(env, alert, value) {
  await env.APP_DATA_DB.prepare(
    "UPDATE push_watchlist_alerts SET last_seen_value = ?, updated_at = ? WHERE installation_id = ? AND local_alert_id = ?",
  ).bind(value, Date.now(), alert.installation_id, alert.local_alert_id).run();
}

async function updatePushAlertNotifiedValue(env, alert, value) {
  await env.APP_DATA_DB.prepare(
    "UPDATE push_watchlist_alerts SET last_seen_value = ?, last_notified_value = ?, last_notified_at = ?, updated_at = ? WHERE installation_id = ? AND local_alert_id = ?",
  ).bind(value, value, Date.now(), Date.now(), alert.installation_id, alert.local_alert_id).run();
}

function pushAlertDeliveryAllowed(alert, snapshot, now) {
  if (Number(alert.only_when_park_open) === 1 && !isParkOpenNow(snapshot, now)) return false;
  const lastNotifiedAt = Number(alert.last_notified_at ?? 0);
  const cooldownMillis = Math.max(0, Number(alert.cooldown_minutes ?? 0)) * 60 * 1000;
  if (lastNotifiedAt > 0 && now - lastNotifiedAt < cooldownMillis) return false;
  if (Number(alert.quiet_hours_enabled) !== 1) return true;

  const offsetMinutes = openingOffsetMinutes(snapshot.openFrom) ?? 0;
  const localMinutes = ((Math.floor(now / 60000) + offsetMinutes) % 1440 + 1440) % 1440;
  const start = Number(alert.quiet_start_minutes ?? 1320);
  const end = Number(alert.quiet_end_minutes ?? 480);
  return start <= end
    ? !(localMinutes >= start && localMinutes < end)
    : !(localMinutes >= start || localMinutes < end);
}

function openingOffsetMinutes(value) {
  const match = String(value || "").match(/([+-])(\d{2}):(\d{2})$/);
  if (!match) return null;
  const minutes = Number(match[2]) * 60 + Number(match[3]);
  return match[1] === "-" ? -minutes : minutes;
}

function parkLocalDateParts(now, openingOffsetSource) {
  const offsetMinutes = openingOffsetMinutes(openingOffsetSource) ?? 0;
  const local = new Date(now + offsetMinutes * 60 * 1000);
  return {
    date: local.toISOString().slice(0, 10),
    hour: local.getUTCHours(),
  };
}

async function disablePushInstallation(env, installationId) {
  await env.APP_DATA_DB.prepare(
    "UPDATE push_installations SET disabled_at = ?, updated_at = ? WHERE installation_id = ?",
  ).bind(Date.now(), Date.now(), installationId).run();
}

async function sendFcmDataMessage(env, token, payload) {
  const accessToken = await getFcmAccessToken(env);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FCM_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json; charset=utf-8",
    },
    body: JSON.stringify({
      message: {
        token,
        data: Object.fromEntries(
          Object.entries(payload).map(([key, value]) => [key, String(value ?? "")]),
        ),
        android: {
          priority: "HIGH",
        },
      },
    }),
  });

  if (response.ok) return { ok: true };
  const errorText = await response.text();
  return {
    ok: false,
    error: `FCM HTTP ${response.status}: ${errorText.slice(0, 200)}`,
    disableToken: response.status === 400 || response.status === 404,
  };
}

async function getFcmAccessToken(env) {
  const nowSeconds = Math.floor(Date.now() / 1000);
  if (cachedFcmAccessToken && cachedFcmAccessToken.expiresAtSeconds - 60 > nowSeconds) {
    return cachedFcmAccessToken.token;
  }

  const assertion = await createFcmJwt(env, nowSeconds);
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!response.ok) {
    throw new Error(`FCM OAuth failed with HTTP ${response.status}: ${(await response.text()).slice(0, 200)}`);
  }
  const body = await response.json();
  cachedFcmAccessToken = {
    token: body.access_token,
    expiresAtSeconds: nowSeconds + Number(body.expires_in ?? 3600),
  };
  return cachedFcmAccessToken.token;
}

async function createFcmJwt(env, nowSeconds) {
  const header = base64UrlString(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64UrlString(JSON.stringify({
    iss: env.FCM_CLIENT_EMAIL,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  }));
  const signingInput = `${header}.${payload}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(env.FCM_PRIVATE_KEY),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  return `${signingInput}.${base64UrlBytes(new Uint8Array(signature))}`;
}

function normalizePushAlert(alert) {
  const localAlertId = cleanString(alert?.localAlertId, 80);
  const parkKey = cleanString(alert?.parkKey, 160);
  const type = cleanString(alert?.type, 80);
  if (!localAlertId || !parkKey || !type) return null;
  return {
    localAlertId,
    parkKey,
    attractionId: cleanString(alert?.attractionId, 160) || null,
    type,
    threshold: Math.max(0, Math.min(999, Math.round(Number(alert?.threshold ?? 0) || 0))),
    notifyOnce: Boolean(alert?.notifyOnce),
    onlyWhenParkOpen: alert?.onlyWhenParkOpen !== false,
    quietHoursEnabled: Boolean(alert?.quietHoursEnabled),
    quietStartMinutes: clampMinutes(alert?.quietStartMinutes, 1320),
    quietEndMinutes: clampMinutes(alert?.quietEndMinutes, 480),
    cooldownMinutes: Math.max(0, Math.min(1440, Math.round(Number(alert?.cooldownMinutes ?? 30) || 0))),
  };
}

function clampMinutes(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.max(0, Math.min(1439, Math.round(parsed))) : fallback;
}

function isParkOpenNow(snapshot, now) {
  if (!snapshot.openedToday) return false;
  const openAt = Date.parse(snapshot.openFrom);
  const closeAt = Date.parse(snapshot.closedFrom);
  if (Number.isFinite(openAt) && now < openAt) return false;
  if (Number.isFinite(closeAt) && now > closeAt) return false;
  return true;
}

function hasOpenWait(item) {
  return item && item.statusCode === 0 && Number.isFinite(item.value) && item.value >= 0;
}

function normalizedAttractionStatus(item) {
  if (item.statusCode === 0) return "opened";
  if (item.statusCode === -3) return "maintenance";
  if (item.statusCode === -1 || item.statusCode === -2) return "closed";
  return String(item.status || "unknown").toLowerCase();
}

function readablePushStatus(status) {
  if (status === "opened") return "Wieder offen. Wenn sie auf deiner Liste steht: jetzt hin.";
  if (status === "closed") return "Gerade geschlossen. Spar dir den Weg und nimm eine Alternative.";
  if (status === "maintenance") return "Technikpause gemeldet. Plane die Attraktion spaeter nochmal ein.";
  return `Status geaendert: ${status}`;
}

function hasFcmConfig(env) {
  return Boolean(env.FCM_PROJECT_ID && env.FCM_CLIENT_EMAIL && env.FCM_PRIVATE_KEY);
}

function ensurePushD1(env) {
  if (!hasD1(env)) {
    throw new Error("APP_DATA_DB is required for push alerts");
  }
}

function cleanString(value, maxLength) {
  return String(value ?? "").trim().slice(0, maxLength);
}

function groupBy(items, keySelector) {
  const grouped = new Map();
  for (const item of items) {
    const key = keySelector(item);
    const list = grouped.get(key) ?? [];
    list.push(item);
    grouped.set(key, list);
  }
  return grouped;
}

function pemToArrayBuffer(pem) {
  const normalized = String(pem || "")
    .replace(/\\n/g, "\n")
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(normalized);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function base64UrlString(value) {
  return btoa(value).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlBytes(bytes) {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
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
    openFrom: snapshot.openFrom,
    closedFrom: snapshot.closedFrom,
    openAttractions: snapshot.openAttractions,
    totalAttractions: snapshot.totalAttractions,
  };
}

async function readTrendHistory(env, parkKey = null) {
  const legacyTrend = await readJson(env, TREND_KEY, emptyTrendHistory());
  const filteredLegacyTrend = filterTrendHistoryByPark(legacyTrend, parkKey);
  if (!hasD1(env)) return filteredLegacyTrend;

  const d1Trend = await readTrendHistoryD1(env, Date.now(), parkKey);
  return mergeTrendHistory(filteredLegacyTrend, d1Trend);
}

async function readTrendHistoryD1(env, now, parkKey = null) {
  const minCapturedAt = now - MAX_HISTORY_AGE_MILLIS;
  const parkFilter = parkKey ? " AND park_key = ?" : "";
  const statement = env.APP_DATA_DB.prepare(`
    SELECT
      park_key,
      captured_at_millis,
      generated_at_millis,
      opened_today,
      open_from,
      closed_from,
      attractions_json
    FROM attraction_history_snapshots
    WHERE captured_at_millis >= ?${parkFilter}
    ORDER BY park_key, captured_at_millis
  `);
  const result = parkKey
    ? await statement.bind(minCapturedAt, parkKey).all()
    : await statement.bind(minCapturedAt).all();

  const byPark = new Map();
  let generatedAtMillis = 0;
  for (const row of result.results ?? []) {
    const parkKey = String(row.park_key ?? "");
    if (!parkKey) continue;
    const attractions = parseJsonArray(row.attractions_json);
    const openAttractions = attractions.filter((attraction) => Number(attraction.statusCode) === 0).length;
    const calculatedCrowdLevel = estimateCrowdLevelFromAttractions(attractions);
    const openedToday = Number(row.opened_today ?? 0) === 1;
    const displayCrowdLevel = openedToday && openAttractions > 0 ? calculatedCrowdLevel : null;
    if (displayCrowdLevel == null) continue;

    const capturedAtMillis = Number(row.captured_at_millis ?? 0);
    const snapshots = byPark.get(parkKey) ?? [];
    snapshots.push({
      capturedAtMillis,
      apiCrowdLevel: null,
      calculatedCrowdLevel,
      displayCrowdLevel,
      openedToday,
      openFrom: row.open_from ?? null,
      closedFrom: row.closed_from ?? null,
      openAttractions,
      totalAttractions: attractions.length,
    });
    byPark.set(parkKey, snapshots);
    generatedAtMillis = Math.max(generatedAtMillis, Number(row.generated_at_millis ?? 0), capturedAtMillis);
  }

  return {
    generatedAtMillis,
    parks: [...byPark.entries()].map(([parkKey, snapshots]) => ({
      parkKey,
      snapshots: snapshots
        .sort((a, b) => Number(a.capturedAtMillis) - Number(b.capturedAtMillis))
        .slice(-MAX_HISTORY_POINTS_PER_PARK),
    })),
  };
}

function filterTrendHistoryByPark(trend, parkKey) {
  if (!parkKey) return trend;
  return {
    generatedAtMillis: Number(trend.generatedAtMillis ?? 0),
    parks: (trend.parks ?? []).filter((park) => park.parkKey === parkKey),
  };
}

function mergeTrendHistory(legacyTrend, d1Trend) {
  const byPark = new Map();
  for (const trend of [legacyTrend, d1Trend]) {
    for (const park of trend.parks ?? []) {
      const existing = byPark.get(park.parkKey) ?? [];
      const byCapture = new Map(existing.map((snapshot) => [Number(snapshot.capturedAtMillis), snapshot]));
      for (const snapshot of park.snapshots ?? []) {
        byCapture.set(Number(snapshot.capturedAtMillis), snapshot);
      }
      byPark.set(
        park.parkKey,
        [...byCapture.values()]
          .sort((a, b) => Number(a.capturedAtMillis) - Number(b.capturedAtMillis))
          .slice(-MAX_HISTORY_POINTS_PER_PARK),
      );
    }
  }
  return {
    generatedAtMillis: Math.max(
      Number(legacyTrend.generatedAtMillis ?? 0),
      Number(d1Trend.generatedAtMillis ?? 0),
    ),
    parks: [...byPark.entries()]
      .filter(([, snapshots]) => snapshots.length > 0)
      .map(([parkKey, snapshots]) => ({ parkKey, snapshots }))
      .sort((a, b) => a.parkKey.localeCompare(b.parkKey)),
  };
}

function toLatestParkSnapshot(snapshot) {
  return snapshot;
}

function toAttractionSnapshotRow(snapshot, generatedAtMillis) {
  return {
    generatedAtMillis,
    parkKey: snapshot.parkKey,
    date: isoDate(snapshot.capturedAtMillis),
    capturedAtMillis: snapshot.capturedAtMillis,
    openedToday: snapshot.openedToday === true,
    openFrom: snapshot.openFrom ?? null,
    closedFrom: snapshot.closedFrom ?? null,
    attractions: snapshot.attractions,
  };
}

async function writeAttractionSnapshotsD1(env, snapshots) {
  const db = env.APP_DATA_DB;
  if (!db || snapshots.length === 0) return;

  const statements = [];
  for (const snapshot of snapshots) {
    statements.push(
      db.prepare(`
        INSERT INTO attraction_history_days (
          park_key,
          date,
          generated_at_millis,
          open_from,
          closed_from,
          schema_version
        )
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(park_key, date) DO UPDATE SET
          generated_at_millis = max(attraction_history_days.generated_at_millis, excluded.generated_at_millis),
          open_from = coalesce(excluded.open_from, attraction_history_days.open_from),
          closed_from = coalesce(excluded.closed_from, attraction_history_days.closed_from),
          schema_version = excluded.schema_version
      `).bind(
        snapshot.parkKey,
        snapshot.date,
        snapshot.generatedAtMillis,
        snapshot.openFrom,
        snapshot.closedFrom,
        D1_SCHEMA_VERSION,
      ),
      db.prepare(`
        INSERT OR REPLACE INTO attraction_history_snapshots (
          park_key,
          date,
          captured_at_millis,
          generated_at_millis,
          opened_today,
          open_from,
          closed_from,
          attractions_json
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      `).bind(
        snapshot.parkKey,
        snapshot.date,
        snapshot.capturedAtMillis,
        snapshot.generatedAtMillis,
        snapshot.openedToday ? 1 : 0,
        snapshot.openFrom,
        snapshot.closedFrom,
        JSON.stringify(snapshot.attractions),
      ),
    );
  }
  await db.batch(statements);
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

async function readStatisticsIndex(env) {
  const legacyIndex = await readJson(env, ATTRACTION_HISTORY_INDEX_KEY, emptyAttractionHistoryIndex());
  if (!hasD1(env)) return legacyIndex;

  const d1Index = await readStatisticsIndexD1(env);
  return mergeStatisticsIndexes(legacyIndex, d1Index);
}

async function readStatisticsIndexD1(env) {
  const result = await env.APP_DATA_DB.prepare(`
    SELECT
      d.park_key AS parkKey,
      d.date AS date,
      d.generated_at_millis AS generatedAtMillis,
      COUNT(s.captured_at_millis) AS sampleCount
    FROM attraction_history_days d
    LEFT JOIN attraction_history_snapshots s
      ON s.park_key = d.park_key AND s.date = d.date
    GROUP BY d.park_key, d.date
    ORDER BY d.park_key, d.date
  `).all();

  const byPark = new Map();
  let generatedAtMillis = 0;
  for (const row of result.results ?? []) {
    const parkKey = String(row.parkKey ?? "");
    const date = String(row.date ?? "");
    if (!parkKey || !date) continue;
    const existing = byPark.get(parkKey) ?? {
      parkKey,
      dates: [],
      latestDate: null,
      attractionCount: 0,
      sampleCount: 0,
      updatedAtMillis: 0,
      attractions: [],
    };
    existing.dates.push(date);
    const updatedAtMillis = Number(row.generatedAtMillis ?? 0);
    generatedAtMillis = Math.max(generatedAtMillis, updatedAtMillis);
    if (existing.latestDate == null || date > existing.latestDate) {
      existing.latestDate = date;
      existing.sampleCount = Number(row.sampleCount ?? 0);
      existing.updatedAtMillis = updatedAtMillis;
    }
    byPark.set(parkKey, existing);
  }

  const parks = [];
  for (const park of byPark.values()) {
    const latestDay = park.latestDate
      ? await readAttractionDayD1(env, park.parkKey, park.latestDate)
      : null;
    parks.push({
      ...park,
      dates: [...new Set(park.dates)].sort(),
      attractionCount: latestDay?.attractions?.length ?? 0,
      attractions: (latestDay?.attractions ?? []).map((attraction) => ({
        id: attraction.id,
        name: attraction.name,
        latestDate: park.latestDate,
        sampleCount: attraction.sampleCount,
        averageWaitMinutes: attraction.averageWaitMinutes,
        lastValue: attraction.lastValue,
        lastStatusCode: attraction.lastStatusCode,
      })),
    });
  }

  parks.sort((a, b) => a.parkKey.localeCompare(b.parkKey));
  return { generatedAtMillis, parks };
}

function mergeStatisticsIndexes(legacyIndex, d1Index) {
  const byPark = new Map();
  for (const index of [legacyIndex, d1Index]) {
    for (const park of index.parks ?? []) {
      const existing = byPark.get(park.parkKey);
      if (!existing) {
        byPark.set(park.parkKey, {
          ...park,
          dates: [...new Set(park.dates ?? [])].sort(),
          attractions: park.attractions ?? [],
        });
        continue;
      }
      const dates = [...new Set([...(existing.dates ?? []), ...(park.dates ?? [])])].sort();
      const useParkForLatest = String(park.latestDate ?? "") >= String(existing.latestDate ?? "");
      byPark.set(park.parkKey, {
        ...existing,
        dates,
        latestDate: useParkForLatest ? park.latestDate : existing.latestDate,
        attractionCount: useParkForLatest ? park.attractionCount : existing.attractionCount,
        sampleCount: useParkForLatest ? park.sampleCount : existing.sampleCount,
        updatedAtMillis: Math.max(Number(existing.updatedAtMillis ?? 0), Number(park.updatedAtMillis ?? 0)),
        attractions: useParkForLatest && (park.attractions ?? []).length > 0
          ? park.attractions
          : (existing.attractions ?? []),
      });
    }
  }
  return {
    generatedAtMillis: Math.max(
      Number(legacyIndex.generatedAtMillis ?? 0),
      Number(d1Index.generatedAtMillis ?? 0),
    ),
    parks: [...byPark.values()].sort((a, b) => a.parkKey.localeCompare(b.parkKey)),
  };
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

function estimateCrowdLevelFromAttractions(attractions) {
  const waits = (attractions ?? [])
    .filter((attraction) => Number(attraction.statusCode) === 0)
    .map((attraction) => Number(attraction.value))
    .filter((value) => Number.isFinite(value) && value >= 0)
    .map((value) => Math.min(value, 120))
    .sort((a, b) => a - b);
  if (waits.length < 3) return null;

  const averageWait = waits.reduce((sum, value) => sum + value, 0) / waits.length;
  const p75Wait = waits[Math.floor((waits.length - 1) * 0.75)];
  const estimated = ((averageWait / 60) * 0.7 + (p75Wait / 90) * 0.3) * 100;
  return roundToNearestFive(Math.max(0, Math.min(100, estimated)));
}

function roundToNearestFive(value) {
  return Math.max(0, Math.min(100, Math.round(value / 5) * 5));
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
  if (hasD1(env)) {
    const d1Day = await readAttractionDayD1(env, parkKey, date);
    if ((d1Day.snapshots ?? []).length > 0) return d1Day;
  }
  const aggregate = await readJson(env, attractionDailyKey(date, attractionHistoryShard(env, parkKey)), null);
  const day = aggregate?.parks?.find((park) => park.parkKey === parkKey);
  if (day) return day;
  const unshardedAggregate = await readJson(env, attractionDailyKey(date), null);
  const unshardedDay = unshardedAggregate?.parks?.find((park) => park.parkKey === parkKey);
  if (unshardedDay) return unshardedDay;
  if (date === isoDate(Date.now())) {
    try {
      const snapshot = await collectParkSnapshot(parkKey, Date.now(), { includeCrowd: false });
      if ((snapshot.attractions ?? []).length > 0) {
        return buildAttractionDayData(
          parkKey,
          date,
          [{
            capturedAtMillis: snapshot.capturedAtMillis,
            openedToday: snapshot.openedToday,
            openFrom: snapshot.openFrom,
            closedFrom: snapshot.closedFrom,
            attractions: snapshot.attractions,
          }],
          Date.now(),
          snapshot.openFrom,
          snapshot.closedFrom,
        );
      }
    } catch (error) {
      console.warn("Could not build live attraction day fallback", parkKey, error);
    }
  }
  return readJson(env, attractionDayKey(parkKey, date), emptyAttractionDay(parkKey, date));
}

async function readAttractionDayD1(env, parkKey, date) {
  const [dayResult, snapshotResult] = await Promise.all([
    env.APP_DATA_DB.prepare(`
      SELECT generated_at_millis, open_from, closed_from
      FROM attraction_history_days
      WHERE park_key = ? AND date = ?
      LIMIT 1
    `).bind(parkKey, date).first(),
    env.APP_DATA_DB.prepare(`
      SELECT
        captured_at_millis,
        generated_at_millis,
        opened_today,
        open_from,
        closed_from,
        attractions_json
      FROM attraction_history_snapshots
      WHERE park_key = ? AND date = ?
      ORDER BY captured_at_millis
    `).bind(parkKey, date).all(),
  ]);

  const rows = snapshotResult.results ?? [];
  if (rows.length === 0) return emptyAttractionDay(parkKey, date);

  const snapshots = rows.map((row) => ({
    capturedAtMillis: Number(row.captured_at_millis ?? 0),
    openedToday: Number(row.opened_today ?? 0) === 1,
    openFrom: row.open_from ?? null,
    closedFrom: row.closed_from ?? null,
    attractions: parseJsonArray(row.attractions_json),
  }));
  const generatedAtMillis = Math.max(
    Number(dayResult?.generated_at_millis ?? 0),
    ...rows.map((row) => Number(row.generated_at_millis ?? 0)).filter(Number.isFinite),
  );
  const openFrom = dayResult?.open_from
    ?? snapshots.find((snapshot) => snapshot.openFrom)?.openFrom
    ?? null;
  const closedFrom = dayResult?.closed_from
    ?? snapshots.find((snapshot) => snapshot.closedFrom)?.closedFrom
    ?? null;
  const cleanedSnapshots = filterOperatingWindowSnapshots(snapshots, openFrom, closedFrom);
  return buildAttractionDayData(parkKey, date, cleanedSnapshots, generatedAtMillis, openFrom, closedFrom);
}

async function readGlobalMarkers(env) {
  const date = isoDate(Date.now());
  if (hasD1(env)) {
    const d1Markers = await readGlobalMarkersD1(env, date);
    const legacyMarkers = await readGlobalMarkersKv(env, date);
    const byPark = new Map(legacyMarkers.markers.map((marker) => [marker.parkKey, marker]));
    for (const marker of d1Markers.markers) {
      const existing = byPark.get(marker.parkKey);
      if (!existing || Number(marker.capturedAtMillis ?? 0) >= Number(existing.capturedAtMillis ?? 0)) {
        byPark.set(marker.parkKey, marker);
      }
    }
    const markers = [...byPark.values()].sort((a, b) => a.parkKey.localeCompare(b.parkKey));
    return {
      generatedAtMillis: Math.max(d1Markers.generatedAtMillis, legacyMarkers.generatedAtMillis),
      date,
      markers,
    };
  }
  return readGlobalMarkersKv(env, date);
}

async function readGlobalMarkersKv(env, date) {
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

async function readGlobalMarkersD1(env, date) {
  const result = await env.APP_DATA_DB.prepare(`
    SELECT s.*
    FROM attraction_history_snapshots s
    INNER JOIN (
      SELECT park_key, max(captured_at_millis) AS captured_at_millis
      FROM attraction_history_snapshots
      WHERE date = ?
      GROUP BY park_key
    ) latest
      ON latest.park_key = s.park_key
      AND latest.captured_at_millis = s.captured_at_millis
    WHERE s.date = ?
    ORDER BY s.park_key
  `).bind(date, date).all();

  const markers = (result.results ?? []).map((row) => {
    const attractions = parseJsonArray(row.attractions_json);
    const openAttractions = attractions.filter((attraction) => Number(attraction.statusCode) === 0).length;
    return {
      parkKey: String(row.park_key ?? ""),
      capturedAtMillis: Number(row.captured_at_millis ?? 0),
      openedToday: Number(row.opened_today ?? 0) === 1,
      openFrom: row.open_from ?? null,
      closedFrom: row.closed_from ?? null,
      openAttractions,
      totalAttractions: attractions.length,
      attractionCount: attractions.length,
    };
  }).filter((marker) => marker.parkKey);

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

function parseJsonArray(value) {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function hasD1(env) {
  return Boolean(env.APP_DATA_DB);
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

function scheduledHistoryShardIndex(scheduledTimeMillis, cronShardIndex, cronShardCount, historyShardCount) {
  if (historyShardCount <= cronShardCount && cronShardIndex >= historyShardCount) {
    return null;
  }
  const fiveMinuteSlot = Math.floor(Number(scheduledTimeMillis) / (5 * 60 * 1000));
  const cycleOffset = (fiveMinuteSlot * cronShardCount) + cronShardIndex;
  return cycleOffset % historyShardCount;
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
