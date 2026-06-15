param(
    [Parameter(Mandatory = $true)]
    [string]$GoogleServicesJson,

    [Parameter(Mandatory = $true)]
    [string]$ServiceAccountJson
)

$ErrorActionPreference = "Stop"
$packageName = "de.wartezeiten.app"
$statusUrl = "https://wartezeiten-app.tutorialfynn.workers.dev/push/status"

function Require-Command([string]$Name) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found."
    }
}

Require-Command "gh"
Require-Command "npx"

$googleServicesPath = (Resolve-Path $GoogleServicesJson).Path
$serviceAccountPath = (Resolve-Path $ServiceAccountJson).Path
$googleServices = Get-Content -Raw $googleServicesPath | ConvertFrom-Json
$serviceAccount = Get-Content -Raw $serviceAccountPath | ConvertFrom-Json

$androidClient = $googleServices.client | Where-Object {
    $_.client_info.android_client_info.package_name -eq $packageName
} | Select-Object -First 1

if (-not $androidClient) {
    throw "google-services.json does not contain Android package '$packageName'."
}

$apiKey = $androidClient.api_key | Select-Object -First 1 -ExpandProperty current_key
$applicationId = $androidClient.client_info.mobilesdk_app_id
$projectId = $googleServices.project_info.project_id
$senderId = $googleServices.project_info.project_number

if (-not $apiKey -or -not $applicationId -or -not $projectId -or -not $senderId) {
    throw "google-services.json is missing required Firebase Android values."
}

if ($serviceAccount.project_id -ne $projectId) {
    throw "Firebase project mismatch: google-services.json uses '$projectId', service account uses '$($serviceAccount.project_id)'."
}

if (-not $serviceAccount.client_email -or -not $serviceAccount.private_key) {
    throw "Service-account JSON is missing client_email or private_key."
}

Copy-Item -LiteralPath $googleServicesPath -Destination (Join-Path $PSScriptRoot "..\app\google-services.json") -Force

$repoVariables = [ordered]@{
    FIREBASE_APPLICATION_ID = $applicationId
    FIREBASE_API_KEY = $apiKey
    FIREBASE_PROJECT_ID = $projectId
    FIREBASE_GCM_SENDER_ID = $senderId
}

foreach ($entry in $repoVariables.GetEnumerator()) {
    & gh variable set $entry.Key --body $entry.Value
    if ($LASTEXITCODE -ne 0) {
        throw "Could not set GitHub variable '$($entry.Key)'."
    }
}

$workerSecrets = [ordered]@{
    FCM_PROJECT_ID = $projectId
    FCM_CLIENT_EMAIL = $serviceAccount.client_email
    FCM_PRIVATE_KEY = $serviceAccount.private_key
}

foreach ($entry in $workerSecrets.GetEnumerator()) {
    $entry.Value | & npx wrangler secret put $entry.Key
    if ($LASTEXITCODE -ne 0) {
        throw "Could not set Cloudflare secret '$($entry.Key)'."
    }
}

& npx wrangler d1 migrations apply wartezeiten-app-data --remote
if ($LASTEXITCODE -ne 0) {
    throw "D1 migration failed. The Worker was not deployed."
}

& npx wrangler deploy
if ($LASTEXITCODE -ne 0) {
    throw "Worker deployment failed."
}

$status = Invoke-RestMethod -Uri $statusUrl -Method Get
if (-not $status.pushReady) {
    throw "Worker deployed, but push is not ready: $($status | ConvertTo-Json -Compress)"
}

Write-Host "Standby push is ready for project '$projectId'."
