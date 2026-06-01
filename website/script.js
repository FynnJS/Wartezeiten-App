const releaseUrl = './release.json';
const downloadButton = document.getElementById('downloadButton');
const downloadButton2 = document.getElementById('downloadButton2');
const versionNameEl = document.getElementById('versionName');
const releaseDateEl = document.getElementById('releaseDate');
const apkSizeEl = document.getElementById('apkSize');
const apkHashEl = document.getElementById('apkHash');
const releaseNotesEl = document.getElementById('releaseNotes');

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

    // Download-Links
    if (data.apkUrl) {
      downloadButton.href = data.apkUrl;
      downloadButton2.href = data.apkUrl;
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
