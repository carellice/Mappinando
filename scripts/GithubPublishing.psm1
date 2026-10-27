Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-DistributionConfig {
    param([Parameter(Mandatory = $true)][string]$Path)
    $values = @{}
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        $clean = $line.Trim()
        if (-not $clean -or $clean.StartsWith('#')) { continue }
        if ($clean -notmatch '^(githubRepository|signingCertificateSha256)=(\S+)$' -or $values.ContainsKey($Matches[1])) { throw 'Configurazione di distribuzione non valida.' }
        $values[$Matches[1]] = $Matches[2]
    }
    if ($values.githubRepository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$' -or $values.signingCertificateSha256 -notmatch '^[a-f0-9]{64}$') {
        throw 'Repository o certificato di firma non validi in release.properties.'
    }
    [pscustomobject]@{ Repository = $values.githubRepository; CertificateSha256 = $values.signingCertificateSha256 }
}

function New-AppUpdateManifest {
    param([Parameter(Mandatory = $true)]$BuildInfo)
    if ($BuildInfo.applicationId -ne 'it.mappinando.app' -or $BuildInfo.buildType -ne 'debug') { throw 'Tipo di APK non supportato.' }
    if ($BuildInfo.versionName -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$' -or
        [long]$BuildInfo.versionCode -lt 1 -or [long]$BuildInfo.versionCode -gt 2100000000) { throw 'Versione APK non valida.' }
    if ($BuildInfo.apk -ne ('Mappinando-{0}-debug.apk' -f $BuildInfo.versionName) -or
        $BuildInfo.sha256 -notmatch '^[a-f0-9]{64}$' -or [long]$BuildInfo.sizeBytes -lt 1 -or [long]$BuildInfo.sizeBytes -gt 268435456) { throw 'Metadati APK non validi.' }
    [pscustomobject][ordered]@{
        schemaVersion = 1; applicationId = 'it.mappinando.app'
        versionName = [string]$BuildInfo.versionName; versionCode = [int]$BuildInfo.versionCode
        minSdk = 26; apk = [string]$BuildInfo.apk; sha256 = [string]$BuildInfo.sha256
        sizeBytes = [long]$BuildInfo.sizeBytes
    }
}

function Assert-ReleaseAssets {
    param([Parameter(Mandatory = $true)]$Release, [Parameter(Mandatory = $true)][string[]]$Files, [string]$ApkSha256)
    foreach ($path in $Files) {
        $name = [IO.Path]::GetFileName($path)
        $matching = @($Release.assets | Where-Object { $_.name -eq $name })
        if ($matching.Count -ne 1 -or $matching[0].state -ne 'uploaded' -or [long]$matching[0].size -ne (Get-Item -LiteralPath $path).Length) {
            throw "Asset GitHub mancante o incompleto: $name. La release resta in bozza."
        }
        if ($name.EndsWith('.apk') -and $matching[0].PSObject.Properties.Name -contains 'digest' -and $matching[0].digest) {
            if ($matching[0].digest -ne ('sha256:' + $ApkSha256)) { throw 'Hash APK su GitHub non corrispondente.' }
        }
    }
}

Export-ModuleMember -Function Read-DistributionConfig, New-AppUpdateManifest, Assert-ReleaseAssets
