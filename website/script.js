const releaseUrl = './release.json';
const updateBanner = document.getElementById('updateBanner');
const downloadButton = document.getElementById('downloadButton');
const bannerButton = document.getElementById('bannerButton');
const versionNameEl = document.getElementById('versionName');
const versionCodeEl = document.getElementById('versionCode');
const releaseDateEl = document.getElementById('releaseDate');
const apkSizeEl = document.getElementById('apkSize');
const apkHashEl = document.getElementById('apkHash');
const shaValueEl = document.getElementById('shaValue');
const releaseNotesEl = document.getElementById('releaseNotes');

async function loadRelease() {
  try {
    const response = await fetch(releaseUrl, { cache: 'no-store' });
    if (!response.ok) throw new Error('Release metadata konnte nicht geladen werden.');
    const data = await response.json();

    versionNameEl.textContent = data.versionName || '–';
    versionCodeEl.textContent = data.versionCode ?? '–';
    releaseDateEl.textContent = data.releaseDate || '–';
    apkHashEl.textContent = data.sha256 || '–';
    shaValueEl.textContent = data.sha256 || '–';

    if (data.apkUrl) {
      downloadButton.href = data.apkUrl;
      bannerButton.href = data.apkUrl;
    }

    if (data.releaseNotes && Array.isArray(data.releaseNotes)) {
      releaseNotesEl.innerHTML = data.releaseNotes.map(note => `<li>${note}</li>`).join('');
    }

    if (data.showBanner) {
      updateBanner.hidden = false;
    }

    if (data.apkSize) {
      apkSizeEl.textContent = data.apkSize;
    }

    if (data.virusTotalUrl) {
      const virusTotalLink = document.getElementById('virusTotalLink');
      virusTotalLink.href = data.virusTotalUrl;
      virusTotalLink.textContent = 'VirusTotal-Scan anzeigen';
    }
  } catch (error) {
    console.warn(error);
  }
}

loadRelease();
