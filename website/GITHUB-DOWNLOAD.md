# GitHub-Download Integration für Wartezeiten App Website

Dieses Skript automatisiert das Herunterladen der neuesten APK-Release von GitHub und aktualisiert die Website automatisch.

## 🔧 Setup

### ✅ Öffentliches Repository (Einfach & Sicher)

Da dein Repository jetzt **öffentlich** ist, benötigst du **keinen GitHub Token**:

```powershell
cd website
.\download-from-github.ps1 -Owner "FynnJS" -Repo "Wartezeiten-App"
```

**Vorteile:**
- ✅ Keine Tokens nötig
- ✅ Einfach zu automatisieren (GitHub Actions, Cronjobs, etc.)
- ✅ Nutzer können Releases und Code einsehen
- ✅ Transparent und vertrauenswürdig

### Optional: Mit Token (für schnellere API-Requests)

Falls du die API-Rate-Limits erhöhen möchtest, kannst du optional einen Token hinzufügen:

```powershell
cd website
$token = "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
.\download-from-github.ps1 -Owner "FynnJS" -Repo "Wartezeiten-App" -GitHubToken $token
```

**Hinweis:** Das ist optional. Für öffentliche Repos funktioniert das Skript auch ohne Token.

## 📋 Was macht das Skript?

1. ✅ Holt die **neueste Release** von GitHub
2. ✅ Lädt die **APK-Datei** herunter
3. ✅ Berechnet den **SHA-256 Hash**
4. ✅ Aktualisiert `release.json` mit:
   - Versionsnummer
   - Download-URL
   - SHA-256 Hash
   - Dateigröße
   - Veröffentlichungsdatum
   - Release-Notizen

5. ✅ Die Website aktualisiert sich automatisch beim nächsten Laden

## 📝 Release-Metadaten

Das Skript erstellt/aktualisiert automatisch die Datei `release.json`:

```json
{
  "versionName": "1.0.5",
  "apkUrl": "./releases/wartezeiten-app-1.0.5.apk",
  "sha256": "299e995ab87c88b7f6e6721a2ab19691b76ea5a1c283ed786d84d0c35c4fa36a",
  "apkSize": "19.33",
  "releaseDate": "2026-06-01",
  "releaseNotes": [
    "Bug-Fixes und Performance-Verbesserungen",
    "Neue Filter-Optionen",
    "Offline-Caching optimiert"
  ]
}
```

## 🚀 Automatisierung (CI/CD)

### GitHub Actions (empfohlen)

Erstelle eine `.github/workflows/update-website.yml`:

```yaml
name: Update Website from Latest Release

on:
  release:
    types: [published, created]

jobs:
  update-website:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Download APK and Update Website
        run: |
          cd website
          .\download-from-github.ps1 -Owner "FynnJS" -Repo "Wartezeiten-App"
      
      - name: Commit Changes
        run: |
          git config --local user.email "ci@github.com"
          git config --local user.name "CI Bot"
          git add website/releases/* website/release.json
          git commit -m "Update website from release ${{ github.event.release.tag_name }}" || true
          git push
```

**🎉 Vorteil:** Jedes Mal wenn du einen Release machst, wird die Website automatisch aktualisiert!

## 🔒 Sicherheit

- **Öffentliche Repos:** Keine Token notwendig
- **Private Repos:** Verwende einen **Read-Only Token** mit nur `repo`-Berechtigung
- **Tokens nicht im Code:** Speichere sie in GitHub Secrets oder `.env` Dateien

## 🐛 Fehlerbehebung

| Problem | Lösung |
|---------|--------|
| "404 - Release nicht gefunden" | Stelle sicher, dass dein Repository eine Release hat |
| "Keine APK-Datei gefunden" | Die Release muss eine Datei mit `.apk` Endung enthalten |
| "401 - Authentication failed" | Prüfe deinen GitHub Token |
| "Path nicht gefunden" | Führe das Skript aus dem `website/` Verzeichnis aus |

## 📚 Weitere Optionen

```powershell
# Mit allen Optionen:
.\download-from-github.ps1 `
  -Owner "FynnJS" `
  -Repo "Wartezeiten-App" `
  -OutputDir "./releases" `
  -GitHubToken "ghp_xxxx"
```

---

**Hinweis:** Die Website wird automatisch aktualisiert, sobald `release.json` neu geschrieben wird. Der Browser cacht möglicherweise die alte Version – verwende `Ctrl+Shift+R` zum Neuladen.
