# GitHub-Download-Skript für APK-Releases
# Funktioniert mit öffentlichen und privaten Repositories
# Verwendung: .\download-from-github.ps1 -Owner "FynnJS" -Repo "Wartezeiten-App" -OutputDir "./releases"

param(
    [Parameter(Mandatory = $true)]
    [string]$Owner,
    
    [Parameter(Mandatory = $true)]
    [string]$Repo,
    
    [Parameter(Mandatory = $false)]
    [string]$OutputDir = "./releases",
    
    [Parameter(Mandatory = $false)]
    [string]$GitHubToken = ""
)

function Write-Status {
    param([string]$Message, [string]$Type = "Info")
    $colors = @{"Success" = "Green"; "Error" = "Red"; "Info" = "Cyan"; "Warning" = "Yellow"}
    $color = if ($colors.ContainsKey($Type)) { $colors[$Type] } else { $colors.Info }
    Write-Host "[$Type] $Message" -ForegroundColor $color
}

try {
    # Stelle sicher, dass das Ausgabeverzeichnis existiert
    if (-not (Test-Path $OutputDir)) {
        New-Item -ItemType Directory -Path $OutputDir | Out-Null
        Write-Status "Verzeichnis '$OutputDir' erstellt" "Success"
    }

    # Setze die GitHub API URL
    $apiUrl = "https://api.github.com/repos/$Owner/$Repo/releases/latest"

    # Füge Token hinzu, wenn vorhanden
    $headers = @{}
    if ($GitHubToken) {
        $headers["Authorization"] = "token $GitHubToken"
        Write-Status "Verwende GitHub Token für Authentication" "Info"
    }

    Write-Status "Hole Release-Informationen von GitHub..." "Info"
    
    # Hol die neueste Release
    $response = Invoke-RestMethod -Uri $apiUrl -Headers $headers -ErrorAction Stop
    
    if (-not $response) {
        throw "Keine Release gefunden"
    }

    # Bestimme Release-Name
    $releaseName = if ($response.name) { $response.name } else { $response.tag_name }
    Write-Status "Aktuelle Version: $releaseName" "Success"

    # Suche nach APK-Datei
    $apkAsset = $response.assets | Where-Object { $_.name -match "\.apk$" } | Select-Object -First 1

    if (-not $apkAsset) {
        throw "Keine APK-Datei in der neuesten Release gefunden. Verfügbare Dateien: $($response.assets.name -join ', ')"
    }

    $apkUrl = $apkAsset.browser_download_url
    $apkName = $apkAsset.name
    $apkPath = Join-Path $OutputDir $apkName

    Write-Status "Lade APK herunter: $apkName" "Info"
    
    # Lade APK herunter
    Invoke-WebRequest -Uri $apkUrl -OutFile $apkPath -ErrorAction Stop

    if (Test-Path $apkPath) {
        $fileSize = (Get-Item $apkPath).Length / 1MB
        Write-Status "APK erfolgreich heruntergeladen: $apkPath ($([Math]::Round($fileSize, 2)) MB)" "Success"

        # Berechne SHA-256 Hash
        $sha256 = (Get-FileHash -Path $apkPath -Algorithm SHA256).Hash.ToLower()
        Write-Status "SHA-256: $sha256" "Info"

        # Aktualisiere release.json
        $releaseJsonPath = Join-Path (Split-Path $PSScriptRoot) "release.json"
        
        if (Test-Path $releaseJsonPath) {
            $releaseJson = Get-Content $releaseJsonPath -Raw | ConvertFrom-Json
        } else {
            $releaseJson = @{}
        }

        # Aktualisiere Release-Metadaten
        $releaseJson | Add-Member -NotePropertyName "versionName" -NotePropertyValue ($response.tag_name -replace "^v", "") -Force
        $releaseJson | Add-Member -NotePropertyName "apkUrl" -NotePropertyValue "./releases/$apkName" -Force
        $releaseJson | Add-Member -NotePropertyName "sha256" -NotePropertyValue $sha256 -Force
        $releaseJson | Add-Member -NotePropertyName "apkSize" -NotePropertyValue "$([Math]::Round($fileSize, 2))" -Force
        $releaseJson | Add-Member -NotePropertyName "releaseDate" -NotePropertyValue ($response.published_at -split "T")[0] -Force
        
        # Parse Release Notes
        $notes = @()
        if ($response.body) {
            $notes = $response.body -split "`n" | Where-Object { $_.Trim() -match "^-\s*" } | ForEach-Object { $_.Trim() -replace "^-\s*", "" } | Where-Object { $_ -ne "" }
        }
        if ($notes.Count -eq 0) {
            $notes = @("Neuer Release veröffentlicht")
        }
        $releaseJson | Add-Member -NotePropertyName "releaseNotes" -NotePropertyValue $notes -Force

        # Speichere release.json
        $releaseJson | ConvertTo-Json | Set-Content $releaseJsonPath
        Write-Status "release.json aktualisiert" "Success"

        Write-Status "Download abgeschlossen! Die Website wird automatisch aktualisiert." "Success"
    } else {
        throw "APK-Datei nicht gefunden nach Download"
    }
} catch {
    Write-Status "Fehler: $_" "Error"
    exit 1
}

