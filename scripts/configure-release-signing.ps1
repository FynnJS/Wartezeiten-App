param(
    [string]$Repository = "FynnJS/Wartezeiten-App",
    [string]$BackupDirectory = (Join-Path $env:USERPROFILE "Documents\Wartezeiten-App-signing"),
    [string]$KeyAlias = "wartezeiten-release",
    [string]$GitHubCliPath = "",
    [switch]$AllowNewCertificate
)

$ErrorActionPreference = "Stop"

function New-SigningPassword {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_")
}

$keytoolCandidates = @()
if ($env:JAVA_HOME) {
    $keytoolCandidates += Join-Path $env:JAVA_HOME "bin\keytool.exe"
}
$keytoolCandidates += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
$keytoolCandidates = $keytoolCandidates | Where-Object { Test-Path -LiteralPath $_ }
$keytool = $keytoolCandidates | Select-Object -First 1
if (-not $keytool) {
    throw "keytool.exe was not found. Install Android Studio or set JAVA_HOME."
}

$gh = if ($GitHubCliPath) {
    Get-Item -LiteralPath $GitHubCliPath -ErrorAction SilentlyContinue
} else {
    Get-Command gh -ErrorAction SilentlyContinue
}
if (-not $gh) {
    throw "GitHub CLI (gh) is required. Install it and run 'gh auth login' first."
}

New-Item -ItemType Directory -Path $BackupDirectory -Force | Out-Null
$keystorePath = Join-Path $BackupDirectory "release-keystore.jks"
$propertiesStorePath = $keystorePath.Replace("\", "/")
$backupPropertiesPath = Join-Path $BackupDirectory "keystore.properties"

if (Test-Path -LiteralPath $backupPropertiesPath) {
    $properties = @{}
    Get-Content -LiteralPath $backupPropertiesPath | ForEach-Object {
        if ($_ -match '^([^#][^=]*)=(.*)$') {
            $properties[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    $storePassword = $properties.storePassword
    $keyPassword = $properties.keyPassword
    $KeyAlias = $properties.keyAlias
} elseif (Test-Path -LiteralPath $keystorePath) {
    throw "A keystore exists without its password backup: $keystorePath"
} else {
    $storePassword = New-SigningPassword
    $keyPassword = $storePassword
    & $keytool -genkeypair -v -keystore $keystorePath -storepass $storePassword `
        -keypass $keyPassword -storetype JKS -alias $KeyAlias -keyalg RSA -keysize 4096 -validity 10000 `
        -dname "CN=Wartezeiten App, OU=Release, O=FynnJS, C=DE"
    if ($LASTEXITCODE -ne 0) {
        throw "Release keystore creation failed."
    }
    @(
        "storeFile=$propertiesStorePath"
        "storePassword=$storePassword"
        "keyAlias=$KeyAlias"
        "keyPassword=$keyPassword"
    ) | Set-Content -LiteralPath $backupPropertiesPath -Encoding ASCII
}

$certificateOutput = & $keytool -list -v -keystore $keystorePath -storepass $storePassword -alias $KeyAlias
$certificateLine = $certificateOutput | Select-String 'SHA256:' | Select-Object -First 1
if (-not $certificateLine) {
    throw "Could not read the release certificate fingerprint."
}
$certificateSha256 = ($certificateLine.ToString() -replace '.*SHA256:\s*', '' -replace ':', '').Trim().ToLowerInvariant()
$canonicalCertificatePath = Join-Path $PSScriptRoot "..\config\release-signing.properties"
if (Test-Path -LiteralPath $canonicalCertificatePath) {
    $canonicalCertificateLine = Get-Content -LiteralPath $canonicalCertificatePath |
        Where-Object { $_ -match '^\s*releaseCertSha256\s*=' } |
        Select-Object -First 1
    $canonicalCertificateSha256 = if ($canonicalCertificateLine) {
        ($canonicalCertificateLine -replace '^\s*releaseCertSha256\s*=\s*', '' -replace ':', '').Trim().ToLowerInvariant()
    } else {
        ""
    }
    if ($canonicalCertificateSha256 -and $certificateSha256 -ne $canonicalCertificateSha256 -and -not $AllowNewCertificate) {
        throw "The configured keystore certificate ($certificateSha256) does not match the canonical release certificate ($canonicalCertificateSha256). Restore the original release keystore instead of publishing an update-incompatible APK. Use -AllowNewCertificate only for an intentional package/signing lineage reset."
    }
}
$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))

$secrets = [ordered]@{
    RELEASE_KEYSTORE_BASE64 = $keystoreBase64
    RELEASE_STORE_PASSWORD = $storePassword
    RELEASE_KEY_ALIAS = $KeyAlias
    RELEASE_KEY_PASSWORD = $keyPassword
}
foreach ($entry in $secrets.GetEnumerator()) {
    $ghExecutable = if ($gh.Source) { $gh.Source } else { $gh.FullName }
    $entry.Value | & $ghExecutable secret set $entry.Key --repo $Repository
    if ($LASTEXITCODE -ne 0) {
        throw "Could not set GitHub secret '$($entry.Key)'."
    }
}

@(
    "storeFile=$propertiesStorePath"
    "storePassword=$storePassword"
    "keyAlias=$KeyAlias"
    "keyPassword=$keyPassword"
) | Set-Content -LiteralPath (Join-Path $PSScriptRoot "..\keystore.properties") -Encoding ASCII

Write-Host "Release signing configured for $Repository."
Write-Host "Certificate SHA-256: $certificateSha256"
Write-Host "Back up this directory securely: $BackupDirectory"
