# 运行前提：必须使用用户明确指定且已校验的备份、校验文件和数据库标识。
# 破坏性边界：只处理确认的密码迁移，不执行 Flyway clean/repair，不删除卷、备份或上传文件。
# 失败恢复：迁移失败时保留日志和备份路径，停止后续步骤，按备份校验结果人工恢复。
# 退出码：成功返回 0，校验、连接或迁移失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,
    [Parameter(Mandatory = $true)]
    [string] $BackupSha256,
    [Parameter(Mandatory = $true)]
    [string] $ConfirmDatabase
)

$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

$requiredVariables = @(
    'CC4C_DB_URL',
    'CC4C_DB_USERNAME',
    'CC4C_DB_PASSWORD'
)
foreach ($name in $requiredVariables) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "Required process environment variable '$name' is missing."
    }
}
if (-not (Test-Path -LiteralPath $BackupPath -PathType Leaf)) {
    throw "The backup file does not exist."
}
if ($BackupSha256 -notmatch '^[a-fA-F0-9]{64}$') {
    throw "BackupSha256 must contain exactly 64 hexadecimal characters."
}

$previous = @{}
$migrationVariables = @{
    'CC4C_PASSWORD_MIGRATION_BACKUP_PATH' = [System.IO.Path]::GetFullPath($BackupPath)
    'CC4C_PASSWORD_MIGRATION_BACKUP_SHA256' = $BackupSha256.ToLowerInvariant()
    'CC4C_PASSWORD_MIGRATION_CONFIRM_DATABASE' = $ConfirmDatabase
    'SPRING_CONFIG_NAME' = 'application-example'
}
foreach ($entry in $migrationVariables.GetEnumerator()) {
    $previous[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
}

Push-Location -LiteralPath (Join-Path $workspaceRoot 'backend')
try {
    & mvn --no-transfer-progress -DskipTests compile exec:java `
        '-Dexec.mainClass=com.cc4ctools.PasswordMigrationApplication' `
        '-Dexec.cleanupDaemonThreads=false'
    $migrationExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
    foreach ($entry in $migrationVariables.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $previous[$entry.Key], 'Process')
    }
}

exit $migrationExitCode
