#Requires -Version 5.1
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'ApkVersioning.psm1') -Force
$workspace = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$fixtureRoot = Join-Path $workspace ('.tools\script-tests-' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixtureRoot)
$script:passed = 0

function Assert-Equal($Expected, $Actual, [string]$Message) {
    if ($Expected -ne $Actual) { throw "$Message. Atteso: '$Expected'; ottenuto: '$Actual'." }
}
function Assert-Fails([scriptblock]$Action, [string]$Message) {
    $failed = $false
    try { & $Action | Out-Null } catch { $failed = $true }
    if (-not $failed) { throw $Message }
}
function New-Fixture([string]$Name, [string]$Version = '1.0.0', [int]$Code = 1) {
    $path = Join-Path $fixtureRoot $Name
    [void][IO.Directory]::CreateDirectory($path)
    [IO.File]::WriteAllText((Join-Path $path 'version.properties'), "versionCode=$Code`nversionName=$Version`n")
    $path
}
function Write-FakeGradleOutput([string]$Project, $Version) {
    $output = Join-Path $Project 'app\build\outputs\apk\debug'
    [void][IO.Directory]::CreateDirectory($output)
    [IO.File]::WriteAllBytes((Join-Path $output 'app-debug.apk'), [byte[]]@(80, 75, 3, 4, 1, 2, 3, 4))
    $metadata = @{
        applicationId = 'it.mappinando.app'
        elements = @(@{ versionCode = $Version.VersionCode; versionName = $Version.VersionName; outputFile = 'app-debug.apk' })
    } | ConvertTo-Json -Depth 4
    [IO.File]::WriteAllText((Join-Path $output 'output-metadata.json'), $metadata)
}
function Test-Case([string]$Name, [scriptblock]$Action) {
    & $Action
    $script:passed++
    Write-Host ("PASS: $Name") -ForegroundColor Green
}
$fakeBuild = { param($project, $version) Write-FakeGradleOutput $project $version }

try {
    Test-Case 'Incrementi patch, minor e major con codice monotono' {
        $current = [pscustomobject]@{ VersionName = '1.9.9'; VersionCode = 41 }
        Assert-Equal '1.9.10' (Get-NextAppVersion $current patch).VersionName 'Incremento patch'
        Assert-Equal '1.10.0' (Get-NextAppVersion $current minor).VersionName 'Incremento minor'
        Assert-Equal '2.0.0' (Get-NextAppVersion $current major).VersionName 'Incremento major'
        Assert-Equal 42 (Get-NextAppVersion $current major).VersionCode 'Incremento codice'
    }
    Test-Case 'Versioni non valide e overflow vengono rifiutati' {
        $path = New-Fixture 'invalid'
        foreach ($invalid in @("versionCode=1`nversionName=01.0.0", "versionCode=0`nversionName=1.0.0", "versionCode=1`nversionName=1.0.0`nversionCode=2")) {
            [IO.File]::WriteAllText((Join-Path $path 'version.properties'), $invalid)
            Assert-Fails { Read-AppVersion (Join-Path $path 'version.properties') } 'Versione malformata accettata'
        }
        Assert-Fails { Get-NextAppVersion ([pscustomobject]@{ VersionName = '1.0.0'; VersionCode = 2100000000 }) } 'Overflow codice accettato'
        Assert-Fails { Get-NextAppVersion ([pscustomobject]@{ VersionName = '1.0.2147483647'; VersionCode = 1 }) } 'Overflow patch accettato'
    }
    Test-Case 'DryRun non compila e non cambia la versione' {
        $path = New-Fixture 'dry run con spazi'
        $before = [IO.File]::ReadAllText((Join-Path $path 'version.properties'))
        $result = Invoke-VersionedApkBuild $path -DryRun -BuildAction { throw 'Build invocata da DryRun' }
        Assert-Equal '1.0.1' $result.VersionName 'Versione prevista'
        Assert-Equal $before ([IO.File]::ReadAllText((Join-Path $path 'version.properties'))) 'Versione modificata'
        Assert-Equal $false ([IO.Directory]::Exists((Join-Path $path 'releases'))) 'Cartella artifact creata da DryRun'
        Assert-Equal $false ([IO.File]::Exists((Join-Path $path '.build-apk.lock'))) 'Lock non rilasciato'
    }
    Test-Case 'Build fallita non consuma la versione e rilascia il lock' {
        $path = New-Fixture 'failed-build'
        $before = [IO.File]::ReadAllText((Join-Path $path 'version.properties'))
        Assert-Fails { Invoke-VersionedApkBuild $path -BuildAction { throw 'Gradle simulato fallito' } } 'Build fallita accettata'
        Assert-Equal $before ([IO.File]::ReadAllText((Join-Path $path 'version.properties'))) 'Incremento su errore'
        $next = Invoke-VersionedApkBuild $path -DryRun -BuildAction $fakeBuild
        Assert-Equal '1.0.1' $next.VersionName 'Lock o versione non ripristinati'
    }
    Test-Case 'Metadati APK incoerenti impediscono la promozione' {
        $path = New-Fixture 'wrong-metadata'
        Assert-Fails {
            Invoke-VersionedApkBuild $path -BuildAction {
                param($project, $version)
                Write-FakeGradleOutput $project ([pscustomobject]@{ VersionName = '1.0.0'; VersionCode = 1 })
            }
        } 'Metadati incoerenti accettati'
        Assert-Equal 1 (Read-AppVersion (Join-Path $path 'version.properties')).VersionCode 'Incremento su metadata errato'
        Assert-Equal $false ([IO.File]::Exists((Join-Path $path 'releases\Mappinando-1.0.1-debug.apk'))) 'APK errato pubblicato'
    }
    Test-Case 'Build riuscita pubblica APK, hash e dettagli prima di aggiornare la versione' {
        $path = New-Fixture 'success with spaces'
        $result = Invoke-VersionedApkBuild $path -BuildAction $fakeBuild
        $saved = Read-AppVersion (Join-Path $path 'version.properties')
        Assert-Equal '1.0.1' $saved.VersionName 'Versione non salvata'
        Assert-Equal 2 $saved.VersionCode 'Codice non salvato'
        $hash = (Get-FileHash -LiteralPath $result.ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
        Assert-Equal ($hash + '  Mappinando-1.0.1-debug.apk') ([IO.File]::ReadAllText($result.HashPath).Trim()) 'Hash errato'
        $info = [IO.File]::ReadAllText($result.InfoPath) | ConvertFrom-Json
        Assert-Equal $hash $info.sha256 'Hash nei dettagli errato'
        Assert-Equal 2 $info.versionCode 'Codice nei dettagli errato'
        Assert-Equal 0 @(Get-ChildItem -LiteralPath (Join-Path $path 'releases') -Directory -Filter '.build-apk-*').Count 'Staging non pulito'
    }
    Test-Case 'Artifact preesistenti non vengono sovrascritti' {
        $path = New-Fixture 'preexisting-artifact'
        [void][IO.Directory]::CreateDirectory((Join-Path $path 'releases'))
        $artifact = Join-Path $path 'releases\Mappinando-1.0.1-debug.apk'
        [IO.File]::WriteAllText($artifact, 'originale')
        Assert-Fails { Invoke-VersionedApkBuild $path -BuildAction { throw 'Non deve compilare' } } 'Collisione accettata'
        Assert-Equal 'originale' ([IO.File]::ReadAllText($artifact)) 'File esistente modificato'
        Assert-Equal 1 (Read-AppVersion (Join-Path $path 'version.properties')).VersionCode 'Versione incrementata'
    }
    Test-Case 'Collisione durante la build preserva artifact esterno e versione' {
        $path = New-Fixture 'race-artifact'
        Assert-Fails {
            Invoke-VersionedApkBuild $path -BuildAction {
                param($project, $version)
                Write-FakeGradleOutput $project $version
                [void][IO.Directory]::CreateDirectory((Join-Path $project 'releases'))
                [IO.File]::WriteAllText((Join-Path $project 'releases\Mappinando-1.0.1-debug.apk'), 'arrivato durante build')
            }
        } 'Collisione tardiva accettata'
        Assert-Equal 'arrivato durante build' ([IO.File]::ReadAllText((Join-Path $path 'releases\Mappinando-1.0.1-debug.apk'))) 'Artifact altrui modificato'
        Assert-Equal 1 (Read-AppVersion (Join-Path $path 'version.properties')).VersionCode 'Versione incrementata'
        Assert-Equal 0 @(Get-ChildItem -LiteralPath (Join-Path $path 'releases') -Directory -Filter '.build-apk-*').Count 'Staging non pulito'
    }
    Test-Case 'Promozione parziale fallita rimuove solo i propri artifact' {
        $path = New-Fixture 'partial-promotion'
        Assert-Fails {
            Invoke-VersionedApkBuild $path -BuildAction {
                param($project, $version)
                Write-FakeGradleOutput $project $version
                [void][IO.Directory]::CreateDirectory((Join-Path $project 'releases'))
                [IO.File]::WriteAllText((Join-Path $project 'releases\Mappinando-1.0.1-debug.apk.sha256'), 'hash esterno')
            }
        } 'Promozione parziale accettata'
        Assert-Equal $false ([IO.File]::Exists((Join-Path $path 'releases\Mappinando-1.0.1-debug.apk'))) 'APK non confermato rimasto pubblicato'
        Assert-Equal 'hash esterno' ([IO.File]::ReadAllText((Join-Path $path 'releases\Mappinando-1.0.1-debug.apk.sha256'))) 'File esterno modificato'
        Assert-Equal 1 (Read-AppVersion (Join-Path $path 'version.properties')).VersionCode 'Versione consumata su promozione parziale'
    }
    Test-Case 'Lock concorrente non viene chiuso o cancellato' {
        $path = New-Fixture 'concurrent-lock'
        $lockPath = Join-Path $path '.build-apk.lock'
        $otherLock = New-Object IO.FileStream($lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        try {
            Assert-Fails { Invoke-VersionedApkBuild $path -DryRun -BuildAction $fakeBuild } 'Build concorrente accettata'
            Assert-Equal $true $otherLock.CanRead 'Lock altrui chiuso'
            Assert-Equal $true ([IO.File]::Exists($lockPath)) 'Lock altrui cancellato'
        } finally { $otherLock.Dispose() }
    }
    Test-Case 'Una versione salvata piu vecchia della cronologia viene rifiutata' {
        $path = New-Fixture 'history-rollback'
        [void][IO.Directory]::CreateDirectory((Join-Path $path 'releases'))
        [IO.File]::WriteAllText((Join-Path $path 'releases\Mappinando-2.0.0-debug.apk.build.json'), '{"versionCode":9}')
        Assert-Fails { Invoke-VersionedApkBuild $path -DryRun -BuildAction $fakeBuild } 'Downgrade del codice accettato'
    }
    Test-Case 'Due generazioni consecutive mantengono entrambi i pacchetti' {
        $path = New-Fixture 'sequential'
        $first = Invoke-VersionedApkBuild $path -BuildAction $fakeBuild
        $second = Invoke-VersionedApkBuild $path -BuildAction $fakeBuild
        Assert-Equal '1.0.2' $second.VersionName 'Versione successiva errata'
        Assert-Equal 3 $second.VersionCode 'Codice successivo errato'
        Assert-Equal $true ([IO.File]::Exists($first.ApkPath)) 'Primo artifact perso'
        Assert-Equal $true ([IO.File]::Exists($second.ApkPath)) 'Secondo artifact perso'
    }
    Write-Host ("`n$script:passed test dello script superati. Nessuna build Gradle avviata.") -ForegroundColor Green
} finally {
    $resolvedFixture = [IO.Path]::GetFullPath($fixtureRoot)
    $allowedPrefix = [IO.Path]::GetFullPath((Join-Path $workspace '.tools')) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolvedFixture.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase) -or
        -not ([IO.Path]::GetFileName($resolvedFixture)).StartsWith('script-tests-')) {
        throw 'Pulizia test interrotta: percorso fixture fuori dalla cartella prevista.'
    }
    Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
}
