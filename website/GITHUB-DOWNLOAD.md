# GitHub Download Integration

`download-from-github.ps1` kann die neueste APK aus einem GitHub Release laden und `website/release.json` aktualisieren.

## Nutzung

```powershell
cd website
.\download-from-github.ps1 -Owner "FynnJS" -Repo "Wartezeiten-App"
```

Optional mit Token für höhere GitHub API Limits:

```powershell
$token = "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
.\download-from-github.ps1 -Owner "FynnJS" -Repo "Wartezeiten-App" -GitHubToken $token
```

## Geschriebene Metadaten

Das Skript aktualisiert:

- `versionName`
- `releasePageUrl`
- `apkUrl`
- `apkSize`
- `releaseDate`
- `releaseNotes`

Zusätzliche Sicherheits-Scan-Felder werden nicht mehr geschrieben.
