const releaseUrl = './release.json';
const downloadButton = document.getElementById('downloadButton');
const downloadButton2 = document.getElementById('downloadButton2');
const versionNameEl = document.getElementById('versionName');
const releaseDateEl = document.getElementById('releaseDate');
const apkSizeEl = document.getElementById('apkSize');
const apkHashEl = document.getElementById('apkHash');
const releaseNotesEl = document.getElementById('releaseNotes');

let apkDownloadUrl = null;

async function loadRelease() {
  try {
    const response = await fetch(releaseUrl, { cache: 'no-store' });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: Release metadata konnte nicht geladen werden.`);
    }
    const data = await response.json();

    // Version und Datum
    versionNameEl.textContent = data.versionName || '–';
    releaseDateEl.textContent = data.releaseDate || '–';
    
    // APK-Größe
    if (data.apkSize) {
      apkSizeEl.textContent = data.apkSize;
    }

    // SHA-256 Hash mit Fallback
    const sha256 = data.sha256 || data.apkHash;
    if (sha256) {
      apkHashEl.textContent = sha256;
    } else {
      apkHashEl.textContent = 'Noch nicht verfügbar';
      apkHashEl.style.color = '#999';
    }

    // Download-URL speichern und Handler setzen
    if (data.apkUrl) {
      apkDownloadUrl = data.apkUrl;
      downloadButton.onclick = startDownload;
      downloadButton2.onclick = startDownload;
      // Nicht .href setzen, damit Download-Handler aktiv wird
      downloadButton.href = '#';
      downloadButton2.href = '#';
    }

    // Release Notes
    if (data.releaseNotes && Array.isArray(data.releaseNotes)) {
      releaseNotesEl.innerHTML = data.releaseNotes.map(note => `<li>${note}</li>`).join('');
    }

    console.log('✓ Release-Informationen erfolgreich geladen');
  } catch (error) {
    console.error('✗ Fehler beim Laden der Release-Informationen:', error);
    apkHashEl.textContent = 'Fehler beim Laden';
    apkHashEl.style.color = '#d32f2f';
  }
}

function startDownload(e) {
  e.preventDefault();
  if (!apkDownloadUrl) {
    alert('Download-Link nicht verfügbar');
    return;
  }
  // Öffne Download in neuem Tab/Fenster und lade herunter
  const link = document.createElement('a');
  link.href = apkDownloadUrl;
  link.download = 'wartezeiten-app-1.0.0.apk';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}

function copyToClipboard() {
  const text = apkHashEl.textContent;
  if (!text || text.includes('–') || text.includes('Fehler')) {
    alert('SHA-256 nicht verfügbar');
    return;
  }
  
  navigator.clipboard.writeText(text).then(() => {
    alert('✓ SHA-256 Hash kopiert!');
  }).catch(err => {
    console.error('Fehler beim Kopieren:', err);
    alert('Fehler beim Kopieren der Zwischenablage');
  });
}

// Laden beim Start
loadRelease();
