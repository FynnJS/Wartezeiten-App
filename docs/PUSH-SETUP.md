# Push-Benachrichtigungen einrichten

Standby-Push benötigt eine Firebase-Android-App für `de.wartezeiten.app` und einen Firebase-Service-Account. Die JSON-Dateien dürfen nicht committed werden; passende Dateinamen sind in `.gitignore` ausgeschlossen.

## Voraussetzungen

- Firebase-Projekt mit aktivierter Cloud Messaging API
- Android-App `de.wartezeiten.app` im Firebase-Projekt
- heruntergeladene `google-services.json`
- Service-Account-JSON mit Berechtigung zum Senden von FCM-Nachrichten
- authentifizierte GitHub CLI (`gh auth login`)
- authentifizierter Wrangler (`npx wrangler login`)

## Automatisches Setup

Im Repository ausführen:

```powershell
.\scripts\configure-push.ps1 `
  -GoogleServicesJson C:\Pfad\google-services.json `
  -ServiceAccountJson C:\Pfad\firebase-service-account.json
```

Das Skript:

1. prüft Paketname und Projekt-IDs der Firebase-Dateien,
2. kopiert `google-services.json` lokal nach `app/`, damit Debug- und lokale Release-Builds Firebase automatisch einbetten,
3. setzt die vier Firebase-Buildwerte als GitHub-Repository-Variablen,
4. speichert Projekt-ID, Client-E-Mail und Private Key als Cloudflare-Worker-Secrets,
5. wendet ausstehende D1-Migrationen remote an,
6. deployed den Worker,
7. prüft `https://wartezeiten-app.tutorialfynn.workers.dev/push/status`.

Erwartete Antwort nach erfolgreichem Setup:

```json
{
  "ok": true,
  "pushReady": true,
  "d1Configured": true,
  "fcmConfigured": true
}
```

## Release-Prüfung

Die Release-Pipeline bricht ab, wenn einer der vier Android-Firebase-Werte fehlt. Vor dem GitHub-Release außerdem kontrollieren:

```powershell
Invoke-RestMethod https://wartezeiten-app.tutorialfynn.workers.dev/push/status
```

In der App zeigt die Watchlist den Zustand von Standby-Push. Die Testbenachrichtigung prüft Android-Kanal und Berechtigung unabhängig von FCM. Ein echter Watchlist-Alarm prüft zusätzlich Worker, Cron, Firebase und Gerätezustellung.
