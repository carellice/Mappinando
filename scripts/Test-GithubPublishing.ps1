#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'GithubPublishing.psm1') -Force
$root = Join-Path (Split-Path -Parent $PSScriptRoot) ('.tools\publish-tests-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($root)
$passed = 0
function Assert-True($Value, [string]$Message) { if (-not $Value) { throw $Message } }
function Assert-Fails([scriptblock]$Action) { $failed = $false; try { & $Action | Out-Null } catch { $failed = $true }; Assert-True $failed 'Doveva essere rifiutato.' }
function Test-Case([string]$Name, [scriptblock]$Action) { & $Action; $script:passed++; Write-Host "PASS: $Name" }
function New-Info {
    [pscustomobject]@{ applicationId = 'it.mappinando.app'; versionName = '1.2.3'; versionCode = 20; apk = 'Mappinando-1.2.3-debug.apk'; buildType = 'debug'; sha256 = ('a' * 64); sizeBytes = 12345 }
}
try {
    Test-Case 'Configurazione pubblica e certificato letti senza credenziali' {
        $file = Join-Path $root 'release.properties'
        [IO.File]::WriteAllText($file, "githubRepository=owner/Mappinando`nsigningCertificateSha256=$('a' * 64)`n")
        $config = Read-DistributionConfig $file
        Assert-True ($config.Repository -eq 'owner/Mappinando') 'Repository errata.'
    }
    Test-Case 'Repository e chiave di configurazione non valide rifiutate' {
        $file = Join-Path $root 'bad.properties'
        [IO.File]::WriteAllText($file, "githubRepository=https://other/owner/repo`nsigningCertificateSha256=$('a' * 64)`n")
        Assert-Fails { Read-DistributionConfig $file }
        [IO.File]::WriteAllText($file, "githubRepository=owner/repo`ngithubRepository=other/repo`nsigningCertificateSha256=$('a' * 64)`n")
        Assert-Fails { Read-DistributionConfig $file }
    }
    Test-Case 'Manifest coerente con i metadati della build' {
        $manifest = New-AppUpdateManifest (New-Info)
        Assert-True ($manifest.schemaVersion -eq 1 -and $manifest.versionCode -eq 20 -and $manifest.minSdk -eq 26) 'Manifest errato.'
        Assert-True ($manifest.apk -eq 'Mappinando-1.2.3-debug.apk') 'Nome APK errato.'
    }
    Test-Case 'Percorsi arbitrari, hash e dimensioni non validi rifiutati' {
        $info = New-Info; $info.apk = '../other.apk'; Assert-Fails { New-AppUpdateManifest $info }
        $info = New-Info; $info.sha256 = 'bad'; Assert-Fails { New-AppUpdateManifest $info }
        $info = New-Info; $info.sizeBytes = 0; Assert-Fails { New-AppUpdateManifest $info }
    }
    Test-Case 'Asset mancanti o caricati parzialmente impediscono la pubblicazione' {
        $asset = Join-Path $root 'app.apk'; [IO.File]::WriteAllBytes($asset, [byte[]]@(1, 2, 3))
        $release = [pscustomobject]@{ assets = @() }; Assert-Fails { Assert-ReleaseAssets $release @($asset) }
        $release.assets = @([pscustomobject]@{ name = 'app.apk'; state = 'uploaded'; size = 2 }); Assert-Fails { Assert-ReleaseAssets $release @($asset) }
        $release.assets = @([pscustomobject]@{ name = 'app.apk'; state = 'uploaded'; size = 3; digest = ('sha256:' + ('a' * 64)) })
        Assert-ReleaseAssets $release @($asset) ('a' * 64)
        Assert-Fails { Assert-ReleaseAssets $release @($asset) ('b' * 64) }
    }
    Test-Case 'Ripresa accetta correzioni agli strumenti ma rifiuta modifiche al codice APK' {
        Assert-ResumeChanges " M scripts/Publish-Release.ps1`n M scripts/GithubPublishing.psm1"
        Assert-ResumeChanges " M version.properties"
        Assert-Fails { Assert-ResumeChanges " M app/src/main/AndroidManifest.xml" }
        Assert-Fails { Assert-ResumeChanges "?? other-file.txt" }
    }
    Write-Host "$passed test pubblicazione superati. Nessun comando GitHub eseguito."
} finally {
    # The fixture directory is retained inside .tools for inspection; no external files are changed.
}
