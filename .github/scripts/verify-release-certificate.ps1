param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,
    [string]$CanonicalFile = "config/release-signing.properties"
)

$ErrorActionPreference = "Stop"

function Write-GitHubError {
    param([string]$Message)
    Write-Error "::error::$Message"
}

function Normalize-CertificateFingerprint {
    param([string]$Value)
    if (-not $Value) {
        return ""
    }
    return ($Value -replace '[:\s]', '').Trim().ToLowerInvariant()
}

if (-not (Test-Path -LiteralPath $ApkPath)) {
    Write-GitHubError "Release APK was not found: $ApkPath"
    exit 1
}

if (-not (Test-Path -LiteralPath $CanonicalFile)) {
    Write-GitHubError "Canonical release certificate file is missing: $CanonicalFile"
    exit 1
}

$canonicalLine = Get-Content -LiteralPath $CanonicalFile |
    Where-Object { $_ -match '^\s*releaseCertSha256\s*=' } |
    Select-Object -First 1
$canonicalCert = if ($canonicalLine) {
    Normalize-CertificateFingerprint ($canonicalLine -replace '^\s*releaseCertSha256\s*=\s*', '')
} else {
    ""
}
if (-not $canonicalCert) {
    Write-GitHubError "Canonical release certificate fingerprint is empty in $CanonicalFile"
    exit 1
}

$secretCert = Normalize-CertificateFingerprint $env:RELEASE_CERT_SHA256
if (-not $secretCert) {
    Write-GitHubError "RELEASE_CERT_SHA256 secret is required."
    exit 1
}
if ($secretCert -ne $canonicalCert) {
    Write-GitHubError "RELEASE_CERT_SHA256 secret does not match the canonical release certificate in $CanonicalFile. Expected $canonicalCert but secret contains $secretCert."
    exit 1
}

$apksigner = $env:APKSIGNER
if (-not $apksigner) {
    if (-not $env:ANDROID_HOME) {
        Write-GitHubError "ANDROID_HOME is not set and APKSIGNER was not provided."
        exit 1
    }
    $apksigner = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME "build-tools") -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -in @("apksigner", "apksigner.bat") } |
        Sort-Object FullName |
        Select-Object -Last 1 -ExpandProperty FullName
}
if (-not $apksigner -or -not (Test-Path -LiteralPath $apksigner)) {
    Write-GitHubError "apksigner was not found."
    exit 1
}

$certificateOutput = & $apksigner verify --print-certs $ApkPath
if ($LASTEXITCODE -ne 0) {
    Write-GitHubError "apksigner verification failed for $ApkPath."
    exit 1
}

$actualLine = $certificateOutput |
    Select-String 'certificate SHA-256 digest:' |
    Select-Object -First 1
$actualCert = if ($actualLine) {
    Normalize-CertificateFingerprint ($actualLine.ToString() -replace '.*certificate SHA-256 digest:\s*', '')
} else {
    ""
}
if (-not $actualCert) {
    Write-GitHubError "Could not read certificate SHA-256 digest from $ApkPath."
    exit 1
}

if ($actualCert -ne $canonicalCert) {
    Write-GitHubError "Release certificate mismatch. Expected $canonicalCert but built $actualCert."
    exit 1
}

Write-Host "Release certificate verified: $actualCert"
