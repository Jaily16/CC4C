# 运行前提：Java 21、管理员引导 JAR、仓库外运行环境文件和仓库外密码文件均已由用户准备。
# 破坏性边界：只在精确确认的数据库中创建首个管理员；不创建数据库、不管理外部服务、不打印秘密。
# 失败恢复：任何检查或引导失败立即停止，并在 finally 中恢复调用进程的全部临时环境变量。
# 退出码：引导成功返回 0，路径、配置、数据库确认、连接或业务失败返回非零码。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d{7}$')]
    [string] $AdminId,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string] $ConfirmDatabase,

    [Parameter(Mandatory = $true)]
    [string] $PasswordFile,

    [Parameter(Mandatory = $true)]
    [string] $RuntimeEnvironmentPath
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $workspaceRoot 'scripts\development\host-environment.ps1')

if (-not [System.IO.Path]::IsPathRooted($PasswordFile)) {
    throw 'PasswordFile must be an absolute path outside the repository.'
}
$resolvedPasswordFile = [System.IO.Path]::GetFullPath($PasswordFile)
$workspacePrefix = [System.IO.Path]::GetFullPath($workspaceRoot).TrimEnd('\') + '\'
if ($resolvedPasswordFile.StartsWith($workspacePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'PasswordFile must remain outside the repository.'
}
if (-not (Test-Path -LiteralPath $resolvedPasswordFile -PathType Leaf)) {
    throw 'The administrator password file does not exist.'
}
$passwordItem = Get-Item -LiteralPath $resolvedPasswordFile -Force
if ($passwordItem.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
    throw 'PasswordFile must not be a reparse point.'
}

$environmentPathSnapshot = $null
$processEnvironmentSnapshot = $null
$bootstrapEnvironmentNames = @(
    'CC4C_ADMIN_BOOTSTRAP_ID',
    'CC4C_ADMIN_BOOTSTRAP_CONFIRM_DATABASE',
    'CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE'
)
$bootstrapEnvironmentSnapshot = [ordered]@{}
foreach ($name in $bootstrapEnvironmentNames) {
    $bootstrapEnvironmentSnapshot[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $environmentPathSnapshot = Set-Cc4cHostEnvironmentPathOverrides -RuntimePath $RuntimeEnvironmentPath
    $runtime = Read-Cc4cEnvironmentFile -Kind Runtime
    $values = Assert-Cc4cRuntimeEnvironment $runtime
    if ((Get-Cc4cDatabaseName $values) -cne $ConfirmDatabase) {
        throw 'ConfirmDatabase does not exactly match the configured database.'
    }

    & (Join-Path $workspaceRoot 'scripts\development\host-preflight.ps1') `
        -Component MySQL `
        -ConfirmDatabase $ConfirmDatabase `
        -RuntimeEnvironmentPath $RuntimeEnvironmentPath
    if ($LASTEXITCODE -ne 0) {
        throw 'MySQL host preflight failed.'
    }

    $jarPath = Join-Path $workspaceRoot 'backend\target\cc4c-5.0.0-SNAPSHOT-admin-bootstrap.jar'
    if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
        throw 'The CC4C administrator bootstrap JAR has not been built.'
    }
    $javaCommand = Get-Command java.exe -ErrorAction Stop
    $javaVersion = @(& $javaCommand.Source -version 2>&1)
    if ($LASTEXITCODE -ne 0 -or ($javaVersion -join "`n") -notmatch 'version "21(?:\.|"|-)') {
        throw 'Java 21 is required for administrator bootstrap.'
    }

    $processEnvironmentSnapshot = Set-Cc4cProcessEnvironment $values
    [Environment]::SetEnvironmentVariable('CC4C_ADMIN_BOOTSTRAP_ID', $AdminId, 'Process')
    [Environment]::SetEnvironmentVariable('CC4C_ADMIN_BOOTSTRAP_CONFIRM_DATABASE', $ConfirmDatabase, 'Process')
    [Environment]::SetEnvironmentVariable('CC4C_ADMIN_BOOTSTRAP_PASSWORD_FILE', $resolvedPasswordFile, 'Process')

    & $javaCommand.Source -jar $jarPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Administrator bootstrap failed.'
    }
}
finally {
    foreach ($name in $bootstrapEnvironmentNames) {
        [Environment]::SetEnvironmentVariable($name, $bootstrapEnvironmentSnapshot[$name], 'Process')
    }
    if ($null -ne $processEnvironmentSnapshot) {
        Restore-Cc4cProcessEnvironment $processEnvironmentSnapshot
    }
    if ($null -ne $environmentPathSnapshot) {
        Restore-Cc4cHostEnvironmentPathOverrides $environmentPathSnapshot
    }
}
