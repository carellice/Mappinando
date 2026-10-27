#Requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('patch', 'minor', 'major')][string]$Increment = 'patch',
    [switch]$Resume,
    [switch]$DryRun,
    [string]$NotesFile
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$publishLock = $null
$pushedLocation = $false

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments, [switch]$Capture)
    if ($Capture) {
        $lines = & $Executable @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Comando non riuscito: $Executable $($Arguments -join ' ')" }
        return ($lines -join "`n")
    }
    & $Executable @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Comando non riuscito: $Executable $($Arguments -join ' ')" }
}

function Write-PublishJson([string]$Path, $Value) {
    [IO.File]::WriteAllText($Path, (($Value | ConvertTo-Json -Depth 6) + "`n"), (New-Object Text.UTF8Encoding($false)))
}

try {
    Import-Module (Join-Path $PSScriptRoot 'ApkVersioning.psm1') -Force
    Import-Module (Join-Path $PSScriptRoot 'GithubPublishing.psm1') -Force
    $project = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
    $config = Read-DistributionConfig (Join-Path $project 'release.properties')
    $versionFile = Join-Path $project 'version.properties'
    $current = Read-AppVersion $versionFile
    $pendingFile = Join-Path $project '.release-pending.json'
    if ($DryRun) {
        $planned = if ($Resume) { $current } else { Get-NextAppVersion $current $Increment }
        Write-Host ("Repository: https://github.com/{0}" -f $config.Repository)
        Write-Host ("Versione: {0} (codice {1}); tag v{0}" -f $planned.VersionName, $planned.VersionCode)
        Write-Host 'Anteprima: nessuna compilazione, commit, push o pubblicazione.'
        exit 0
    }
    $publishLock = New-Object IO.FileStream((Join-Path $project '.publish-release.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None, 4096, [IO.FileOptions]::DeleteOnClose)
    Push-Location -LiteralPath $project; $pushedLocation = $true
    [void](Get-Command git -ErrorAction Stop); [void](Get-Command gh -ErrorAction Stop)
    Invoke-Checked gh @('auth', 'status')
    $remoteRepo = (Invoke-Checked gh @('repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner') -Capture).Trim()
    if ($remoteRepo -ine $config.Repository) { throw 'Il remote Git non corrisponde alla repository in release.properties.' }
    $branch = (Invoke-Checked git @('branch', '--show-current') -Capture).Trim()
    if ($branch -ne 'main') { throw 'Pubblica dal branch main, dopo aver salvato le modifiche in un commit.' }
    $head = (Invoke-Checked git @('rev-parse', 'HEAD') -Capture).Trim()
    $status = (Invoke-Checked git @('status', '--porcelain', '--untracked-files=normal') -Capture)
    if (-not $Resume -and $status) { throw 'Prima del rilascio salva tutte le modifiche in un commit. Nessuna versione incrementata.' }
    if (-not $Resume -and (Test-Path -LiteralPath $pendingFile)) { throw 'C''e un rilascio da completare. Usa -Resume per riprovare senza incrementare la versione.' }
    Invoke-Checked git @('fetch', 'origin', 'main')
    $remoteHead = (Invoke-Checked git @('rev-parse', 'origin/main') -Capture).Trim()
    if ($Resume) {
        if (-not (Test-Path -LiteralPath $pendingFile)) { throw 'Non c''e un rilascio in sospeso. Esegui lo script senza -Resume per preparare una nuova versione.' }
        $pending = [IO.File]::ReadAllText($pendingFile) | ConvertFrom-Json
        if ($pending.repository -ne $config.Repository -or $pending.versionName -ne $current.VersionName -or [int]$pending.versionCode -ne $current.VersionCode) { throw 'Il rilascio in sospeso non corrisponde alla versione locale.' }
        if ($head -ne $pending.baseCommit -and $head -ne $pending.releaseCommit) {
            # Recover the narrow crash window between committing the version and saving its ID.
            $parent = (Invoke-Checked git @('rev-parse', 'HEAD^') -Capture).Trim()
            $changed = (Invoke-Checked git @('diff-tree', '--no-commit-id', '--name-only', '-r', 'HEAD') -Capture).Trim()
            if ($parent -ne $pending.baseCommit -or $changed -ne 'version.properties') { throw 'La cronologia e cambiata dopo la build. Rilascio interrotto per evitare un APK non corrispondente ai sorgenti.' }
            $pending.releaseCommit = $head
        }
        if ($remoteHead -ne $pending.baseCommit -and $remoteHead -ne $pending.releaseCommit) { throw 'Il branch remoto e cambiato. Risolvi la sincronizzazione prima di riprendere.' }
    } else {
        if ($head -ne $remoteHead) { throw 'main non coincide con origin/main. Sincronizza prima di pubblicare.' }
        Invoke-Checked powershell.exe @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'Build-Apk.ps1'), '-Increment', $Increment)
        if ((Invoke-Checked git @('rev-parse', 'HEAD') -Capture).Trim() -ne $head) { throw 'La cronologia e cambiata durante la build. Non viene pubblicato un APK con sorgenti diversi.' }
        $current = Read-AppVersion $versionFile
        $pending = [pscustomobject]@{ repository = $config.Repository; versionName = $current.VersionName; versionCode = $current.VersionCode; baseCommit = $head; releaseCommit = '' }
    }
    if ($status -and $Resume) {
        Assert-ResumeChanges $status
    }
    $artifact = 'Mappinando-{0}-debug.apk' -f $current.VersionName
    $apk = Join-Path $project ('releases\' + $artifact)
    $infoPath = $apk + '.build.json'; $hashPath = $apk + '.sha256'
    $info = [IO.File]::ReadAllText($infoPath) | ConvertFrom-Json
    $manifest = New-AppUpdateManifest $info
    if ($manifest.versionCode -ne $current.VersionCode -or $manifest.versionName -ne $current.VersionName) { throw 'Versione della build non corrispondente a version.properties.' }
    if ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sha256) { throw 'L''APK locale non corrisponde al suo hash.' }
    $sdk = $env:ANDROID_HOME
    if (-not $sdk) {
        $localProperties = Join-Path $project 'local.properties'
        if (Test-Path -LiteralPath $localProperties) {
            $sdkLine = [IO.File]::ReadAllLines($localProperties) | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
            if ($sdkLine) { $sdk = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\') }
        }
    }
    if (-not $sdk) { throw 'Imposta ANDROID_HOME o sdk.dir in local.properties per verificare la firma.' }
    $signer = Join-Path $sdk 'build-tools\36.0.0\apksigner.bat'
    $certificate = Invoke-Checked $signer @('verify', '--print-certs', $apk) -Capture
    $match = [regex]::Match($certificate, '(?m)^Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})\r?$')
    if (-not $match.Success -or $match.Groups[1].Value -ne $config.CertificateSha256) { throw 'La firma e diversa da quella delle app gia installate. Usa la stessa chiave; non pubblicare questo APK come aggiornamento.' }
    Write-PublishJson $pendingFile $pending
    $dirty = (Invoke-Checked git @('status', '--porcelain', '--untracked-files=normal') -Capture)
    if ($dirty) {
        if ($Resume) { Assert-ResumeChanges $dirty }
        elseif ($dirty.Trim() -ne 'M version.properties') { throw 'I sorgenti sono cambiati durante la compilazione. Il rilascio non viene pubblicato.' }
        $versionDirty = Invoke-Checked git @('status', '--porcelain', '--', 'version.properties') -Capture
        if ($versionDirty) {
            Invoke-Checked git @('add', '--', 'version.properties')
            Invoke-Checked git @('commit', '-m', ('Release v' + $current.VersionName))
        }
    }
    $releaseCommit = (Invoke-Checked git @('rev-parse', 'HEAD') -Capture).Trim()
    $versionInCommit = Invoke-Checked git @('show', 'HEAD:version.properties') -Capture
    if ($versionInCommit -notmatch ('(?m)^versionCode=' + $current.VersionCode + '\r?$') -or
        $versionInCommit -notmatch ('(?m)^versionName=' + [regex]::Escape($current.VersionName) + '\r?$')) { throw 'Il commit non contiene la versione dell''APK.' }
    $pending.releaseCommit = $releaseCommit; Write-PublishJson $pendingFile $pending
    $tag = 'v' + $current.VersionName
    $existingTag = & git rev-parse --verify --quiet ($tag + '^{commit}')
    if ($LASTEXITCODE -eq 0) {
        if (($existingTag -join '').Trim() -ne $releaseCommit) { throw 'Il tag esiste gia su un altro commit. Non viene spostato.' }
    } else { Invoke-Checked git @('tag', '-a', $tag, '-m', ('Mappinando ' + $current.VersionName)) }
    Invoke-Checked git @('push', '--atomic', 'origin', 'HEAD:refs/heads/main', ('refs/tags/' + $tag))
    $stage = Join-Path $project ('releases\github-' + $tag)
    [void][IO.Directory]::CreateDirectory($stage)
    $updatePath = Join-Path $stage 'Mappinando-update.json'
    Write-PublishJson $updatePath $manifest
    $notesPath = Join-Path $stage 'release-notes.md'
    if ($NotesFile) {
        if (-not (Test-Path -LiteralPath $NotesFile)) { throw 'File delle note di rilascio non trovato. Usa -Resume dopo aver corretto il percorso.' }
        [IO.File]::Copy([IO.Path]::GetFullPath($NotesFile), $notesPath, $true)
    } else {
        $defaultNotes = Join-Path $project ('docs\release-notes\' + $current.VersionName + '.md')
        if (Test-Path -LiteralPath $defaultNotes) { [IO.File]::Copy($defaultNotes, $notesPath, $true) }
        else { [IO.File]::WriteAllText($notesPath, ("Mappinando $($current.VersionName)`n`nUna nuova versione del tuo piccolo atlante personale.`nLe tappe, le note e le attivita restano salvate aggiornando l'app.`n`nScarica l'APK per installare o aggiornare Mappinando su Android 8.0 o successivo."), (New-Object Text.UTF8Encoding($false))) }
    }
    $files = @($apk, $hashPath, $infoPath, $updatePath)
    $previousErrorPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $existingRelease = & gh release view $tag --repo $config.Repository --json isDraft,databaseId 2>$null
        $releaseExists = $LASTEXITCODE -eq 0
    } finally { $ErrorActionPreference = $previousErrorPreference }
    if (-not $releaseExists) {
        Invoke-Checked gh @('release', 'create', $tag, '--repo', $config.Repository, '--draft', '--verify-tag', '--title', ('Mappinando ' + $current.VersionName), '--notes-file', $notesPath)
    } elseif (-not (($existingRelease -join "`n" | ConvertFrom-Json).isDraft)) {
        $releaseId = (($existingRelease -join "`n") | ConvertFrom-Json).databaseId
        $published = Invoke-Checked gh @('api', ('repos/' + $config.Repository + '/releases/' + $releaseId)) -Capture | ConvertFrom-Json
        Assert-ReleaseAssets $published $files $manifest.sha256
        [IO.File]::Delete($pendingFile)
        Write-Host ("Release gia completa: " + $published.html_url) -ForegroundColor Green
        exit 0
    }
    Invoke-Checked gh (@('release', 'upload', $tag, '--repo', $config.Repository, '--clobber') + $files)
    # Drafts may not resolve through /releases/tags/{tag}; use the release's stable database ID.
    $releaseId = (Invoke-Checked gh @('release', 'view', $tag, '--repo', $config.Repository, '--json', 'databaseId', '--jq', '.databaseId') -Capture).Trim()
    $uploaded = Invoke-Checked gh @('api', ('repos/' + $config.Repository + '/releases/' + $releaseId)) -Capture | ConvertFrom-Json
    Assert-ReleaseAssets $uploaded $files $manifest.sha256
    Invoke-Checked gh @('release', 'edit', $tag, '--repo', $config.Repository, '--draft=false', '--latest')
    [IO.File]::Delete($pendingFile)
    Write-Host ("Release pubblicata: https://github.com/" + $config.Repository + '/releases/tag/' + $tag) -ForegroundColor Green
    exit 0
} catch {
    Write-Host ("Pubblicazione interrotta: " + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Se la build e gia pronta, correggi il problema e riprova con -Resume: non incrementa nuovamente la versione.'
    exit 1
} finally {
    if ($pushedLocation) { Pop-Location }
    if ($null -ne $publishLock) { $publishLock.Dispose() }
}
