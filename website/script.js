const releaseUrl = './release.json';
const downloadButton = document.getElementById('downloadButton');
const downloadButton2 = document.getElementById('downloadButton2');
const versionNameEl = document.getElementById('versionName');
const releaseDateEl = document.getElementById('releaseDate');
const apkSizeEl = document.getElementById('apkSize');
const releaseNotesEl = document.getElementById('releaseNotes');

const defaultReleasePageUrl = 'https://github.com/FynnJS/Wartezeiten-App/releases/latest';

function setDownloadLinks(url = defaultReleasePageUrl) {
  downloadButton.href = url;
  downloadButton2.href = url;
  downloadButton.target = '_blank';
  downloadButton2.target = '_blank';
  downloadButton.rel = 'noopener';
  downloadButton2.rel = 'noopener';
}

function formatApkSize(value) {
  if (!value) return null;

  const raw = String(value).trim();
  const numericValue = Number(raw.replace(',', '.').replace(/[^0-9.]/g, ''));
  if (!Number.isFinite(numericValue)) return raw;

  if (/bytes?/i.test(raw)) {
    return (numericValue / (1024 * 1024)).toFixed(2);
  }

  return numericValue.toFixed(2);
}

function releaseNotesFor(data, language = 'de') {
  if (data.releaseNotesLocalized && Array.isArray(data.releaseNotesLocalized[language])) {
    return data.releaseNotesLocalized[language];
  }

  if (data.releaseNotesLocalized && Array.isArray(data.releaseNotesLocalized.de)) {
    return data.releaseNotesLocalized.de;
  }

  return Array.isArray(data.releaseNotes) ? data.releaseNotes : [];
}

async function loadRelease() {
  try {
    setDownloadLinks();

    const response = await fetch(releaseUrl, { cache: 'no-store' });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: Release metadata konnte nicht geladen werden.`);
    }
    const data = await response.json();

    versionNameEl.textContent = data.versionName || '-';
    releaseDateEl.textContent = data.releaseDate || '-';

    if (data.apkSize) {
      apkSizeEl.textContent = formatApkSize(data.apkSize) || data.apkSize;
    } else {
      apkSizeEl.textContent = '-';
    }

    setDownloadLinks(data.releasePageUrl || defaultReleasePageUrl);

    const notes = releaseNotesFor(data, 'de');
    if (notes.length > 0) {
      releaseNotesEl.innerHTML = '';
      notes.forEach(note => {
        const item = document.createElement('li');
        item.textContent = note;
        releaseNotesEl.appendChild(item);
      });
    }

    console.log('Release-Informationen erfolgreich geladen');
  } catch (error) {
    console.error('Fehler beim Laden der Release-Informationen:', error);
    setDownloadLinks();
  }
}

loadRelease();
