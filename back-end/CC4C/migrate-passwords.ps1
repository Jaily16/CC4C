[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,
    [Parameter(Mandatory = $true)]
    [string] $BackupSha256,
    [Parameter(Mandatory = $true)]
    [string] $ConfirmDatabase
)

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

Push-Location -LiteralPath $PSScriptRoot
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
