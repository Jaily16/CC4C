# 运行前提：仅在已批准的维护窗口内运行；先完成写入冻结、备份和消息排空。
# 破坏性边界：只允许处理下方固定映射中的 Docker 卷；不触碰容器、数据库、Redis/RabbitMQ
# 数据内容、上传文件、秘密或任意其他卷。脚本绝不执行 Compose 启停、队列清空或 Flyway 操作。
# 失败恢复：Copy/RestoreLegacy 失败时只回收本次创建的精确目标卷；DeleteSource 遇到首个失败立即停止。
# 退出码：操作成功返回 0；前置条件、摘要、标签、Docker 或回滚检查失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('DryRun', 'Copy', 'Verify', 'DeleteSource', 'RestoreLegacy')]
    [string] $Mode,

    [string] $ManifestPath,

    [string] $ConfirmSourceDeletion
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$expectedHead = '8f2987267a942655c1059243aaa60cf4bd29748b'
$sourceProject = 'cc4c-v3'
$targetProject = 'cc4c'
$sourcePrefix = 'cc4c-v3'
$targetPrefix = 'cc4c'
$sourceDeletionConfirmation = 'DELETE-CC4C-V3-SOURCE-VOLUMES'
$helperImage = 'mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb'

$volumeMappings = @(
    [ordered]@{ LogicalName = 'mysql_data'; Source = 'cc4c-v3_mysql_data'; Target = 'cc4c_mysql_data' }
    [ordered]@{ LogicalName = 'redis_security_data'; Source = 'cc4c-v3_redis_security_data'; Target = 'cc4c_redis_security_data' }
    [ordered]@{ LogicalName = 'redis_cache_data'; Source = 'cc4c-v3_redis_cache_data'; Target = 'cc4c_redis_cache_data' }
    [ordered]@{ LogicalName = 'rabbitmq_data'; Source = 'cc4c-v3_rabbitmq_data'; Target = 'cc4c_rabbitmq_data' }
    [ordered]@{ LogicalName = 'prometheus_data'; Source = 'cc4c-v3_prometheus_data'; Target = 'cc4c_prometheus_data' }
    [ordered]@{ LogicalName = 'grafana_data'; Source = 'cc4c-v3_grafana_data'; Target = 'cc4c_grafana_data' }
    [ordered]@{ LogicalName = 'blog_uploads'; Source = 'cc4c-v3_blog_uploads'; Target = 'cc4c_blog_uploads' }
    [ordered]@{ LogicalName = 'avatar_uploads'; Source = 'cc4c-v3_avatar_uploads'; Target = 'cc4c_avatar_uploads' }
)

function Get-FullPath {
    param([Parameter(Mandatory = $true)][string] $Path)
    return [System.IO.Path]::GetFullPath($Path)
}

function Assert-ExternalManifestPath {
    param([Parameter(Mandatory = $true)][string] $Path)
    $fullPath = Get-FullPath $Path
    $root = (Get-FullPath $workspaceRoot).TrimEnd('\') + '\'
    if ($fullPath.TrimEnd('\') -ieq $workspaceRoot.TrimEnd('\') -or
        $fullPath.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'The migration manifest must be outside the repository workspace.'
    }
    return $fullPath
}

function Get-EffectiveManifestPath {
    if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        $nonce = [Guid]::NewGuid().ToString('N')
        $parent = Join-Path ([System.IO.Path]::GetTempPath()) "cc4c-v4-aspect5-migration-$stamp-$nonce"
        New-Item -ItemType Directory -Path $parent | Out-Null
        return (Join-Path $parent 'volume-migration.json')
    }
    $fullPath = Assert-ExternalManifestPath $ManifestPath
    $parentPath = Split-Path -Parent $fullPath
    if (-not (Test-Path -LiteralPath $parentPath -PathType Container)) {
        New-Item -ItemType Directory -Path $parentPath | Out-Null
    }
    if ($Mode -eq 'DryRun' -and (Test-Path -LiteralPath $fullPath)) {
        throw 'Refusing to overwrite an existing migration manifest during DryRun.'
    }
    if ($Mode -ne 'DryRun' -and -not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw 'Copy, Verify, DeleteSource and RestoreLegacy require an existing DryRun manifest.'
    }
    return $fullPath
}

function Invoke-DockerQuiet {
    param([Parameter(Mandatory = $true)][string[]] $Arguments)
    $output = & docker @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker metadata or volume operation failed.'
    }
    return $output
}

function Get-VolumeMetadata {
    param([Parameter(Mandatory = $true)][string] $Name)
    try {
        $raw = & docker volume inspect --format '{{json .}}' $Name 2>$null
    } catch {
        return $null
    }
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($raw -join [Environment]::NewLine))) {
        return $null
    }
    try {
        return (($raw -join [Environment]::NewLine) | ConvertFrom-Json)
    }
    catch {
        throw 'Docker returned invalid volume metadata.'
    }
}

function Get-ProjectContainerIds {
    param([Parameter(Mandatory = $true)][string] $Project)
    $ids = @(& docker ps -aq --filter "label=com.docker.compose.project=$Project" 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to inspect Compose project containers.'
    }
    return @($ids | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-VolumeConsumerIds {
    param([Parameter(Mandatory = $true)][string] $Volume)
    $ids = @(& docker ps -aq --filter "volume=$Volume" 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to inspect Docker volume consumers.'
    }
    return @($ids | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-LabelValue {
    param(
        [Parameter(Mandatory = $true)] $Metadata,
        [Parameter(Mandatory = $true)][string] $Name
    )
    if ($null -eq $Metadata.Labels) {
        return $null
    }
    $property = $Metadata.Labels.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return [string] $property.Value
}

function Assert-VolumeLabels {
    param(
        [Parameter(Mandatory = $true)] $Metadata,
        [Parameter(Mandatory = $true)][string] $ExpectedProject,
        [Parameter(Mandatory = $true)][string] $ExpectedLogicalName
    )
    $projectLabel = Get-LabelValue $Metadata 'com.docker.compose.project'
    $volumeLabel = Get-LabelValue $Metadata 'com.docker.compose.volume'
    if ($projectLabel -cne $ExpectedProject -or $volumeLabel -cne $ExpectedLogicalName) {
        throw "Volume labels do not match the approved Compose identity for '$ExpectedLogicalName'."
    }
}

function Get-TextDigest {
    param([Parameter(Mandatory = $true)][string] $Text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        return ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Get-GitBaseline {
    $head = ((& git rev-parse HEAD 2>$null) -join '').Trim()
    $origin = ((& git rev-parse origin/main 2>$null) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($head) -or [string]::IsNullOrWhiteSpace($origin)) {
        throw 'Unable to record the Git baseline.'
    }
    $status = ((& git status --short --untracked-files=all 2>$null) -join [Environment]::NewLine)
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to record the Git status baseline.'
    }
    return [ordered]@{
        head = $head
        originMain = $origin
        statusDigest = Get-TextDigest $status
        statusCount = if ([string]::IsNullOrWhiteSpace($status)) { 0 } else { @($status -split [Environment]::NewLine).Count }
    }
}

function Assert-GitBaseline {
    param([Parameter(Mandatory = $true)] $Expected)
    $current = Get-GitBaseline
    if ($current.head -cne $expectedHead -or $current.originMain -cne $expectedHead) {
        throw 'Git HEAD or origin/main is not the approved V4 baseline.'
    }
    if ($current.head -cne $Expected.head -or
        $current.originMain -cne $Expected.originMain -or
        $current.statusDigest -cne $Expected.statusDigest) {
        throw 'The Git baseline changed after the migration manifest was created.'
    }
}

function Get-VolumeSummary {
    param([Parameter(Mandatory = $true)][string] $Volume)
    $fingerprintCommand = 'set -eu; cd /data; count=$(find . -type f | wc -l); bytes=$(find . -type f -printf "%s\n" | awk "{s+=\$1} END {print s+0}"); summary=$(find . -type f -printf "%P\t%s\n" | LC_ALL=C sort | sha256sum | awk "{print \$1}"); printf "{\"fileCount\":%s,\"bytes\":%s,\"summarySha256\":\"%s\"}\n" "$count" "$bytes" "$summary"'
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($fingerprintCommand))
    $bootstrapCommand = "printf '%s' '$encodedCommand' | base64 -d | /bin/sh"
    $output = & docker run --rm --network none --read-only --entrypoint /bin/sh -v "$($Volume):/data:ro" $helperImage -c $bootstrapCommand 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($output -join [Environment]::NewLine))) {
        throw 'Unable to calculate an opaque volume summary.'
    }
    try {
        return (($output -join [Environment]::NewLine) | ConvertFrom-Json)
    }
    catch {
        throw 'The opaque volume summary was invalid.'
    }
}

function Copy-VolumeOpaque {
    param(
        [Parameter(Mandatory = $true)][string] $Source,
        [Parameter(Mandatory = $true)][string] $Target
    )
    $copyCommand = 'set -eu; cd /from; tar -cf - . | tar -xf - -C /to'
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($copyCommand))
    $bootstrapCommand = "printf '%s' '$encodedCommand' | base64 -d | /bin/sh"
    & docker run --rm --network none --read-only --entrypoint /bin/sh -v "$($Source):/from:ro" -v "$($Target):/to" $helperImage -c $bootstrapCommand 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Opaque Docker volume copy failed.'
    }
}

function Assert-ProjectStopped {
    param([Parameter(Mandatory = $true)][string] $Project)
    if (@(Get-ProjectContainerIds $Project).Count -ne 0) {
        throw "Compose project '$Project' must have no containers for this operation."
    }
}

function Assert-VolumeUnattached {
    param([Parameter(Mandatory = $true)][string] $Volume)
    if (@(Get-VolumeConsumerIds $Volume).Count -ne 0) {
        throw "Volume '$Volume' is still attached to a container."
    }
}

function Assert-ConsumersBelongToProject {
    param(
        [Parameter(Mandatory = $true)][string] $Volume,
        [Parameter(Mandatory = $true)][string] $Project
    )
    foreach ($id in @(Get-VolumeConsumerIds $Volume)) {
        $label = ((& docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' $id 2>$null) -join '').Trim()
        if ($LASTEXITCODE -ne 0 -or $label -cne $Project) {
            throw "Volume '$Volume' is attached outside the approved Compose project."
        }
    }
}

function New-ComposeVolume {
    param(
        [Parameter(Mandatory = $true)][string] $Name,
        [Parameter(Mandatory = $true)][string] $Project,
        [Parameter(Mandatory = $true)][string] $LogicalName
    )
    if ($null -ne (Get-VolumeMetadata $Name)) {
        throw "Refusing to overwrite existing volume '$Name'."
    }
    Invoke-DockerQuiet @(
        'volume', 'create',
        '--label', "com.docker.compose.project=$Project",
        '--label', "com.docker.compose.volume=$LogicalName",
        $Name
    ) | Out-Null
}

function Remove-ExactVolume {
    param([Parameter(Mandatory = $true)][string] $Name)
    Assert-VolumeUnattached $Name
    if ($null -eq (Get-VolumeMetadata $Name)) {
        return
    }
    Invoke-DockerQuiet @('volume', 'rm', $Name) | Out-Null
}

function Get-VolumeMetadataSummary {
    param([Parameter(Mandatory = $true)] $Metadata)
    $labels = [ordered]@{}
    foreach ($labelName in @(
            'com.docker.compose.project',
            'com.docker.compose.volume',
            'com.docker.compose.version'
        )) {
        $value = Get-LabelValue $Metadata $labelName
        if ($null -ne $value) {
            $labels[$labelName] = $value
        }
    }
    return [ordered]@{
        name = [string] $Metadata.Name
        type = 'docker-volume'
        driver = [string] $Metadata.Driver
        scope = [string] $Metadata.Scope
        labels = $labels
    }
}

function New-Manifest {
    $volumes = @()
    foreach ($mapping in $volumeMappings) {
        $sourceMetadata = Get-VolumeMetadata $mapping.Source
        $targetMetadata = Get-VolumeMetadata $mapping.Target
        $volumes += [ordered]@{
            logicalName = $mapping.LogicalName
            source = $mapping.Source
            target = $mapping.Target
            sourcePresent = ($null -ne $sourceMetadata)
            targetPresent = ($null -ne $targetMetadata)
            sourceMetadata = if ($null -eq $sourceMetadata) { $null } else { Get-VolumeMetadataSummary $sourceMetadata }
            targetMetadata = if ($null -eq $targetMetadata) { $null } else { Get-VolumeMetadataSummary $targetMetadata }
            sourceFingerprint = $null
            targetFingerprint = $null
            sourceDeleted = $false
        }
    }
    return [ordered]@{
        schemaVersion = 1
        workspaceRoot = $workspaceRoot
        sourceProject = $sourceProject
        targetProject = $targetProject
        sourcePrefix = $sourcePrefix
        targetPrefix = $targetPrefix
        helperImage = $helperImage
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        git = Get-GitBaseline
        preconditions = [ordered]@{
            maintenanceWindowConfirmed = $false
            writeFreezeConfirmed = $false
            backupSha256Verified = $false
            outboxDrained = $false
            rabbitDrained = $false
            externalWritersStopped = $false
            targetAcceptanceConfirmed = $false
        }
        volumes = $volumes
        operation = 'DryRun'
    }
}

function Save-Manifest {
    param([Parameter(Mandatory = $true)] $Manifest, [Parameter(Mandatory = $true)][string] $Path)
    $Manifest.generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    $json = $Manifest | ConvertTo-Json -Depth 12
    [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Read-Manifest {
    param([Parameter(Mandatory = $true)][string] $Path)
    $fullPath = Assert-ExternalManifestPath $Path
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "Migration manifest does not exist: $fullPath"
    }
    try {
        return (Get-Content -Raw -LiteralPath $fullPath | ConvertFrom-Json)
    }
    catch {
        throw 'Migration manifest is not valid JSON.'
    }
}

function Assert-ManifestIdentity {
    param([Parameter(Mandatory = $true)] $Manifest)
    if ($Manifest.schemaVersion -ne 1 -or
        [System.IO.Path]::GetFullPath([string] $Manifest.workspaceRoot) -cne [System.IO.Path]::GetFullPath($workspaceRoot) -or
        $Manifest.sourceProject -cne $sourceProject -or
        $Manifest.targetProject -cne $targetProject -or
        $Manifest.sourcePrefix -cne $sourcePrefix -or
        $Manifest.targetPrefix -cne $targetPrefix -or
        $Manifest.helperImage -cne $helperImage) {
        throw 'Migration manifest identity does not match the approved mapping.'
    }
    $actual = @($Manifest.volumes | ForEach-Object { "$($_.source)|$($_.target)|$($_.logicalName)" } | Sort-Object)
    $expected = @($volumeMappings | ForEach-Object { "$($_.Source)|$($_.Target)|$($_.LogicalName)" } | Sort-Object)
    if (($actual -join [Environment]::NewLine) -cne ($expected -join [Environment]::NewLine)) {
        throw 'Migration manifest contains an unapproved volume mapping.'
    }
}

function Assert-MigrationPreconditions {
    param(
        [Parameter(Mandatory = $true)] $Manifest,
        [switch] $RequireAcceptance
    )
    $required = @(
        'maintenanceWindowConfirmed',
        'writeFreezeConfirmed',
        'backupSha256Verified',
        'outboxDrained',
        'rabbitDrained',
        'externalWritersStopped'
    )
    foreach ($name in $required) {
        if ($null -eq $Manifest.preconditions -or $Manifest.preconditions.$name -ne $true) {
            throw "Migration precondition '$name' is not confirmed in the external manifest."
        }
    }
    if ($RequireAcceptance -and $Manifest.preconditions.targetAcceptanceConfirmed -ne $true) {
        throw 'Target health, contract, smoke, persistence and user acceptance are not confirmed in the external manifest.'
    }
}

function Set-ManifestFingerprint {
    param(
        [Parameter(Mandatory = $true)] $Entry,
        [Parameter(Mandatory = $true)][string] $Property,
        [Parameter(Mandatory = $true)] $Summary
    )
    $Entry.$Property = [ordered]@{
        fileCount = [int64] $Summary.fileCount
        bytes = [int64] $Summary.bytes
        summarySha256 = [string] $Summary.summarySha256
    }
}

function Assert-SummaryEqual {
    param([Parameter(Mandatory = $true)] $Expected, [Parameter(Mandatory = $true)] $Actual)
    if ($null -eq $Expected -or
        [int64] $Expected.fileCount -ne [int64] $Actual.fileCount -or
        [int64] $Expected.bytes -ne [int64] $Actual.bytes -or
        [string] $Expected.summarySha256 -cne [string] $Actual.summarySha256) {
        throw 'Volume summary verification failed.'
    }
}

function Assert-TargetVolumes {
    param(
        [Parameter(Mandatory = $true)] $Manifest,
        [switch] $RequireSource,
        [switch] $AllowDeletedSource
    )
    foreach ($entry in @($Manifest.volumes)) {
        $sourceMetadata = Get-VolumeMetadata $entry.source
        $targetMetadata = Get-VolumeMetadata $entry.target
        if (-not $entry.sourcePresent) {
            if ($null -ne $targetMetadata) {
                throw 'A target volume exists for a source volume that was not present in the dry-run.'
            }
            continue
        }
        if ($AllowDeletedSource -and [bool] $entry.sourceDeleted) {
            if ($null -ne $sourceMetadata) {
                throw "Manifest marks source volume '$($entry.source)' as deleted, but it still exists."
            }
            $sourceMetadata = $null
        }
        if ($RequireSource -and -not ($AllowDeletedSource -and [bool] $entry.sourceDeleted) -and $null -eq $sourceMetadata) {
            throw "Source volume '$($entry.source)' is missing before verification."
        }
        if ($null -eq $targetMetadata) {
            throw "Target volume '$($entry.target)' is missing."
        }
        Assert-VolumeLabels $targetMetadata $targetProject $entry.logicalName
        Assert-ConsumersBelongToProject $entry.target $targetProject
        Assert-SummaryEqual $entry.targetFingerprint (Get-VolumeSummary $entry.target)
        if ($null -ne $sourceMetadata) {
            Assert-VolumeLabels $sourceMetadata $sourceProject $entry.logicalName
            Assert-SummaryEqual $entry.sourceFingerprint (Get-VolumeSummary $entry.source)
        }
    }
}

function Invoke-DryRun {
    param([Parameter(Mandatory = $true)][string] $Path)
    $manifest = New-Manifest
    Save-Manifest $manifest $Path
    Write-Output 'DryRun: no Docker volume was created, copied, attached, or removed.'
    foreach ($entry in @($manifest.volumes)) {
        [pscustomobject]@{
            SourceVolume = $entry.source
            TargetVolume = $entry.target
            SourceExists = $entry.sourcePresent
            TargetExists = $entry.targetPresent
            Action = if (-not $entry.sourcePresent) { 'NotCreated' } elseif ($entry.targetPresent) { 'Conflict' } else { 'CopyCandidate' }
        }
    }
    Write-Output "Manifest: $Path"
}

function Invoke-Copy {
    param([Parameter(Mandatory = $true)][string] $Path)
    $manifest = Read-Manifest $Path
    Assert-ManifestIdentity $manifest
    Assert-GitBaseline $manifest.git
    Assert-MigrationPreconditions $manifest
    Assert-ProjectStopped $sourceProject
    Assert-ProjectStopped $targetProject
    $createdTargets = @()
    try {
        foreach ($entry in @($manifest.volumes)) {
            if (-not $entry.sourcePresent) {
                continue
            }
            $sourceMetadata = Get-VolumeMetadata $entry.source
            if ($null -eq $sourceMetadata) {
                throw "Source volume '$($entry.source)' disappeared after DryRun."
            }
            Assert-VolumeLabels $sourceMetadata $sourceProject $entry.logicalName
            Assert-VolumeUnattached $entry.source
            if ($null -ne (Get-VolumeMetadata $entry.target)) {
                throw "Refusing to overwrite existing target volume '$($entry.target)'."
            }
            $sourceSummary = Get-VolumeSummary $entry.source
            New-ComposeVolume $entry.target $targetProject $entry.logicalName
            $createdTargets += $entry.target
            Copy-VolumeOpaque $entry.source $entry.target
            $targetSummary = Get-VolumeSummary $entry.target
            Assert-SummaryEqual $sourceSummary $targetSummary
            $entry.sourceMetadata = Get-VolumeMetadataSummary (Get-VolumeMetadata $entry.source)
            $entry.targetMetadata = Get-VolumeMetadataSummary (Get-VolumeMetadata $entry.target)
            Set-ManifestFingerprint $entry 'sourceFingerprint' $sourceSummary
            Set-ManifestFingerprint $entry 'targetFingerprint' $targetSummary
            $entry.targetPresent = $true
        }
        $manifest.operation = 'Copied'
        Save-Manifest $manifest $Path
        Write-Output 'Copy completed for every source volume that existed in the DryRun manifest.'
    }
    catch {
        for ($index = $createdTargets.Count - 1; $index -ge 0; $index--) {
            try { Remove-ExactVolume $createdTargets[$index] } catch { }
        }
        throw
    }
}

function Invoke-Verify {
    param([Parameter(Mandatory = $true)][string] $Path)
    $manifest = Read-Manifest $Path
    Assert-ManifestIdentity $manifest
    Assert-GitBaseline $manifest.git
    Assert-TargetVolumes $manifest
    $manifest.operation = 'Verified'
    Save-Manifest $manifest $Path
    Write-Output 'Verify completed. Target labels, consumers and opaque summaries match the manifest.'
}

function Invoke-DeleteSource {
    param([Parameter(Mandatory = $true)][string] $Path)
    if ($ConfirmSourceDeletion -cne $sourceDeletionConfirmation) {
        throw "Refusing source deletion. ConfirmSourceDeletion must exactly equal '$sourceDeletionConfirmation'."
    }
    $manifest = Read-Manifest $Path
    Assert-ManifestIdentity $manifest
    Assert-GitBaseline $manifest.git
    Assert-MigrationPreconditions $manifest -RequireAcceptance
    Assert-ProjectStopped $sourceProject
    Assert-TargetVolumes $manifest -RequireSource -AllowDeletedSource
    foreach ($entry in @($manifest.volumes)) {
        if (-not $entry.sourcePresent -or [bool] $entry.sourceDeleted) {
            continue
        }
        Assert-VolumeUnattached $entry.source
        Remove-ExactVolume $entry.source
        $entry.sourceDeleted = $true
        $entry.sourceMetadata = $null
        Save-Manifest $manifest $Path
    }
    $manifest.operation = 'SourceDeleted'
    Save-Manifest $manifest $Path
    Write-Output 'DeleteSource completed only for the exact approved source volumes.'
}

function Invoke-RestoreLegacy {
    param([Parameter(Mandatory = $true)][string] $Path)
    $manifest = Read-Manifest $Path
    Assert-ManifestIdentity $manifest
    Assert-GitBaseline $manifest.git
    Assert-ProjectStopped $targetProject
    $createdSources = @()
    try {
        foreach ($entry in @($manifest.volumes)) {
            if (-not $entry.sourcePresent -or [bool] $entry.sourceDeleted) {
                $targetMetadata = Get-VolumeMetadata $entry.target
                if ($null -eq $targetMetadata) {
                    throw "Cannot restore '$($entry.source)' because its target volume is missing."
                }
                Assert-VolumeLabels $targetMetadata $targetProject $entry.logicalName
                Assert-ConsumersBelongToProject $entry.target $targetProject
                Assert-SummaryEqual $entry.targetFingerprint (Get-VolumeSummary $entry.target)
                if ($null -ne (Get-VolumeMetadata $entry.source)) {
                    throw "Refusing to overwrite existing legacy volume '$($entry.source)'."
                }
                New-ComposeVolume $entry.source $sourceProject $entry.logicalName
                $createdSources += $entry.source
                Copy-VolumeOpaque $entry.target $entry.source
                $sourceSummary = Get-VolumeSummary $entry.source
                Assert-SummaryEqual $entry.targetFingerprint $sourceSummary
                $entry.sourcePresent = $true
                $entry.sourceDeleted = $false
                $entry.sourceMetadata = Get-VolumeMetadataSummary (Get-VolumeMetadata $entry.source)
                Set-ManifestFingerprint $entry 'sourceFingerprint' $sourceSummary
                Save-Manifest $manifest $Path
            }
        }
        $manifest.operation = 'LegacyRestored'
        Save-Manifest $manifest $Path
        Write-Output 'RestoreLegacy completed for missing legacy volumes without overwriting existing data.'
    }
    catch {
        for ($index = $createdSources.Count - 1; $index -ge 0; $index--) {
            try { Remove-ExactVolume $createdSources[$index] } catch { }
        }
        throw
    }
}

$effectiveManifestPath = Get-EffectiveManifestPath
try {
    switch ($Mode) {
        'DryRun' { Invoke-DryRun $effectiveManifestPath }
        'Copy' { Invoke-Copy $effectiveManifestPath }
        'Verify' { Invoke-Verify $effectiveManifestPath }
        'DeleteSource' { Invoke-DeleteSource $effectiveManifestPath }
        'RestoreLegacy' { Invoke-RestoreLegacy $effectiveManifestPath }
    }
    exit 0
}
catch {
    Write-Error $_.Exception.Message
    exit 1
}
