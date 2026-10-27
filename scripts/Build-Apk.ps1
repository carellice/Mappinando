#Requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('patch', 'minor', 'major')][string]$Increment = 'patch',
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try {
    Import-Module (Join-Path $PSScriptRoot 'ApkVersioning.psm1') -Force
    $projectPath = Split-Path -Parent $PSScriptRoot
    $result = Invoke-VersionedApkBuild -ProjectRoot $projectPath -Increment $Increment -DryRun:$DryRun -BuildAction {
        param($buildProjectPath, $candidate)
        $wrapper = Join-Path $buildProjectPath 'gradlew.bat'
        if (-not [IO.File]::Exists($wrapper)) { throw "Wrapper Gradle mancante: $wrapper" }
        Push-Location -LiteralPath $buildProjectPath
        try {
            $gradleArguments = @(
                ':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', '--console', 'plain',
                ('-PmappinandoVersionCode=' + $candidate.VersionCode),
                ('-PmappinandoVersionName=' + $candidate.VersionName)
            )
            & $wrapper @gradleArguments
            if ($LASTEXITCODE -ne 0) { throw "Gradle ha restituito errore $LASTEXITCODE. La versione non e stata incrementata." }
        } finally { Pop-Location }
    }
    if ($result.DryRun) {
        Write-Host ("Prossima versione: {0} (versionCode {1})" -f $result.VersionName, $result.VersionCode)
        Write-Host ("APK previsto: {0}" -f $result.ApkPath)
        Write-Host 'Anteprima completata: nessuna compilazione e nessun incremento salvato.'
    } else {
        Write-Host ''
        Write-Host ("APK pronto: {0}" -f $result.ApkPath) -ForegroundColor Green
        Write-Host ("Versione salvata: {0} (versionCode {1})" -f $result.VersionName, $result.VersionCode)
        Write-Host ("SHA-256: {0}" -f $result.HashPath)
        Write-Host ("Dettagli build: {0}" -f $result.InfoPath)
    }
    exit 0
} catch {
    Write-Host ''
    Write-Host ('Generazione APK interrotta: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
