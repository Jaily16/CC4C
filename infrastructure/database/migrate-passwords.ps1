#requires -Version 7.0
# 运行前提：用户明确确认离线密码迁移，backend/.env.local、备份路径、SHA-256 和精确数据库名已准备。
# 外部依赖：依赖共享环境加载器、Java 21、Maven 及既有依赖、用户提供的备份和已就绪的专用 MySQL。
# 破坏性边界：通过 Maven 编译并调用既有密码迁移工具，可能更新用户密码；不创建数据库、不改写 Flyway 历史或备份。
# 失败恢复：保留现场和备份，恢复调用进程的全部临时变量；不自动重复迁移或回滚数据。
# 退出码：工具成功为 0，配置、备份前置检查或迁移失败为 1。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string] $BackupPath,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-fA-F0-9]{64}$')][string] $BackupSha256,
    [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9_]+$')][string] $ConfirmDatabase
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '..\host\host-environment.ps1')
$snapshot = $null

try {
    $workspaceRoot = Get-Cc4cHostWorkspaceRoot
    # 本脚本只检查备份路径元数据；实际校验与迁移由既有 Java 工具在用户调用时执行。
    $backup = Assert-Cc4cOrdinaryPath ([IO.Path]::GetFullPath($BackupPath))
    $values = Assert-Cc4cRuntimeEnvironment (Read-Cc4cEnvironmentFile -Kind Runtime)
    if ((Get-Cc4cDatabaseName $values) -cne $ConfirmDatabase) { throw 'ConfirmDatabase must exactly match the configured database.' }
    $values.CC4C_PASSWORD_MIGRATION_BACKUP_PATH = $backup
    $values.CC4C_PASSWORD_MIGRATION_BACKUP_SHA256 = $BackupSha256.ToLowerInvariant()
    $values.CC4C_PASSWORD_MIGRATION_CONFIRM_DATABASE = $ConfirmDatabase
    $snapshot = Set-Cc4cProcessEnvironment $values -Backend
    Push-Location -LiteralPath (Join-Path $workspaceRoot 'backend')
    try {
        $arguments = @(
            '--no-transfer-progress', '-DskipTests', 'compile', 'exec:java',
            '-Dexec.mainClass=com.cc4ctools.PasswordMigrationApplication',
            '-Dexec.cleanupDaemonThreads=false'
        )
        & mvn @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Password migration failed; preserve the backup and database for manual inspection.' }
    }
    finally { Pop-Location }
    exit 0
}
catch {
    Write-Error $_.Exception.Message -ErrorAction Continue
    exit 1
}
finally { if ($null -ne $snapshot) { Restore-Cc4cProcessEnvironment $snapshot } }
