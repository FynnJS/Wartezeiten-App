const overviewView = document.getElementById('view-overview');
const detailView = document.getElementById('view-detail');
const parkListEl = document.getElementById('parkList');
const parksStatusEl = document.getElementById('parksStatus');
const parkSearchEl = document.getElementById('parkSearch');
const recentParksEl = document.getElementById('recentParks');

const backButton = document.getElementById('backButton');
const refreshButton = document.getElementById('refreshButton');
const detailParkName = document.getElementById('detailParkName');
const detailParkLand = document.getElementById('detailParkLand');
const detailUpdated = document.getElementById('detailUpdated');
const dataGapBanner = document.getElementById('dataGapBanner');
const liveErrorEl = document.getElementById('liveError');

const statusOpenEl = document.getElementById('statusOpen');
const statusHoursEl = document.getElementById('statusHours');
const crowdBarFillEl = document.getElementById('crowdBarFill');
const crowdTextEl = document.getElementById('crowdText');
const statusAttractionsEl = document.getElementById('statusAttractions');

const attractionSearchEl = document.getElementById('attractionSearch');
const statusFilterEl = document.getElementById('statusFilter');
const sortOrderEl = document.getElementById('sortOrder');
const attractionsStatusEl = document.getElementById('attractionsStatus');
const attractionListEl = document.getElementById('attractionList');

const statsSectionEl = document.getElementById('statsSection');
const dateSelectEl = document.getElementById('dateSelect');
const attractionSelectEl = document.getElementById('attractionSelect');
const statsStatusEl = document.getElementById('statsStatus');
const statsSummaryEl = document.getElementById('statsSummary');
const statsChartEl = document.getElementById('statsChart');
const statsChartWrapEl = document.querySelector('.stats-chart-wrap');
const chartTooltipEl = document.getElementById('chartTooltip');

const SVG_NS = 'http://www.w3.org/2000/svg';
const LIVE_REFRESH_MILLIS = 60000;
const RECENT_PARKS_KEY = 'wartezeiten-recent-parks';
const MAX_RECENT_PARKS = 8;
const DEFAULT_TITLE = document.title;

// Ported from countryToIsoCode() in ParkListScreen.kt to keep flags identical to the Android app.
const COUNTRY_TO_ISO = {
  deutschland: 'DE', germany: 'DE', de: 'DE',
  österreich: 'AT', austria: 'AT', at: 'AT',
  schweiz: 'CH', switzerland: 'CH', ch: 'CH',
  frankreich: 'FR', france: 'FR', fr: 'FR',
  niederlande: 'NL', netherlands: 'NL', nl: 'NL',
  belgien: 'BE', belgium: 'BE', be: 'BE',
  'vereinigtes königreich': 'GB', 'united kingdom': 'GB', uk: 'GB', gb: 'GB', 'great britain': 'GB', großbritannien: 'GB',
  usa: 'US', us: 'US', 'u.s.a.': 'US', 'united states': 'US', 'united states of america': 'US',
  'vereinigte staaten': 'US', 'vereinigte staaten von amerika': 'US',
  spanien: 'ES', spain: 'ES', es: 'ES',
  italien: 'IT', italy: 'IT', it: 'IT',
  dänemark: 'DK', denmark: 'DK', dk: 'DK',
  schweden: 'SE', sweden: 'SE', se: 'SE',
  norwegen: 'NO', norway: 'NO', no: 'NO',
  finnland: 'FI', finland: 'FI', fi: 'FI',
  japan: 'JP', jp: 'JP',
  tschechien: 'CZ', 'czech republic': 'CZ', cz: 'CZ',
  polen: 'PL', poland: 'PL', pl: 'PL',
  portugal: 'PT', pt: 'PT',
  luxemburg: 'LU', luxembourg: 'LU', lu: 'LU',
};

let allParks = [];
let currentParkKey = null;
let liveData = null;
let liveRefreshTimer = null;
let currentDayData = null;
let pendingAttractionSelection = null;
let chartState = null;

async function fetchJson(url) {
  const response = await fetch(url, { cache: 'no-store' });
  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const body = await response.json();
      if (body && body.error) message = body.error;
    } catch {
      // response body was not JSON, keep the HTTP status message
    }
    throw new Error(message);
  }
  return response.json();
}

function countryToFlag(country) {
  const iso = COUNTRY_TO_ISO[String(country || '').toLowerCase().trim()];
  if (!iso) return '';
  return [...iso].map((letter) => String.fromCodePoint(0x1f1e6 + letter.charCodeAt(0) - 65)).join('');
}

function renderLoadingStatus(el, text) {
  el.replaceChildren();
  const spinner = document.createElement('span');
  spinner.className = 'spinner';
  el.append(spinner, document.createTextNode(text));
}

function offsetMinutesFromIso(value) {
  const match = String(value || '').match(/([+-])(\d{2}):(\d{2})$/);
  if (!match) return null;
  const minutes = Number(match[2]) * 60 + Number(match[3]);
  return match[1] === '-' ? -minutes : minutes;
}

function formatParkTime(millis, offsetSource) {
  const offset = offsetMinutesFromIso(offsetSource) ?? 0;
  const local = new Date(millis + offset * 60000);
  const hh = String(local.getUTCHours()).padStart(2, '0');
  const mm = String(local.getUTCMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}

// Mirrors cacheAgeLabel() in WaitingTimesScreen.kt for identical wording.
function cacheAgeLabel(millis) {
  if (!Number.isFinite(millis) || millis <= 0) return 'unbekannt';
  const minutes = Math.round((Date.now() - millis) / 60000);
  if (minutes <= 1) return 'gerade eben';
  if (minutes < 60) return `vor ${minutes} Minuten`;
  if (minutes < 120) return 'vor 1 Stunde';
  return `vor ${Math.round(minutes / 60)} Stunden`;
}

function estimateCrowdLevel(attractions) {
  const waits = (attractions ?? [])
    .filter((item) => Number(item.statusCode) === 0)
    .map((item) => Math.min(Number(item.value), 120))
    .filter((value) => Number.isFinite(value) && value >= 0)
    .sort((a, b) => a - b);
  if (waits.length < 3) return null;
  const average = waits.reduce((sum, value) => sum + value, 0) / waits.length;
  const p75 = waits[Math.floor((waits.length - 1) * 0.75)];
  const estimated = ((average / 60) * 0.7 + (p75 / 90) * 0.3) * 100;
  return Math.max(0, Math.min(100, estimated));
}

function isLikelyMissingWaitingTimeData(live, now) {
  if (!live || !live.openedToday) return false;
  if (!Array.isArray(live.attractions) || live.attractions.length === 0) return false;
  const openAtMillis = Date.parse(live.openFrom);
  if (!Number.isFinite(openAtMillis) || now - openAtMillis < 15 * 60000) return false;
  const closeAtMillis = Date.parse(live.closedFrom);
  if (Number.isFinite(closeAtMillis) && now > closeAtMillis) return false;
  return !live.attractions.some((item) => item.statusCode === 0);
}

// --- Recently viewed parks ---

function getRecentParkKeys() {
  try {
    const raw = JSON.parse(localStorage.getItem(RECENT_PARKS_KEY) || '[]');
    return Array.isArray(raw) ? raw.filter((key) => typeof key === 'string') : [];
  } catch {
    return [];
  }
}

function addRecentPark(parkKey) {
  const next = [parkKey, ...getRecentParkKeys().filter((key) => key !== parkKey)].slice(0, MAX_RECENT_PARKS);
  localStorage.setItem(RECENT_PARKS_KEY, JSON.stringify(next));
}

function renderRecentParks() {
  const keys = getRecentParkKeys().filter((key) => allParks.some((park) => park.parkKey === key));
  const label = recentParksEl.querySelector('.chip-row-label');
  recentParksEl.replaceChildren(label);
  recentParksEl.hidden = keys.length === 0;

  for (const key of keys) {
    const info = parkLabelFromKey(key);
    const flag = countryToFlag(info.land);
    const chip = document.createElement('button');
    chip.type = 'button';
    chip.className = 'chip';
    chip.textContent = flag ? `${flag} ${info.name}` : info.name;
    chip.addEventListener('click', () => selectPark(key, info.name, info.land));
    recentParksEl.appendChild(chip);
  }
}

// --- Park overview ---

async function loadParks() {
  renderLoadingStatus(parksStatusEl, 'Parks werden geladen…');
  try {
    const data = await fetchJson('/api/parks');
    allParks = Array.isArray(data.parks) ? data.parks : [];
    parksStatusEl.textContent = allParks.length > 0
      ? `${allParks.length} Parks verfügbar.`
      : 'Aktuell sind keine Parks verfügbar.';
    renderParkList(parkSearchEl.value);
    renderRecentParks();
  } catch (error) {
    parksStatusEl.textContent = `Parks konnten nicht geladen werden: ${error.message}`;
  }
}

function renderParkList(filterText) {
  const query = String(filterText || '').trim().toLowerCase();
  const filtered = query
    ? allParks.filter((park) =>
      park.name.toLowerCase().includes(query) || String(park.land || '').toLowerCase().includes(query))
    : allParks;

  parkListEl.replaceChildren();
  for (const park of filtered) {
    const card = document.createElement('button');
    card.type = 'button';
    card.className = 'park-card';
    card.setAttribute('role', 'listitem');

    const name = document.createElement('div');
    name.className = 'park-card-name';
    name.textContent = park.name;

    const flag = countryToFlag(park.land);
    const land = document.createElement('div');
    land.className = 'park-card-land';
    land.textContent = flag ? `${flag} ${park.land}` : (park.land || '');

    card.append(name, land);
    card.addEventListener('click', () => selectPark(park.parkKey, park.name, park.land));
    parkListEl.appendChild(card);
  }

  if (filtered.length === 0 && allParks.length > 0) {
    const empty = document.createElement('p');
    empty.className = 'app-status-text';
    empty.textContent = 'Keine Parks gefunden.';
    parkListEl.appendChild(empty);
  }
}

function parkLabelFromKey(parkKey) {
  const known = allParks.find((park) => park.parkKey === parkKey);
  return known ? { name: known.name, land: known.land } : { name: parkKey, land: '' };
}

// --- Navigation ---

function selectPark(parkKey, name, land) {
  currentParkKey = parkKey;
  location.hash = `#park=${encodeURIComponent(parkKey)}`;
  showDetailView(name, land);
  addRecentPark(parkKey);
  startLivePolling();
  loadStatistics(parkKey);
}

function showDetailView(name, land) {
  overviewView.hidden = true;
  detailView.hidden = false;
  detailParkName.textContent = name || currentParkKey;
  detailParkLand.textContent = countryToFlag(land) ? `${countryToFlag(land)} ${land}` : (land || '');
  document.title = `${name || currentParkKey} – Live-Wartezeiten`;
  liveData = null;
  pendingAttractionSelection = null;
  liveErrorEl.hidden = true;
  dataGapBanner.hidden = true;
  attractionListEl.replaceChildren();
  renderLoadingStatus(attractionsStatusEl, 'Attraktionen werden geladen…');
  window.scrollTo(0, 0);
}

function showOverviewView() {
  stopLivePolling();
  currentParkKey = null;
  liveData = null;
  detailView.hidden = true;
  overviewView.hidden = false;
  document.title = DEFAULT_TITLE;
  renderRecentParks();
  if (location.hash) {
    history.pushState(null, '', location.pathname + location.search);
  }
}

function applyHashRoute() {
  const match = location.hash.match(/^#park=(.+)$/);
  if (!match) {
    showOverviewView();
    return;
  }
  const parkKey = decodeURIComponent(match[1]);
  if (parkKey === currentParkKey) return;
  const label = parkLabelFromKey(parkKey);
  currentParkKey = parkKey;
  showDetailView(label.name, label.land);
  addRecentPark(parkKey);
  startLivePolling();
  loadStatistics(parkKey);
}

// --- Live waiting times ---

function startLivePolling() {
  stopLivePolling();
  loadLiveData(currentParkKey);
  liveRefreshTimer = setInterval(() => loadLiveData(currentParkKey), LIVE_REFRESH_MILLIS);
}

function stopLivePolling() {
  if (liveRefreshTimer) {
    clearInterval(liveRefreshTimer);
    liveRefreshTimer = null;
  }
}

async function loadLiveData(parkKey) {
  try {
    const data = await fetchJson(`/api/parks/${encodeURIComponent(parkKey)}/live`);
    if (parkKey !== currentParkKey) return;
    liveData = data;
    liveErrorEl.hidden = true;
    renderLiveStatus();
    renderAttractionList();
  } catch (error) {
    if (parkKey !== currentParkKey) return;
    liveErrorEl.hidden = false;
    liveErrorEl.textContent = `Live-Daten konnten nicht geladen werden: ${error.message}`;
  }
}

function renderLiveStatus() {
  detailUpdated.textContent = `Datenalter: ${cacheAgeLabel(liveData.capturedAtMillis)}`;

  const now = Date.now();
  const openAtMillis = Date.parse(liveData.openFrom);
  const closeAtMillis = Date.parse(liveData.closedFrom);
  statusOpenEl.classList.remove('is-open', 'is-closed', 'is-later');
  if (!liveData.openedToday) {
    statusOpenEl.textContent = 'Heute geschlossen';
    statusOpenEl.classList.add('is-closed');
  } else if (Number.isFinite(openAtMillis) && now < openAtMillis) {
    statusOpenEl.textContent = 'Öffnet später';
    statusOpenEl.classList.add('is-later');
  } else if (Number.isFinite(closeAtMillis) && now > closeAtMillis) {
    statusOpenEl.textContent = 'Für heute geschlossen';
    statusOpenEl.classList.add('is-closed');
  } else {
    statusOpenEl.textContent = 'Geöffnet';
    statusOpenEl.classList.add('is-open');
  }

  if (liveData.openFrom && liveData.closedFrom) {
    const offsetSource = liveData.openFrom || liveData.closedFrom;
    statusHoursEl.textContent =
      `${formatParkTime(Date.parse(liveData.openFrom), offsetSource)} - ${formatParkTime(Date.parse(liveData.closedFrom), offsetSource)} Uhr (Parkzeit)`;
  } else {
    statusHoursEl.textContent = '';
  }

  if (liveData.crowdLevel == null) {
    crowdBarFillEl.style.width = '0%';
    crowdTextEl.textContent = liveData.openedToday ? 'Auslastung aktuell unbekannt' : 'Heute geschlossen';
  } else {
    const rounded = Math.round(liveData.crowdLevel);
    crowdBarFillEl.style.width = `${Math.max(0, Math.min(100, rounded))}%`;
    crowdTextEl.textContent = `ca. ${rounded}%`;
  }

  statusAttractionsEl.textContent = `${liveData.openAttractions} von ${liveData.totalAttractions} offen`;

  dataGapBanner.hidden = !isLikelyMissingWaitingTimeData(liveData, now);
}

// Wording matches AttractionStatus.label() in WaitingTimesScreen.kt.
const STATUS_LABELS = {
  0: { label: 'Geöffnet', cls: 'status-open' },
  '-1': { label: 'Geschlossen', cls: 'status-closed' },
  '-2': { label: 'Wetter', cls: 'status-closed' },
  '-3': { label: 'Wartung', cls: 'status-maintenance' },
};

function statusInfoFor(statusCode) {
  return STATUS_LABELS[String(statusCode)] ?? { label: 'Unbekannt', cls: 'status-unknown' };
}

function waitColorClass(waitMinutes) {
  if (waitMinutes == null) return null;
  if (waitMinutes < 30) return 'wait-low';
  if (waitMinutes < 60) return 'wait-mid';
  return 'wait-high';
}

// Jumps from a live attraction row to its historical statistics, mirroring how tapping
// an attraction in WaitingTimesScreen opens its detail/history card in the app.
function selectStatsAttraction(attractionId) {
  const hasOption = [...attractionSelectEl.options].some((option) => option.value === attractionId);
  if (hasOption) {
    attractionSelectEl.value = attractionId;
    renderStatisticsSelection();
  } else {
    pendingAttractionSelection = attractionId;
  }
  statsSectionEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function syncSelectedAttractionRow() {
  const selectedId = attractionSelectEl.value;
  for (const row of attractionListEl.querySelectorAll('.attraction-row')) {
    row.classList.toggle('is-selected', Boolean(selectedId) && selectedId !== '__all__' && row.dataset.attractionId === selectedId);
  }
}

function renderAttractionList() {
  const attractions = liveData?.attractions ?? [];
  const query = attractionSearchEl.value.trim().toLowerCase();
  const statusFilter = statusFilterEl.value;
  const sortOrder = sortOrderEl.value;

  let filtered = attractions.filter((item) => item.name.toLowerCase().includes(query));
  if (statusFilter === 'open') {
    filtered = filtered.filter((item) => item.statusCode === 0);
  } else if (statusFilter === 'closed') {
    filtered = filtered.filter((item) => item.statusCode !== 0);
  }

  filtered = [...filtered];
  if (sortOrder === 'name') {
    filtered.sort((a, b) => a.name.localeCompare(b.name));
  } else if (sortOrder === 'wait-asc') {
    filtered.sort((a, b) => (a.waitMinutes ?? 9999) - (b.waitMinutes ?? 9999));
  } else {
    filtered.sort((a, b) => (b.waitMinutes ?? -1) - (a.waitMinutes ?? -1));
  }

  attractionListEl.replaceChildren();
  for (const item of filtered) {
    const info = statusInfoFor(item.statusCode);

    const row = document.createElement('button');
    row.type = 'button';
    row.className = 'attraction-row';
    row.dataset.attractionId = item.id;
    row.title = 'Statistik dieser Attraktion anzeigen';
    row.addEventListener('click', () => selectStatsAttraction(item.id));

    const main = document.createElement('div');
    main.className = 'attraction-main';

    const dot = document.createElement('span');
    dot.className = `status-dot ${info.cls}`;

    const text = document.createElement('div');
    text.className = 'attraction-text';

    const name = document.createElement('span');
    name.className = 'attraction-name';
    name.textContent = item.name;

    const statusLabel = document.createElement('span');
    statusLabel.className = 'attraction-status-label';
    statusLabel.textContent = info.label;

    text.append(name, statusLabel);
    main.append(dot, text);

    const waitBlock = document.createElement('div');
    waitBlock.className = 'attraction-wait-block';

    const waitClass = waitColorClass(item.waitMinutes);
    const waitValue = document.createElement('span');
    waitValue.className = waitClass ? `attraction-wait ${waitClass}` : 'attraction-wait';
    waitValue.textContent = item.waitMinutes != null ? String(item.waitMinutes) : '-';

    const waitUnit = document.createElement('span');
    waitUnit.className = 'attraction-wait-unit';
    waitUnit.textContent = item.waitMinutes != null ? 'Min.' : '';

    waitBlock.append(waitValue, waitUnit);
    row.append(main, waitBlock);
    attractionListEl.appendChild(row);
  }

  attractionsStatusEl.textContent = attractions.length === 0
    ? 'Noch keine aktuellen Attraktionsdaten verfügbar.'
    : `${filtered.length} von ${attractions.length} Attraktionen angezeigt.`;

  syncSelectedAttractionRow();
}

// --- Statistics ---

async function loadStatistics(parkKey) {
  renderLoadingStatus(statsStatusEl, 'Statistik wird geladen…');
  statsSummaryEl.replaceChildren();
  statsChartEl.hidden = true;
  dateSelectEl.replaceChildren();
  attractionSelectEl.replaceChildren();
  currentDayData = null;

  try {
    const data = await fetchJson(`/app-data/statistics/parks/${encodeURIComponent(parkKey)}/dates.json`);
    if (parkKey !== currentParkKey) return;
    const dates = Array.isArray(data.dates) ? data.dates : [];
    if (dates.length === 0) {
      statsStatusEl.textContent = 'Für diesen Park liegen noch keine zentralen Statistikdaten vor. Daten werden laufend gesammelt.';
      return;
    }

    const today = new Date().toISOString().slice(0, 10);
    const selectedDate = dates.includes(today) ? today : dates[dates.length - 1];

    for (const date of [...dates].reverse()) {
      const option = document.createElement('option');
      option.value = date;
      option.textContent = date;
      if (date === selectedDate) option.selected = true;
      dateSelectEl.appendChild(option);
    }

    await loadStatisticsDay(parkKey, selectedDate);
  } catch (error) {
    if (parkKey !== currentParkKey) return;
    statsStatusEl.textContent = `Statistik konnte nicht geladen werden: ${error.message}`;
  }
}

async function loadStatisticsDay(parkKey, date) {
  const previousAttractionSelection = attractionSelectEl.value || '__all__';
  renderLoadingStatus(statsStatusEl, 'Statistik wird geladen…');
  try {
    const dayData = await fetchJson(`/app-data/statistics/parks/${encodeURIComponent(parkKey)}/days/${date}.json`);
    if (parkKey !== currentParkKey) return;
    currentDayData = dayData;

    if (!Array.isArray(dayData.snapshots) || dayData.snapshots.length === 0) {
      statsStatusEl.textContent = `Für ${date} liegen noch keine Messpunkte vor (Park evtl. noch nicht geöffnet oder bereits geschlossen).`;
      statsSummaryEl.replaceChildren();
      statsChartEl.hidden = true;
      attractionSelectEl.replaceChildren();
      return;
    }

    attractionSelectEl.replaceChildren();
    const allOption = document.createElement('option');
    allOption.value = '__all__';
    allOption.textContent = 'Auslastung (gesamt)';
    attractionSelectEl.appendChild(allOption);
    for (const attraction of dayData.attractions ?? []) {
      const option = document.createElement('option');
      option.value = attraction.id;
      option.textContent = attraction.name;
      attractionSelectEl.appendChild(option);
    }

    const hasPendingSelection = pendingAttractionSelection
      && [...attractionSelectEl.options].some((option) => option.value === pendingAttractionSelection);
    if (hasPendingSelection) {
      attractionSelectEl.value = pendingAttractionSelection;
      pendingAttractionSelection = null;
    } else {
      const hasPreviousSelection = [...attractionSelectEl.options].some((option) => option.value === previousAttractionSelection);
      attractionSelectEl.value = hasPreviousSelection ? previousAttractionSelection : '__all__';
    }

    statsStatusEl.textContent = '';
    renderStatisticsSelection();
  } catch (error) {
    if (parkKey !== currentParkKey) return;
    statsStatusEl.textContent = `Statistik konnte nicht geladen werden: ${error.message}`;
  }
}

function renderStatisticsSelection() {
  syncSelectedAttractionRow();
  if (!currentDayData) return;
  const selection = attractionSelectEl.value || '__all__';
  const offsetSource = currentDayData.openFrom || currentDayData.closedFrom;

  if (selection === '__all__') {
    const points = currentDayData.snapshots
      .map((snapshot) => ({ t: snapshot.capturedAtMillis, v: estimateCrowdLevel(snapshot.attractions) }))
      .filter((point) => point.v != null);

    const lastSnapshot = currentDayData.snapshots[currentDayData.snapshots.length - 1];
    const lastOpenCount = (lastSnapshot.attractions ?? []).filter((item) => Number(item.statusCode) === 0).length;
    const averageCrowd = points.length > 0
      ? Math.round(points.reduce((sum, point) => sum + point.v, 0) / points.length)
      : null;

    renderSummaryCards([
      { label: 'Messpunkte', value: String(currentDayData.snapshots.length) },
      { label: 'Offene Attraktionen (zuletzt)', value: `${lastOpenCount} / ${(lastSnapshot.attractions ?? []).length}` },
      { label: 'Ø Auslastung', value: averageCrowd != null ? `${averageCrowd}%` : '-' },
    ]);
    renderChart(points, { valueSuffix: '%', maxValue: 100, offsetSource });
    return;
  }

  const attraction = (currentDayData.attractions ?? []).find((item) => item.id === selection);
  const points = currentDayData.snapshots
    .map((snapshot) => {
      const item = (snapshot.attractions ?? []).find((candidate) => candidate.id === selection);
      return item && Number(item.statusCode) === 0 ? { t: snapshot.capturedAtMillis, v: Number(item.value) } : null;
    })
    .filter(Boolean);

  renderSummaryCards([
    { label: 'Ø Wartezeit', value: attraction?.averageWaitMinutes != null ? `${attraction.averageWaitMinutes} Min.` : '-' },
    { label: 'Minimum', value: attraction?.minWaitMinutes != null ? `${attraction.minWaitMinutes} Min.` : '-' },
    { label: 'Maximum', value: attraction?.maxWaitMinutes != null ? `${attraction.maxWaitMinutes} Min.` : '-' },
    { label: 'Messpunkte (offen)', value: attraction ? String(attraction.openSampleCount) : '-' },
  ]);
  const maxValue = Math.max(5, (attraction?.maxWaitMinutes ?? 0) * 1.2);
  renderChart(points, { valueSuffix: ' Min.', maxValue, offsetSource });
}

function renderSummaryCards(cards) {
  statsSummaryEl.replaceChildren();
  for (const card of cards) {
    const el = document.createElement('div');
    el.className = 'stats-summary-card';
    const value = document.createElement('span');
    value.className = 'value';
    value.textContent = card.value;
    const label = document.createElement('span');
    label.className = 'label';
    label.textContent = card.label;
    el.append(value, label);
    statsSummaryEl.appendChild(el);
  }
}

function svgEl(tag, attrs) {
  const el = document.createElementNS(SVG_NS, tag);
  for (const [key, value] of Object.entries(attrs)) {
    el.setAttribute(key, value);
  }
  return el;
}

function renderChart(points, { valueSuffix, maxValue, offsetSource }) {
  statsChartEl.replaceChildren();
  chartState = null;
  chartTooltipEl.hidden = true;
  if (points.length < 2) {
    statsChartEl.hidden = true;
    statsStatusEl.textContent = 'Nicht genügend Messpunkte für ein Diagramm.';
    return;
  }
  statsChartEl.hidden = false;

  const width = 600;
  const height = 220;
  const paddingLeft = 36;
  const paddingRight = 12;
  const paddingTop = 16;
  const paddingBottom = 28;
  const plotWidth = width - paddingLeft - paddingRight;
  const plotHeight = height - paddingTop - paddingBottom;

  const minT = points[0].t;
  const maxT = points[points.length - 1].t;
  const spanT = Math.max(1, maxT - minT);
  const topValue = Math.max(maxValue, Math.max(...points.map((point) => point.v)) * 1.05);

  const toX = (t) => paddingLeft + ((t - minT) / spanT) * plotWidth;
  const toY = (v) => paddingTop + plotHeight - (Math.max(0, v) / topValue) * plotHeight;

  const linePoints = points.map((point) => `${toX(point.t).toFixed(1)},${toY(point.v).toFixed(1)}`).join(' ');
  const areaPoints = `${paddingLeft},${paddingTop + plotHeight} ${linePoints} ${toX(maxT).toFixed(1)},${paddingTop + plotHeight}`;

  statsChartEl.appendChild(svgEl('polygon', { points: areaPoints, class: 'chart-area' }));
  statsChartEl.appendChild(svgEl('polyline', { points: linePoints, class: 'chart-line' }));
  statsChartEl.appendChild(svgEl('line', {
    x1: paddingLeft, y1: paddingTop + plotHeight, x2: width - paddingRight, y2: paddingTop + plotHeight, class: 'chart-axis',
  }));

  const maxLabel = svgEl('text', { x: 4, y: paddingTop + 8, 'text-anchor': 'start' });
  maxLabel.textContent = `${Math.round(topValue)}${valueSuffix}`;
  statsChartEl.appendChild(maxLabel);

  const zeroLabel = svgEl('text', { x: 4, y: paddingTop + plotHeight, 'text-anchor': 'start' });
  zeroLabel.textContent = `0${valueSuffix}`;
  statsChartEl.appendChild(zeroLabel);

  const startLabel = svgEl('text', { x: paddingLeft, y: height - 8, 'text-anchor': 'start' });
  startLabel.textContent = formatParkTime(minT, offsetSource);
  statsChartEl.appendChild(startLabel);

  const endLabel = svgEl('text', { x: width - paddingRight, y: height - 8, 'text-anchor': 'end' });
  endLabel.textContent = formatParkTime(maxT, offsetSource);
  statsChartEl.appendChild(endLabel);

  const guideLine = svgEl('line', {
    x1: 0, y1: paddingTop, x2: 0, y2: paddingTop + plotHeight, class: 'chart-guide', visibility: 'hidden',
  });
  const guideDot = svgEl('circle', { r: 4, cx: 0, cy: 0, class: 'chart-guide-dot', visibility: 'hidden' });
  statsChartEl.append(guideLine, guideDot);

  chartState = { width, height, points, toX, toY, valueSuffix, offsetSource, guideLine, guideDot };
}

function handleChartHover(event) {
  if (!chartState) return;
  const rect = statsChartEl.getBoundingClientRect();
  const viewBoxX = ((event.clientX - rect.left) / rect.width) * chartState.width;

  let nearest = chartState.points[0];
  let nearestDistance = Infinity;
  for (const point of chartState.points) {
    const distance = Math.abs(chartState.toX(point.t) - viewBoxX);
    if (distance < nearestDistance) {
      nearestDistance = distance;
      nearest = point;
    }
  }

  const x = chartState.toX(nearest.t);
  const y = chartState.toY(nearest.v);
  chartState.guideLine.setAttribute('x1', x);
  chartState.guideLine.setAttribute('x2', x);
  chartState.guideLine.setAttribute('visibility', 'visible');
  chartState.guideDot.setAttribute('cx', x);
  chartState.guideDot.setAttribute('cy', y);
  chartState.guideDot.setAttribute('visibility', 'visible');

  const wrapRect = statsChartWrapEl.getBoundingClientRect();
  chartTooltipEl.textContent = `${formatParkTime(nearest.t, chartState.offsetSource)} · ${Math.round(nearest.v)}${chartState.valueSuffix}`;
  chartTooltipEl.style.left = `${(rect.left - wrapRect.left) + (x / chartState.width) * rect.width}px`;
  chartTooltipEl.style.top = `${(rect.top - wrapRect.top) + (y / chartState.height) * rect.height}px`;
  chartTooltipEl.hidden = false;
}

function hideChartHover() {
  if (chartState) {
    chartState.guideLine.setAttribute('visibility', 'hidden');
    chartState.guideDot.setAttribute('visibility', 'hidden');
  }
  chartTooltipEl.hidden = true;
}

// --- Wiring ---

parkSearchEl.addEventListener('input', () => renderParkList(parkSearchEl.value));
backButton.addEventListener('click', () => {
  location.hash = '';
});
refreshButton.addEventListener('click', () => {
  if (currentParkKey) loadLiveData(currentParkKey);
});
attractionSearchEl.addEventListener('input', renderAttractionList);
statusFilterEl.addEventListener('change', renderAttractionList);
sortOrderEl.addEventListener('change', renderAttractionList);
dateSelectEl.addEventListener('change', () => {
  if (currentParkKey) loadStatisticsDay(currentParkKey, dateSelectEl.value);
});
attractionSelectEl.addEventListener('change', renderStatisticsSelection);
statsChartEl.addEventListener('mousemove', handleChartHover);
statsChartEl.addEventListener('mouseleave', hideChartHover);
window.addEventListener('hashchange', applyHashRoute);
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && !detailView.hidden) {
    location.hash = '';
  }
});

loadParks().then(applyHashRoute);
