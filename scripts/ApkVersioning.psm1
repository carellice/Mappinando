Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-AppVersion {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][string]$Path)

    $properties = @{}
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        $clean = $line.Trim()
        if ($clean.Length -eq 0 -or $clean.StartsWith('#') -or $clean.StartsWith('!')) { continue }
        if ($clean -notmatch '^(versionName|versionCode)\s*=\s*(\S+)\s*$') {
            throw "Riga non valida in version.properties: $clean"
        }
        $key = $Matches[1]
        if ($properties.ContainsKey($key)) { throw "Proprieta duplicata: $key" }
        $properties[$key] = $Matches[2]
    }
    if (-not $properties.ContainsKey('versionName') -or -not $properties.ContainsKey('versionCode')) {
        throw 'version.properties deve contenere versionName e versionCode.'
    }
    if ($properties.versionName -notmatch '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$') {
        throw 'versionName deve avere il formato major.minor.patch, per esempio 1.0.0.'
    }
    foreach ($part in $properties.versionName.Split('.')) {
        if ([long]$part -gt [int]::MaxValue) { throw 'Una componente di versionName e troppo grande.' }
    }
    if ($properties.versionCode -notmatch '^[1-9][0-9]*$' -or [long]$properties.versionCode -gt 2100000000) {
        throw 'versionCode deve essere un intero compreso tra 1 e 2100000000.'
    }
    [pscustomobject]@{ VersionName = $properties.versionName; VersionCode = [int]$properties.versionCode }
}

function Get-NextAppVersion {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)]$Current,
        [ValidateSet('patch', 'minor', 'major')][string]$Increment = 'patch'
    )

    $parts = @($Current.VersionName.Split('.') | ForEach-Object { [long]$_ })
    switch ($Increment) {
        'major' { $parts[0]++; $parts[1] = 0; $parts[2] = 0 }
        'minor' { $parts[1]++; $parts[2] = 0 }
        'patch' { $parts[2]++ }
    }
    if (@($parts | Where-Object { $_ -gt [int]::MaxValue }).Count -gt 0) {
        throw 'La versione successiva supera il limite numerico supportato.'
    }
    $code = [long]$Current.VersionCode + 1
    if ($code -gt 2100000000) { throw 'Raggiunto il limite Android di versionCode.' }
    [pscustomobject]@{ VersionName = ($parts -join '.'); VersionCode = [int]$code }
}

function Write-Utf8File {
    param([string]$Path, [string]$Text)
    [IO.File]::WriteAllText($Path, $Text, (New-Object Text.UTF8Encoding($false)))
}

function Remove-OwnedBuildFile {
    param([string]$Path)
    if ($Path -and [IO.File]::Exists($Path)) {
        try { [IO.File]::Delete($Path) }
        catch { Write-Warning ("Impossibile pulire il file temporaneo di questa build: $Path") }
    }
}

function Invoke-VersionedApkBuild {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [ValidateSet('patch', 'minor', 'major')][string]$Increment = 'patch',
        [switch]$DryRun,
        [Parameter(Mandatory = $true)][scriptblock]$BuildAction
    )

    $projectPath = [IO.Path]::GetFullPath($ProjectRoot)
    $versionPath = Join-Path $projectPath 'version.properties'
    if (-not [IO.File]::Exists($versionPath)) { throw "File versione mancante: $versionPath" }
    $releaseDirectory = Join-Path $projectPath 'releases'
    $lockPath = Join-Path $projectPath '.build-apk.lock'
    $lock = $null
    $stageDirectory = $null
    $temporaryVersionPath = $null
    $stagedFiles = New-Object 'System.Collections.Generic.List[string]'
    $promotedFiles = New-Object 'System.Collections.Generic.List[string]'
    $committed = $false

    try {
        try {
            # The handle, rather than the file's mere existence, owns the lock.
            # A terminated process releases it; DeleteOnClose avoids stale lock files.
            $lock = New-Object IO.FileStream(
                $lockPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite,
                [IO.FileShare]::None, 4096, [IO.FileOptions]::DeleteOnClose
            )
        } catch {
            throw 'Un''altra generazione APK e gia in corso, oppure la cartella non e scrivibile.'
        }
        $lock.SetLength(0)
        $lockBytes = [Text.Encoding]::UTF8.GetBytes("PID=$PID`r`nStartedUtc=$([DateTime]::UtcNow.ToString('o'))`r`n")
        $lock.Write($lockBytes, 0, $lockBytes.Length)
        $lock.Flush()

        $current = Read-AppVersion -Path $versionPath
        $candidate = Get-NextAppVersion -Current $current -Increment $Increment
        $artifactName = 'Mappinando-{0}-debug.apk' -f $candidate.VersionName
        $apkPath = Join-Path $releaseDirectory $artifactName
        $hashPath = $apkPath + '.sha256'
        $infoPath = $apkPath + '.build.json'
        foreach ($path in @($apkPath, $hashPath, $infoPath)) {
            if ([IO.File]::Exists($path)) {
                throw "Esiste gia un pacchetto per la versione $($candidate.VersionName): $path. Non viene sovrascritto. Controlla version.properties e la cronologia in releases."
            }
        }
        if ([IO.Directory]::Exists($releaseDirectory)) {
            foreach ($infoFile in Get-ChildItem -LiteralPath $releaseDirectory -Filter 'Mappinando-*-debug.apk.build.json' -File) {
                $published = [IO.File]::ReadAllText($infoFile.FullName) | ConvertFrom-Json
                if ([long]$published.versionCode -ge $candidate.VersionCode) {
                    throw "version.properties precede una build gia pubblicata (versionCode $($published.versionCode)). Ripristina la versione piu recente prima di compilare."
                }
            }
        }
        if ($DryRun) {
            return [pscustomobject]@{
                VersionName = $candidate.VersionName; VersionCode = $candidate.VersionCode
                ApkPath = $apkPath; HashPath = $hashPath; InfoPath = $infoPath; DryRun = $true
            }
        }

        Write-Host ("Versione: {0} ({1}) -> {2} ({3})" -f $current.VersionName, $current.VersionCode, $candidate.VersionName, $candidate.VersionCode) -ForegroundColor Cyan
        & $BuildAction $projectPath $candidate | Out-Host

        $outputDirectory = Join-Path $projectPath 'app\build\outputs\apk\debug'
        $metadataPath = Join-Path $outputDirectory 'output-metadata.json'
        if (-not [IO.File]::Exists($metadataPath)) { throw 'Gradle non ha prodotto output-metadata.json.' }
        $metadata = [IO.File]::ReadAllText($metadataPath) | ConvertFrom-Json
        $elements = @($metadata.elements)
        if ($metadata.applicationId -ne 'it.mappinando.app' -or $elements.Count -ne 1) {
            throw 'Output Gradle inatteso: deve contenere un solo APK di Mappinando.'
        }
        $element = $elements[0]
        if ([int]$element.versionCode -ne $candidate.VersionCode -or $element.versionName -ne $candidate.VersionName) {
            throw 'La versione incorporata nell''APK non corrisponde alla versione candidata. Nessun incremento salvato.'
        }
        $outputFile = [string]$element.outputFile
        if ([IO.Path]::GetFileName($outputFile) -ne $outputFile -or -not $outputFile.EndsWith('.apk')) {
            throw 'Nome APK non valido nei metadati Gradle.'
        }
        $sourceApk = Join-Path $outputDirectory $outputFile
        if (-not [IO.File]::Exists($sourceApk) -or (Get-Item -LiteralPath $sourceApk).Length -eq 0) {
            throw 'APK mancante o vuoto: nessun incremento salvato.'
        }

        [void][IO.Directory]::CreateDirectory($releaseDirectory)
        $buildId = [Guid]::NewGuid().ToString('N')
        $stageDirectory = Join-Path $releaseDirectory ('.build-apk-' + $buildId)
        [void][IO.Directory]::CreateDirectory($stageDirectory)
        $stagedApk = Join-Path $stageDirectory $artifactName
        $stagedFiles.Add($stagedApk)
        [IO.File]::Copy($sourceApk, $stagedApk, $false)
        $hash = (Get-FileHash -LiteralPath $stagedApk -Algorithm SHA256).Hash.ToLowerInvariant()
        $stagedHash = Join-Path $stageDirectory ($artifactName + '.sha256')
        $stagedFiles.Add($stagedHash)
        Write-Utf8File -Path $stagedHash -Text ($hash + '  ' + $artifactName + "`r`n")
        $stagedInfo = Join-Path $stageDirectory ($artifactName + '.build.json')
        $stagedFiles.Add($stagedInfo)
        $info = [ordered]@{
            applicationId = 'it.mappinando.app'; versionName = $candidate.VersionName
            versionCode = $candidate.VersionCode; buildType = 'debug'; apk = $artifactName
            sha256 = $hash; sizeBytes = (Get-Item -LiteralPath $stagedApk).Length
            createdUtc = [DateTime]::UtcNow.ToString('o')
            checks = @('assembleDebug', 'testDebugUnitTest', 'lintDebug')
        }
        Write-Utf8File -Path $stagedInfo -Text (($info | ConvertTo-Json -Depth 4) + "`r`n")

        # Version and artifacts are prepared before promotion. Move refuses overwrites.
        $temporaryVersionPath = Join-Path $projectPath ('.build-apk-' + $buildId + '.version.tmp')
        $versionText = "# Updated only after a successful versioned APK build.`r`nversionCode=$($candidate.VersionCode)`r`nversionName=$($candidate.VersionName)`r`n"
        Write-Utf8File -Path $temporaryVersionPath -Text $versionText
        foreach ($pair in @(@($stagedApk, $apkPath), @($stagedHash, $hashPath), @($stagedInfo, $infoPath))) {
            [IO.File]::Move($pair[0], $pair[1])
            $promotedFiles.Add($pair[1])
        }
        # Commit last. A build/copy/metadata failure leaves version.properties intact.
        [IO.File]::Replace($temporaryVersionPath, $versionPath, [NullString]::Value)
        $committed = $true
        [pscustomobject]@{
            VersionName = $candidate.VersionName; VersionCode = $candidate.VersionCode
            ApkPath = $apkPath; HashPath = $hashPath; InfoPath = $infoPath; DryRun = $false
        }
    } finally {
        try {
            if (-not $committed) {
                foreach ($ownedFile in $promotedFiles) { Remove-OwnedBuildFile -Path $ownedFile }
            }
            foreach ($ownedStageFile in $stagedFiles) { Remove-OwnedBuildFile -Path $ownedStageFile }
            Remove-OwnedBuildFile -Path $temporaryVersionPath
            # No recursive deletion: only this invocation's known files and empty directory.
            if ($stageDirectory -and [IO.Directory]::Exists($stageDirectory)) {
                try { [IO.Directory]::Delete($stageDirectory, $false) }
                catch { Write-Warning ("Cartella temporanea non vuota, conservata: $stageDirectory") }
            }
        } finally {
            if ($null -ne $lock) { $lock.Dispose() }
        }
    }
}

Export-ModuleMember -Function Read-AppVersion, Get-NextAppVersion, Invoke-VersionedApkBuild
